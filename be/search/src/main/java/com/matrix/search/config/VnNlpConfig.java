package com.matrix.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.pipeline.Utils;
import vn.pipeline.VnCoreNLP;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * VnCoreNLP pipeline configuration bound from {@code app.search.vncorenlp}.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.search.vncorenlp")
public class VnNlpConfig {

    private String modelsBaseDir = "../models/vncorenlp";
    private List<String> annotators = new ArrayList<>(List.of("wseg"));

    @Bean
    public VnCoreNLP vnCoreNLP() throws IOException {
        Path basePath = Paths.get(modelsBaseDir).toAbsolutePath().normalize();
        Utils.jarDir = basePath.toString();
        return new VnCoreNLP(annotators.toArray(new String[0]));
    }
}
