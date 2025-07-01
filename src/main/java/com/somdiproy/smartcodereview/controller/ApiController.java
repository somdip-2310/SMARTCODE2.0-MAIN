package com.somdiproy.smartcodereview.controller;

import com.somdiproy.smartcodereview.model.Session;
import com.somdiproy.smartcodereview.service.GitHubService;
import com.somdiproy.smartcodereview.service.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
public class ApiController {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApiController.class);
    
    private final SessionService sessionService;
    private final GitHubService gitHubService;
    
    @Autowired
    public ApiController(SessionService sessionService, GitHubService gitHubService) {
        this.sessionService = sessionService;
        this.gitHubService = gitHubService;
    }
    
    @GetMapping("/v1/health")
    public Map<String, String> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "smart-code-review");
        return response;
    }
    
    @GetMapping("/v1/version")
    public Map<String, String> version() {
        Map<String, String> response = new HashMap<>();
        response.put("version", "1.0.0");
        response.put("build", "2024.01");
        return response;
    }
    
    @GetMapping("/branch-stats")
    @ResponseBody
    public Map<String, Object> getBranchStats(@RequestParam String sessionId,
                                              @RequestParam String repoUrl,
                                              @RequestParam String branch) {
        try {
            log.info("🔍 Fetching branch stats for branch: {} in repo: {}", branch, repoUrl);
            
            // Validate session
            sessionService.getSession(sessionId);
            
            // Get file stats for the specific branch
            GitHubService.FileAnalysisStats stats = gitHubService.getFileStats(repoUrl, branch, null);
            
            Map<String, Object> response = new HashMap<>();
            response.put("totalFiles", stats.getTotalFiles());
            response.put("eligibleFiles", stats.getEligibleFiles());
            response.put("estimatedTokens", stats.getEstimatedTokens());
            response.put("estimatedCost", stats.getEstimatedCost());
            response.put("branch", branch);
            response.put("status", "success");
            
            log.info("📊 Branch stats for {}: {} eligible files, {} tokens", 
                     branch, stats.getEligibleFiles(), stats.getEstimatedTokens());
            
            return response;
            
        } catch (Exception e) {
            log.error("💥 Failed to get branch stats for branch: {} in repo: {}", branch, repoUrl, e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Failed to analyze branch: " + e.getMessage());
            errorResponse.put("totalFiles", 0);
            errorResponse.put("eligibleFiles", 0);
            errorResponse.put("estimatedTokens", 0);
            errorResponse.put("estimatedCost", 0.0);
            errorResponse.put("branch", branch);
            
            return errorResponse;
        }
    }
    
    @GetMapping("/v1/debug/session/{sessionId}")
    @ResponseBody
    public Map<String, Object> debugSession(@PathVariable String sessionId) {
        try {
            Session session = sessionService.getSession(sessionId);
            Map<String, Object> debug = new HashMap<>();
            debug.put("sessionId", session.getSessionId());
            debug.put("verificationStatus", session.getVerificationStatus());
            debug.put("isVerified", session.isVerified());
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