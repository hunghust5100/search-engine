package com.matrix.search.config;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Apache Lucene base directory configuration.
 */
@Configuration
public class LuceneConfig {

    @Value("${app.search.lucene.index-dir:../data/lucene-index}")
    private String indexDir;

    @Bean(destroyMethod = "close")
    public Directory luceneDirectory() throws IOException {
        Path path = Paths.get(indexDir).toAbsolutePath().normalize();
        Files.createDirectories(path);
        return FSDirectory.open(path);
    }
}
