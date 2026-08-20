package com.nyxn.products_api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI productsApiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Products Catalog API")
                        .version("1.0")
                        .description("REST API for managing an e-commerce product catalog"));
    }
}
