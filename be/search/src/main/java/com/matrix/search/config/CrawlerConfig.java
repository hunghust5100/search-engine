package com.matrix.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Crawler configuration properties bound from {@code app.search.crawler}.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.search.crawler")
public class CrawlerConfig {

    private List<String> seedUrls = new ArrayList<>(List.of("https://hust.edu.vn"));
    private String allowedDomainSuffix = "hust.edu.vn";
    private int maxPages = 500;
    private int maxDepth = 4;
    private int threads = 8;
    private boolean respectRobotsTxt = true;
    private long delayMs = 300;
    private int timeoutMs = 10000;
    private String userAgent = "HustSearchBot/1.0";
}
