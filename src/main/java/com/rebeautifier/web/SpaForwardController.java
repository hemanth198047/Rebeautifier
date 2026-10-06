package com.rebeautifier.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards client-side routes to the built SPA when the API serves static assets.
 */
@Controller
public class SpaForwardController {

    @GetMapping(value = {
            "/",
            "/library",
            "/library/**",
            "/login",
            "/register",
            "/books/**",
            "/backup",
            "/settings",
            "/search"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
