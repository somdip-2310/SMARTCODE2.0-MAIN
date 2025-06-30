package com.somdiproy.smartcodereview.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ApiController {
    
    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "smart-code-review");
        return response;
    }
    
    @GetMapping("/version")
    public Map<String, String> version() {
        Map<String, String> response = new HashMap<>();
        response.put("version", "1.0.0");
        response.put("build", "2024.01");
        return response;
    }
}