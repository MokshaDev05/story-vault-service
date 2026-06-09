package com.moksha.storyvault.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping({"/library", "/login"})
    public String spa() {
        return "forward:/index.html";
    }
}
