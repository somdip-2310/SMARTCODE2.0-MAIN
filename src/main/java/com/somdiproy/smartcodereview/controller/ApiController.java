package com.somdiproy.smartcodereview.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.somdiproy.smartcodereview.model.Session;
import com.somdiproy.smartcodereview.service.SessionService;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class ApiController {
    
    private final SessionService sessionService;
    
    @Autowired
    public ApiController(SessionService sessionService) {
        this.sessionService = sessionService;
    }
    
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
    
    @GetMapping("/debug/session/{sessionId}")
    @ResponseBody
    public Map<String, Object> debugSession(@PathVariable String sessionId) {
        try {
            Session session = sessionService.getSession(sessionId);
            Map<String, Object> debug = new HashMap<>();
            debug.put("sessionId", session.getSessionId());
            debug.put("verificationStatus", session.getVerificationStatus());
            debug.put("isVerified", session.isVerified());
            debug.put("getVerified", session.getVerified());
            debug.put("email", session.getEmailMasked());
            debug.put("scanCount", session.getScanCount());
            debug.put("ttl", session.getTtl());
            debug.put("expiresAt", session.getExpiresAt());
            return debug;
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            error.put("type", e.getClass().getSimpleName());
            return error;
        }
    }
}