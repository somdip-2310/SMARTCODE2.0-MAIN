package com.somdiproy.smartcodereview.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    
	@GetMapping("/")
	public String home(Model model) {
	    model.addAttribute("title", "Smart Code Review Platform");
	    // Flash attributes are automatically added to the model by Spring
	    // No additional code needed here, but we need to ensure they're available
	    return "home";
	}
    
    @GetMapping("/error")
    public String error(Model model) {
        return "error";
    }
}