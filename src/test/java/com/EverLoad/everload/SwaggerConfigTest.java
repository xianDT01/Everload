package com.EverLoad.everload;

import com.EverLoad.everload.config.SwaggerConfig;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.servers.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { SwaggerConfig.class })
class SwaggerConfigTest {

    @Autowired
    ApplicationContext ctx;

    @Autowired
    OpenAPI openAPI;

    @Test
    void beanOpenApi_existeEnContexto() {
        assertNotNull(ctx.getBean(OpenAPI.class));
        assertNotNull(openAPI);
    }

    @Test
    void info_titulo_version_contacto_y_licencia_ok() {
        Info info = openAPI.getInfo();
        assertNotNull(info);
        assertEquals("EverLoad API", info.getTitle());
        assertEquals("v1.0", info.getVersion());
        assertNotNull(info.getDescription());
        assertTrue(info.getDescription().contains("EverLoad"));

        Contact contact = info.getContact();
        assertNotNull(contact);
        assertEquals("XiánDT", contact.getName());
        assertEquals("xiandt01@gmail.com", contact.getEmail());
        assertEquals("https://github.com/xianDT01/Everload", contact.getUrl());

        License license = info.getLicense();
        assertNotNull(license);
        assertTrue(license.getName().contains("Open Source"));
        assertEquals("https://github.com/xianDT01/Everload/blob/main/LICENSE", license.getUrl());
    }

    @Test
    void servers_y_externalDocs_ok() {
        List<Server> servers = openAPI.getServers();
        assertNotNull(servers);
        assertEquals(1, servers.size());
        assertEquals("http://localhost:8080", servers.get(0).getUrl());
        assertEquals("Desarrollo local", servers.get(0).getDescription());

        ExternalDocumentation ext = openAPI.getExternalDocs();
        assertNotNull(ext);
        assertEquals("Repositorio en GitHub", ext.getDescription());
        assertEquals("https://github.com/xianDT01/Everload", ext.getUrl());
    }
}