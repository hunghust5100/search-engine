package com.matrix.search.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI hustSearchOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("HUST Search Engine API")
                        .description("API crawl, đánh chỉ mục và tìm kiếm tài liệu từ hust.edu.vn")
                        .version("0.0.1-SNAPSHOT"));
    }
}
