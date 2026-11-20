package com.matrix.search.crawler.service;

import com.matrix.search.config.CrawlerConfig;
import com.matrix.search.crawler.dto.CrawlStartRequest;
import com.matrix.search.crawler.dto.CrawlStatusResponse;
import com.matrix.search.crawler.dto.RawCrawledPage;
import com.matrix.search.crawler.filter.UrlFilter;
import com.matrix.search.crawler.model.CrawlState;
import com.matrix.search.crawler.robots.RobotsTxtService;
import com.matrix.search.document.service.DocumentService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-threaded crawler engine for hust.edu.vn and its subdomains.
 *
 * <p>Each crawl run owns a fixed worker pool that consumes a shared URL frontier.
 * Workers claim URLs atomically from a visited set, verify robots.txt permission,
 * throttle per-host fetches, fetch the page with Jsoup, hand the raw page to the
 * document module (when an implementation is registered) and enqueue newly
 * discovered in-domain links up to the configured depth. A supervisor thread
 * finalizes the run state once every worker has terminated.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlerServiceImpl implements CrawlerService {

    /** How long a worker waits for a new task before re-checking termination conditions. */
    private static final long WORKER_POLL_TIMEOUT_MS = 250;

    /** Hard upper bound for a single crawl run before workers are forcibly stopped. */
    private static final long MAX_CRAWL_DURATION_MINUTES = 120;

    /** Grace period after a forced pool shutdown. */
    private static final long FORCE_STOP_GRACE_SECONDS = 30;

    /** Maximum size (bytes) of a fetched page body. */
    private static final int MAX_BODY_SIZE_BYTES = 5 * 1024 * 1024;

    /** Upper bound for worker threads accepted from a start request. */
    private static final int MAX_THREADS = 64;

    private static final AtomicInteger RUN_COUNTER = new AtomicInteger();

    private final CrawlerConfig config;
    private final UrlFilter urlFilter;
    private final RobotsTxtService robotsTxtService;
    private final ObjectProvider<DocumentService> documentService;

    /** Guards crawl lifecycle transitions (start/stop). */
    private final Object lifecycleLock = new Object();
    private volatile CrawlRun activeRun;
    private volatile CrawlRun lastFinishedRun;

    @Override
    public CrawlStatusResponse start(CrawlStartRequest request) {
        synchronized (lifecycleLock) {
            CrawlRun current = activeRun;
            if (current != null && !current.isFinished()) {
                throw new IllegalStateException("A crawl is already running; stop it before starting a new one.");
            }
            CrawlRun run = createRun(request == null ? CrawlStartRequest.builder().build() : request);
            activeRun = run;
            run.begin();
            log.info("Crawl started: seeds={}, maxPages={}, maxDepth={}, threads={}",
                    run.seeds, run.maxPages, run.maxDepth, run.threads);
            return snapshot(run);
        }
    }

    @Override
    public CrawlStatusResponse stop() {
        CrawlRun run = activeRun;
        if (run == null || run.isFinished()) {
            throw new IllegalStateException("No crawl is currently running.");
        }
        run.stop();
        return snapshot(run);
    }

    @Override
    public CrawlStatusResponse status() {
        CrawlRun run = activeRun;
        if (run != null) {
            return snapshot(run);
        }
        CrawlRun last = lastFinishedRun;
        return last != null ? snapshot(last) : idleStatus();
    }

    /**
     * Stops any still-running crawl when the application shuts down.
     */
    @PreDestroy
    void shutdown() {
        CrawlRun run = activeRun;
        if (run != null && !run.isFinished()) {
            run.stopRequested.set(true);
            run.workers.shutdownNow();
        }
    }

    private CrawlRun createRun(CrawlStartRequest request) {
        List<String> seeds = resolveSeeds(request);
        if (seeds.isEmpty()) {
            throw new IllegalArgumentException(
                    "No valid seed URL belongs to *." + config.getAllowedDomainSuffix() + "; nothing to crawl.");
        }
        return new CrawlRun(seeds, resolveMaxPages(request), resolveMaxDepth(request), resolveThreads(request));
    }

    private List<String> resolveSeeds(CrawlStartRequest request) {
        List<String> requested = request.getSeedUrls() != null && !request.getSeedUrls().isEmpty()
                ? request.getSeedUrls()
                : config.getSeedUrls();
        LinkedHashSet<String> seeds = new LinkedHashSet<>();
        for (String seed : requested) {
            urlFilter.normalizeAndFilter(seed, null).ifPresentOrElse(
                    seeds::add,
                    () -> log.warn("Ignoring invalid or off-domain seed URL: {}", seed));
        }
        return List.copyOf(seeds);
    }

    private int resolveMaxPages(CrawlStartRequest request) {
        Integer requested = request.getMaxPages();
        if (requested == null) {
            return config.getMaxPages();
        }
        if (requested <= 0) {
            throw new IllegalArgumentException("maxPages must be a positive number.");
        }
        return requested;
    }

    private int resolveMaxDepth(CrawlStartRequest request) {
        Integer requested = request.getMaxDepth();
        if (requested == null) {
            return config.getMaxDepth();
        }
        if (requested < 0) {
            throw new IllegalArgumentException("maxDepth must be zero or a positive number.");
        }
        return requested;
    }

    private int resolveThreads(CrawlStartRequest request) {
        Integer requested = request.getThreads();
        if (requested == null) {
            return config.getThreads();
        }
        if (requested <= 0 || requested > MAX_THREADS) {
            throw new IllegalArgumentException("threads must be between 1 and " + MAX_THREADS + ".");
        }
        return requested;
    }

    private CrawlStatusResponse snapshot(CrawlRun run) {
        return CrawlStatusResponse.builder()
                .state(run.state)
                .startedAt(run.startedAt)
                .finishedAt(run.finishedAt)
                .seedUrls(run.seeds)
                .maxPages(run.maxPages)
                .maxDepth(run.maxDepth)
                .threads(run.threads)
                .pagesCrawled(run.crawled.get())
                .pagesFailed(run.failed.get())
                .urlsDiscovered(run.discovered.get())
                .urlsQueued(run.frontier.size())
                .urlsSkippedRobots(run.robotsSkipped.get())
                .message(describe(run))
                .build();
    }

    private CrawlStatusResponse idleStatus() {
        return CrawlStatusResponse.builder()
                .state(CrawlState.IDLE)
                .seedUrls(config.getSeedUrls())
                .maxPages(config.getMaxPages())
                .maxDepth(config.getMaxDepth())
                .threads(config.getThreads())
                .message("No crawl has been started yet.")
                .build();
    }

    private String describe(CrawlRun run) {
        return switch (run.state) {
            case RUNNING -> "Crawl in progress.";
            case STOPPING -> "Stop requested; workers are finishing their current fetches.";
            case COMPLETED -> "Crawl completed: %d pages fetched, %d failed."
                    .formatted(run.crawled.get(), run.failed.get());
            case STOPPED -> "Crawl stopped: %d pages fetched, %d failed."
                    .formatted(run.crawled.get(), run.failed.get());
            case FAILED -> "Crawl aborted by an unexpected internal error.";
            default -> "Crawl is idle.";
        };
    }

    /**
     * A single crawl run: its own frontier, visited set, counters, worker pool and supervisor.
     */
    private final class CrawlRun {

        final List<String> seeds;
        final int maxPages;
        final int maxDepth;
        final int threads;

        final LinkedBlockingQueue<CrawlTask> frontier = new LinkedBlockingQueue<>();
        final Set<String> visited = ConcurrentHashMap.newKeySet();
        final ConcurrentHashMap<String, AtomicLong> nextSlotByHostMs = new ConcurrentHashMap<>();
        final AtomicInteger activeWorkers = new AtomicInteger();
        final AtomicInteger attempted = new AtomicInteger();
        final AtomicInteger crawled = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
        final AtomicInteger discovered = new AtomicInteger();
        final AtomicInteger robotsSkipped = new AtomicInteger();
        final AtomicBoolean stopRequested = new AtomicBoolean();

        final Instant startedAt = Instant.now();
        volatile Instant finishedAt;
        volatile CrawlState state = CrawlState.RUNNING;

        private final ExecutorService workers;
        private final Thread supervisor;

        CrawlRun(List<String> seeds, int maxPages, int maxDepth, int threads) {
            this.seeds = seeds;
            this.maxPages = maxPages;
            this.maxDepth = maxDepth;
            this.threads = threads;
            this.workers = Executors.newFixedThreadPool(threads);
            this.supervisor = new Thread(this::supervise, "crawl-supervisor-" + RUN_COUNTER.incrementAndGet());
            for (String seed : seeds) {
                if (visited.add(seed)) {
                    discovered.incrementAndGet();
                    frontier.offer(new CrawlTask(seed, 0));
                }
            }
        }

        void begin() {
            for (int i = 0; i < threads; i++) {
                workers.submit(this::workerLoop);
            }
            supervisor.start();
        }

        void stop() {
            if (state == CrawlState.RUNNING) {
                state = CrawlState.STOPPING;
            }
            stopRequested.set(true);
            workers.shutdownNow();
            log.info("Crawl stop requested");
        }

        boolean isFinished() {
            return state == CrawlState.COMPLETED || state == CrawlState.STOPPED || state == CrawlState.FAILED;
        }

        /**
         * Waits for every worker to terminate, then finalizes the run state.
         */
        private void supervise() {
            RuntimeException failure = null;
            workers.shutdown();
            try {
                if (!workers.awaitTermination(MAX_CRAWL_DURATION_MINUTES, TimeUnit.MINUTES)) {
                    log.warn("Crawl exceeded {} minutes; forcing stop", MAX_CRAWL_DURATION_MINUTES);
                    stopRequested.set(true);
                    workers.shutdownNow();
                    try {
                        workers.awaitTermination(FORCE_STOP_GRACE_SECONDS, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                stopRequested.set(true);
                workers.shutdownNow();
            } catch (RuntimeException e) {
                failure = e;
                log.error("Crawl supervisor failed unexpectedly", e);
                workers.shutdownNow();
            }
            finishedAt = Instant.now();
            state = failure != null
                    ? CrawlState.FAILED
                    : stopRequested.get() ? CrawlState.STOPPED : CrawlState.COMPLETED;
            synchronized (lifecycleLock) {
                if (activeRun == this) {
                    activeRun = null;
                }
                lastFinishedRun = this;
            }
            log.info("Crawl finished in state {}: crawled={}, failed={}, skippedByRobots={}",
                    state, crawled.get(), failed.get(), robotsSkipped.get());
        }

        private void workerLoop() {
            while (!stopRequested.get() && attempted.get() < maxPages) {
                CrawlTask task;
                try {
                    task = frontier.poll(WORKER_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (task == null) {
                    // Exit only when every worker is idle and nothing is left to fetch.
                    if (activeWorkers.get() == 0 && frontier.isEmpty()) {
                        break;
                    }
                    continue;
                }
                activeWorkers.incrementAndGet();
                try {
                    process(task);
                } catch (RuntimeException e) {
                    failed.incrementAndGet();
                    log.warn("Unexpected error while crawling {}", task.url(), e);
                } finally {
                    activeWorkers.decrementAndGet();
                }
            }
        }

        private void process(CrawlTask task) {
            if (config.isRespectRobotsTxt() && !robotsTxtService.isAllowed(task.url())) {
                robotsSkipped.incrementAndGet();
                log.debug("robots.txt disallows {}", task.url());
                return;
            }
            if (stopRequested.get()) {
                return;
            }
            // Claim a page-fetch budget slot atomically.
            if (attempted.incrementAndGet() > maxPages) {
                return;
            }

            awaitFetchSlot(task.url());

            Connection.Response response;
            try {
                response = Jsoup.connect(task.url())
                        .userAgent(config.getUserAgent())
                        .timeout(config.getTimeoutMs())
                        .ignoreHttpErrors(true)
                        .followRedirects(true)
                        .maxBodySize(MAX_BODY_SIZE_BYTES)
                        .execute();
            } catch (IOException | IllegalArgumentException e) {
                failed.incrementAndGet();
                log.debug("Fetch failed for {}: {}", task.url(), e.getMessage());
                return;
            }

            // Re-validate the final URL: a redirect may have left the allowed domain.
            String finalUrl = response.url() != null ? response.url().toString() : task.url();
            Optional<String> canonical = urlFilter.normalizeAndFilter(finalUrl, null);
            if (canonical.isEmpty()) {
                failed.incrementAndGet();
                log.debug("Final URL is not crawlable: {} -> {}", task.url(), finalUrl);
                return;
            }
            String url = canonical.get();

            String contentType = response.contentType();
            if (response.statusCode() >= 400
                    || contentType == null
                    || !contentType.toLowerCase(Locale.ROOT).contains("text/html")) {
                failed.incrementAndGet();
                return;
            }

            String rawHtml = response.body();
            Document doc;
            try {
                doc = response.parse();
            } catch (IOException e) {
                failed.incrementAndGet();
                log.debug("Parse failed for {}: {}", url, e.getMessage());
                return;
            }

            crawled.incrementAndGet();
            publish(url, rawHtml, response.statusCode());
            enqueueLinks(doc, task.depth());
        }

        /**
         * Hands the fetched page to the document module when its implementation is registered.
         */
        private void publish(String url, String rawHtml, int httpStatus) {
            RawCrawledPage page = RawCrawledPage.builder()
                    .url(url)
                    .subdomain(hostOf(url))
                    .rawHtml(rawHtml)
                    .httpStatus(httpStatus)
                    .crawledAt(Instant.now())
                    .build();
            DocumentService handler = documentService.getIfAvailable();
            if (handler == null) {
                log.debug("No DocumentService implementation registered; skipping persistence for {}", url);
                return;
            }
            try {
                handler.saveOrUpdateFromCrawl(page);
            } catch (RuntimeException e) {
                log.warn("Document module rejected {}: {}", url, e.getMessage());
            }
        }

        private void enqueueLinks(Document doc, int currentDepth) {
            if (currentDepth >= maxDepth) {
                return;
            }
            for (Element link : doc.select("a[href]")) {
                if (stopRequested.get()) {
                    return;
                }
                String absolute = link.absUrl("href");
                if (absolute.isEmpty()) {
                    continue;
                }
                Optional<String> candidate = urlFilter.normalizeAndFilter(absolute, doc.baseUri());
                if (candidate.isEmpty()) {
                    continue;
                }
                String url = candidate.get();
                if (visited.add(url)) {
                    discovered.incrementAndGet();
                    frontier.offer(new CrawlTask(url, currentDepth + 1));
                }
            }
        }

        /**
         * Enforces per-host politeness across all workers: each host is fetched at most
         * once per {@code delayMs} window (or the robots.txt Crawl-delay, whichever is
         * larger) by atomically reserving a time slot and sleeping until it starts.
         */
        private void awaitFetchSlot(String url) {
            long robotsDelay = config.isRespectRobotsTxt() ? robotsTxtService.crawlDelayMs(url) : 0L;
            long minDelayMs = Math.max(config.getDelayMs(), robotsDelay);
            if (minDelayMs <= 0) {
                return;
            }
            AtomicLong nextSlot = nextSlotByHostMs.computeIfAbsent(
                    hostOf(url), host -> new AtomicLong(System.currentTimeMillis()));
            long reservedAt;
            while (true) {
                long slot = nextSlot.get();
                long earliest = Math.max(slot, System.currentTimeMillis());
                if (nextSlot.compareAndSet(slot, earliest + minDelayMs)) {
                    reservedAt = earliest;
                    break;
                }
            }
            long sleepMs = reservedAt - System.currentTimeMillis();
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private String hostOf(String url) {
            try {
                URI uri = URI.create(url);
                String host = uri.getHost();
                return host != null ? host.toLowerCase(Locale.ROOT) : url;
            } catch (IllegalArgumentException e) {
                return url;
            }
        }

        /** One frontier entry: a canonical URL and the depth at which it was discovered. */
        private record CrawlTask(String url, int depth) {
        }
    }
}
