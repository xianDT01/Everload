package com.EverLoad.everload.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Forwards any GET request that doesn't match an API endpoint or a static
 * resource to index.html, letting Angular's router take over.
 *
 * The pattern "/{path:[^\\.]*}" matches paths with no dot (so it skips
 * requests for .js, .css, .png, etc., which Spring serves as static files).
 */
@Controller
public class SpaController {

    @RequestMapping(value = {
        "/",
        "/{path:[^\\.]*}",
        "/{path:[^\\.]*}/{sub:[^\\.]*}",
        "/{path:[^\\.]*}/{sub:[^\\.]*}/{deep:[^\\.]*}"
    })
    public String forwardToAngular() {
        return "forward:/index.html";
    }
}
