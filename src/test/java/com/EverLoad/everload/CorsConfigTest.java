package com.EverLoad.everload;

import com.EverLoad.everload.config.CorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CorsConfigTest.TestController.class)
@Import(CorsConfig.class) // <<-- usa tu configuración real
class CorsConfigTest {

    @Autowired
    MockMvc mvc;

    @RestController
    static class TestController {
        @GetMapping("/api/test")
        public String ok() { return "ok"; }
    }

    @Test
    void preflight_OPTIONS_incluye_headers_cors() throws Exception {
        mvc.perform(
                        options("/api/test")
                                .header("Origin", "http://localhost:4200")
                                .header("Access-Control-Request-Method", "GET")
                                // Spring suele reflejar los request-headers, no devuelve "*" literal
                                .header("Access-Control-Request-Headers", "X-Requested-With")
                )
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("GET")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("X-Requested-With")))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("Content-Disposition")));
    }

}