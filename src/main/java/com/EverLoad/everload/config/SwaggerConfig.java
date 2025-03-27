package com.EverLoad.everload.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("EverLoad API")
                        .description("""
                                EverLoad - Descarga Música y Videos de Internet

                                EverLoad es una aplicación desarrollada en Spring Boot y Angular 15 que permite descargar fácilmente videos y audios desde las plataformas más populares, todo de forma rápida, segura y desde tu red privada.

                                EverLoad is an app built with Spring Boot and Angular 15 that allows you to easily download videos and audio from the most popular platforms, quickly, securely, and within your private network.

                                EverLoad é unha aplicación feita con Spring Boot e Angular 15 que permite descargar vídeos e audios das plataformas máis populares de forma rápida, segura e desde a túa rede privada.
                                """)
                        .version("v1.0")
                        .contact(new Contact()
                                .name("XiánDT")
                                .email("xiandt01@gmail.com")
                                .url("https://github.com/xianDT01/Everload"))
                        .license(new License()
                                .name("Open Source con atribución")
                                .url("https://github.com/xianDT01/Everload/blob/main/LICENSE")))
                .externalDocs(new ExternalDocumentation()
                        .description("Repositorio en GitHub")
                        .url("https://github.com/xianDT01/Everload"))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Desarrollo local")

                ));
    }
}
