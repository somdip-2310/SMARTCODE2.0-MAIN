package com.somdiproy.smartcodereview.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import com.somdiproy.smartcodereview.event.SessionExpiredEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service for secure temporary storage of GitHub tokens in memory only.
 * Tokens are never persisted to database and are automatically cleaned up after 1 hour.
 */
@Slf4j
@Service
public class SecureTokenService {
    
    private final Map<String, String> sessionTokens = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SessionService.class);
    
    @Value("${encryption.key:defaultSecretKey123456789012345678901234567890}")
    private String encryptionKey;
    
    /**
     * Store GitHub token temporarily for session (in memory only)
     */
    public void storeSessionToken(String sessionId, String githubToken) {
        if (githubToken == null || githubToken.isEmpty()) {
            throw new IllegalArgumentException("GitHub token cannot be empty");
        }
        
        if (sessionId == null || sessionId.isEmpty()) {
            throw new IllegalArgumentException("Session ID cannot be empty");
        }
        
        // Store in memory only - never persist to database
        sessionTokens.put(sessionId, githubToken);
        
        // Schedule automatic cleanup after 1 hour
        scheduler.schedule(() -> {
            sessionTokens.remove(sessionId);
            log.info("Automatically cleaned up token for session: {}", sessionId);
        }, 1, TimeUnit.HOURS);
        
        log.info("Temporarily stored GitHub token for session: {}", sessionId);
    }
    
    /**
     * Retrieve token for session
     */
    public String getSessionToken(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        return sessionTokens.get(sessionId);
    }
    
    /**
     * Remove token immediately
     */
    public void removeSessionToken(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        
        String removed = sessionTokens.remove(sessionId);
        if (removed != null) {
            log.info("Removed GitHub token for session: {}", sessionId);
        }
    }
    
    /**
     * Validate GitHub token format
     * GitHub tokens start with 'ghp_' followed by 36+ alphanumeric characters
     */
    public boolean isValidGitHubToken(String token) {
        return token != null && token.matches("ghp_[a-zA-Z0-9]{36,}");
    }
    
    /**
     * Handle session expired events to clean up associated tokens
     */
    @EventListener
    public void handleSessionExpired(SessionExpiredEvent event) {
        removeSessionToken(event.getSessionId());
        log.info("Cleaned up token for expired session: {}", event.getSessionId());
    }
    
    /**
     * Get the current number of stored tokens (for monitoring)
     */
    public int getStoredTokenCount() {
        return sessionTokens.size();
    }
    
    /**
     * Check if a session has a stored token
     */
    public boolean hasToken(String sessionId) {
        return sessionId != null && sessionTokens.containsKey(sessionId);
    }
    
    /**
     * Clean up resources on application shutdown
     */
    @PreDestroy
    public void cleanup() {
        try {
            log.info("Shutting down SecureTokenService, clearing {} tokens", sessionTokens.size());
            scheduler.shutdown();
            
            // Wait for scheduled tasks to complete
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            
            // Clear all tokens
            sessionTokens.clear();
            log.info("Cleaned up all temporary tokens on shutdown");
        } catch (InterruptedException e) {
            log.error("Error during SecureTokenService shutdown", e);
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}