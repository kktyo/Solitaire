package com.solitaire.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping({"/boot", "/login", "/register", "/home", "/game"})
    public String spaRoutes() {
        return "forward:/index.html";
    }
}
