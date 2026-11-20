package com.matrix.search.crawler.robots;

import java.util.List;

/**
 * Immutable robots.txt rule set for a single origin, already scoped to one user-agent group.
 *
 * <p>Rule matching follows the "longest match wins" principle used by major crawlers:
 * the rule whose path prefix is the longest match decides the outcome, and an Allow rule
 * wins over a Disallow rule of equal length. When no rule matches, crawling is allowed.</p>
 */
public final class RobotsRules {

    /** A single Allow/Disallow path prefix rule. */
    record Rule(String path, boolean allowed) {
    }

    /** Ruleset used when robots.txt is absent or unreachable: everything is allowed. */
    public static final RobotsRules ALLOW_ALL = new RobotsRules(List.of(), 0L);

    private final List<Rule> rules;
    private final long crawlDelayMs;

    public RobotsRules(List<Rule> rules, long crawlDelayMs) {
        this.rules = List.copyOf(rules);
        this.crawlDelayMs = crawlDelayMs;
    }

    /**
     * Builds a ruleset that disallows every path (used for HTTP 401/403 robots.txt).
     */
    public static RobotsRules disallowAll() {
        return new RobotsRules(List.of(new Rule("/", false)), 0L);
    }

    /**
     * Checks whether the given path (including its query string) may be fetched.
     */
    public boolean isAllowed(String path) {
        Rule best = null;
        for (Rule rule : rules) {
            if (!path.startsWith(rule.path())) {
                continue;
            }
            if (best == null
                    || rule.path().length() > best.path().length()
                    || (rule.path().length() == best.path().length() && rule.allowed() && !best.allowed())) {
                best = rule;
            }
        }
        return best == null || best.allowed();
    }

    /**
     * Crawl delay requested by robots.txt in milliseconds (0 when not specified).
     */
    public long crawlDelayMs() {
        return crawlDelayMs;
    }
}
