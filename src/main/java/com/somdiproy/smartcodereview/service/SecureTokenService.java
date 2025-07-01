package com.somdiproy.smartcodereview.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SecureTokenService {
    
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GitHubService.class);
    private final Map<String, String> sessionTokens = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    @Value("${encryption.key:defaultSecretKey123456789012345678901234567890}")
    private String encryptionKey;
    
    /**
     * Store GitHub token temporarily for session (in memory only)
     */
    public void storeSessionToken(String sessionId, String githubToken) {
        if (githubToken == null || githubToken.isEmpty()) {
            throw new IllegalArgumentException("GitHub token cannot be empty");
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
        return sessionTokens.get(sessionId);
    }
    
    /**
     * Remove token immediately
     */
    public void removeSessionToken(String sessionId) {
        sessionTokens.remove(sessionId);
        log.info("Removed GitHub token for session: {}", sessionId);
    }
    
    /**
     * Validate GitHub token format
     */
    public boolean isValidGitHubToken(String token) {
        return token != null && token.matches("ghp_[a-zA-Z0-9]{36,}");
    }
    @EventListener
    public void handleSessionExpired(SessionExpiredEvent event) {
        removeSessionToken(event.getSessionId());
        log.info("Cleaned up token for expired session: {}", event.getSessionId());
    }

    @PreDestroy
    public void cleanup() {
        scheduler.shutdown();
        sessionTokens.clear();
        log.info("Cleaned up all temporary tokens on shutdown");
    }
}