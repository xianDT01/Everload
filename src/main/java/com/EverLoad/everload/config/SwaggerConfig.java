package com.EverLoad.everload.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;

public class SwaggerConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("EverLoad API")
                        .description("Documentación de la API de EverLoad")
                        .version("v1.0"))
                .externalDocs(new ExternalDocumentation()
                        .description("Repositorio en GitHub")
                        .url("https://github.com/xianDT01/Everload"));
    }
}
