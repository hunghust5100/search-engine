package com.matrix.search.crawler.robots;

import com.matrix.search.config.CrawlerConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches, parses and caches {@code robots.txt} rules per origin, scoped to the
 * configured crawler user agent.
 *
 * <p>Behaviour follows the robots exclusion protocol conventions used by major crawlers:
 * a 401/403 response disallows everything, any other missing or unreachable robots.txt
 * allows everything, and the most specific matching user-agent group wins (falling back
 * to the {@code *} group).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RobotsTxtService {

    private final CrawlerConfig config;
    private final ConcurrentHashMap<String, RobotsRules> rulesByOrigin = new ConcurrentHashMap<>();

    /**
     * Checks whether the given absolute URL may be fetched under its origin's robots.txt.
     */
    public boolean isAllowed(String url) {
        try {
            URI uri = URI.create(url.trim());
            if (uri.getHost() == null) {
                return false;
            }
            return rulesFor(uri).isAllowed(targetPath(uri));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Returns the crawl delay (milliseconds) requested by the URL's robots.txt,
     * or 0 when the file is absent or specifies no delay.
     */
    public long crawlDelayMs(String url) {
        try {
            URI uri = URI.create(url.trim());
            if (uri.getHost() == null) {
                return 0L;
            }
            return rulesFor(uri).crawlDelayMs();
        } catch (IllegalArgumentException e) {
            return 0L;
        }
    }

    /**
     * Clears cached robots.txt rules so they are re-fetched on the next check.
     */
    public void clearCache() {
        rulesByOrigin.clear();
    }

    private RobotsRules rulesFor(URI url) {
        String origin = url.getScheme().toLowerCase(Locale.ROOT)
                + "://"
                + url.getHost().toLowerCase(Locale.ROOT);
        return rulesByOrigin.computeIfAbsent(origin, this::fetchRules);
    }

    private RobotsRules fetchRules(String origin) {
        String robotsUrl = origin + "/robots.txt";
        try {
            Connection.Response response = Jsoup.connect(robotsUrl)
                    .userAgent(config.getUserAgent())
                    .timeout(config.getTimeoutMs())
                    .ignoreHttpErrors(true)
                    .followRedirects(true)
                    .execute();
            int status = response.statusCode();
            if (status == 200) {
                return parse(response.body(), botToken());
            }
            if (status == 401 || status == 403) {
                log.info("robots.txt at {} returned {}; disallowing all crawling for this origin", robotsUrl, status);
                return RobotsRules.disallowAll();
            }
            log.info("robots.txt at {} returned {}; crawling permitted", robotsUrl, status);
            return RobotsRules.ALLOW_ALL;
        } catch (IOException | RuntimeException e) {
            log.info("No usable robots.txt at {} ({}); crawling permitted", robotsUrl, e.getMessage());
            return RobotsRules.ALLOW_ALL;
        }
    }

    /**
     * Parses a robots.txt body and selects the rule group for the given bot token,
     * falling back to the {@code *} group when no group matches.
     */
    private RobotsRules parse(String body, String botToken) {
        Map<String, Group> groups = new LinkedHashMap<>();
        List<String> currentAgents = new ArrayList<>();
        boolean afterDirective = false;

        for (String rawLine : body.split("\n")) {
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String directive = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();

            if ("user-agent".equals(directive)) {
                if (value.isEmpty()) {
                    continue;
                }
                if (afterDirective) {
                    // A directive line ends the previous group header and starts a new group.
                    currentAgents = new ArrayList<>();
                    afterDirective = false;
                }
                currentAgents.add(value.toLowerCase(Locale.ROOT));
                continue;
            }

            // An empty directive value has no effect (an empty Disallow means allow all).
            if (value.isEmpty()) {
                continue;
            }
            afterDirective = true;
            switch (directive) {
                case "allow", "disallow" -> {
                    for (String agent : currentAgents) {
                        groups.computeIfAbsent(agent, a -> new Group())
                                .rules.add(new RobotsRules.Rule(value, "allow".equals(directive)));
                    }
                }
                case "crawl-delay" -> {
                    long delayMs = parseDelayMs(value);
                    if (delayMs >= 0) {
                        for (String agent : currentAgents) {
                            Group group = groups.computeIfAbsent(agent, a -> new Group());
                            group.crawlDelayMs = Math.max(group.crawlDelayMs, delayMs);
                        }
                    }
                }
                default -> {
                    // Unknown directives are ignored per the robots exclusion protocol.
                }
            }
        }

        Group group = groups.get(botToken);
        if (group == null) {
            group = groups.get("*");
        }
        if (group == null) {
            return RobotsRules.ALLOW_ALL;
        }
        return new RobotsRules(group.rules, group.crawlDelayMs);
    }

    private long parseDelayMs(String value) {
        try {
            return (long) (Double.parseDouble(value) * 1000L);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String targetPath(URI uri) {
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        String query = uri.getRawQuery();
        return query == null ? path : path + "?" + query;
    }

    private String botToken() {
        String userAgent = config.getUserAgent() == null ? "" : config.getUserAgent();
        int slash = userAgent.indexOf('/');
        return (slash > 0 ? userAgent.substring(0, slash) : userAgent).trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Removes an unescaped trailing comment from a robots.txt line.
     */
    private String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash >= 0 ? line.substring(0, hash) : line;
    }

    /** Mutable builder state for one user-agent group while parsing. */
    private static final class Group {
        private final List<RobotsRules.Rule> rules = new ArrayList<>();
        private long crawlDelayMs;
    }
}
