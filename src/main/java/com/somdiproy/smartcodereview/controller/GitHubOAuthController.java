package com.somdiproy.smartcodereview.controller;

import com.somdiproy.smartcodereview.service.GitHubService;
import com.somdiproy.smartcodereview.service.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for GitHub OAuth integration
 */
@Controller
@RequestMapping("/auth/github")
public class GitHubOAuthController {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GitHubOAuthController.class);
    
    private final SessionService sessionService;
    private final GitHubService gitHubService;
    
    @Autowired
    public GitHubOAuthController(SessionService sessionService, GitHubService gitHubService) {
        this.sessionService = sessionService;
        this.gitHubService = gitHubService;
    }
    
    /**
     * Initiate GitHub OAuth flow
     */
    @GetMapping("/login")
    public String initiateGitHubLogin(@RequestParam String sessionId,
                                    @RequestParam(required = false) String repoUrl,
                                    Model model) {
        try {
            // Validate session
            sessionService.getSession(sessionId);
            
            // Store session and repo info for callback
            model.addAttribute("sessionId", sessionId);
            model.addAttribute("repoUrl", repoUrl);
            
            log.info("🔐 Initiating GitHub OAuth for session: {}", sessionId);
            
            // Redirect to OAuth authorization
            return "redirect:/oauth2/authorization/github?sessionId=" + sessionId + 
                   (repoUrl != null ? "&repoUrl=" + repoUrl : "");
                   
        } catch (Exception e) {
            log.error("Failed to initiate GitHub login", e);
            return "redirect:/auth/github/error?error=invalid_session";
        }
    }
    
    /**
     * Handle successful OAuth callback
     */
    @GetMapping("/success")
    public String handleGitHubSuccess(@RequestParam String sessionId,
                                    @RequestParam(required = false) String repoUrl,
                                    @RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient authorizedClient,
                                    @AuthenticationPrincipal OAuth2User principal,
                                    RedirectAttributes redirectAttributes) {
        try {
            String accessToken = authorizedClient.getAccessToken().getTokenValue();
            String githubUsername = principal.getAttribute("login");
            String githubEmail = principal.getAttribute("email");
            
            log.info("✅ GitHub OAuth successful for user: {} (Session: {})", githubUsername, sessionId);
            
            // Update session with GitHub token
            sessionService.updateGithubToken(sessionId, accessToken);
            
            // Test repository access if URL provided
            if (repoUrl != null && gitHubService.isValidRepositoryUrl(repoUrl)) {
                if (gitHubService.canAccessRepository(repoUrl, accessToken)) {
                    log.info("🎯 Successfully verified access to repository: {}", repoUrl);
                    redirectAttributes.addFlashAttribute("success", 
                        "GitHub access granted! You can now analyze private repositories.");
                    return "redirect:/repository/branches?sessionId=" + sessionId + "&repoUrl=" + repoUrl;
                } else {
                    redirectAttributes.addFlashAttribute("warning", 
                        "GitHub connected, but you don't have access to the specified repository.");
                }
            }
            
            redirectAttributes.addFlashAttribute("success", 
                "GitHub account connected successfully! You can now access private repositories.");
            return "redirect:/repository?sessionId=" + sessionId;
            
        } catch (Exception e) {
            log.error("Error handling GitHub OAuth success", e);
            redirectAttributes.addFlashAttribute("error", 
                "Failed to complete GitHub authentication. Please try again.");
            return "redirect:/repository?sessionId=" + sessionId;
        }
    }
    
    /**
     * Handle OAuth errors
     */
    @GetMapping("/error")
    public String handleGitHubError(@RequestParam(required = false) String error,
                                  @RequestParam(required = false) String sessionId,
                                  Model model) {
        
        log.warn("❌ GitHub OAuth error: {} for session: {}", error, sessionId);
        
        String errorMessage = switch (error != null ? error : "unknown") {
            case "access_denied" -> "GitHub access was denied. You can still analyze public repositories.";
            case "invalid_scope" -> "Invalid permissions requested. Please contact support.";
            case "invalid_session" -> "Your session has expired. Please start again.";
            default -> "GitHub authentication failed. You can still analyze public repositories.";
        };
        
        model.addAttribute("error", errorMessage);
        model.addAttribute("sessionId", sessionId);
        
        return "github-auth-error";
    }
    
    /**
     * Disconnect GitHub account
     */
    @PostMapping("/disconnect")
    public String disconnectGitHub(@RequestParam String sessionId,
                                 RedirectAttributes redirectAttributes) {
        try {
            sessionService.updateGithubToken(sessionId, null);
            
            log.info("🔓 GitHub account disconnected for session: {}", sessionId);
            redirectAttributes.addFlashAttribute("success", "GitHub account disconnected.");
            
        } catch (Exception e) {
            log.error("Failed to disconnect GitHub", e);
            redirectAttributes.addFlashAttribute("error", "Failed to disconnect GitHub account.");
        }
        
        return "redirect:/repository?sessionId=" + sessionId;
    }
    
    /**
     * Check GitHub connection status
     */
    @GetMapping("/status")
    @ResponseBody
    public GitHubConnectionStatus getConnectionStatus(@RequestParam String sessionId) {
        try {
            var session = sessionService.getSession(sessionId);
            boolean connected = session.getGithubToken() != null;
            
            return GitHubConnectionStatus.builder()
                    .connected(connected)
                    .canAccessPrivateRepos(connected)
                    .username(connected ? "Connected" : null)
                    .message(connected ? "GitHub account connected" : "No GitHub connection")
                    .build();
                    
        } catch (Exception e) {
            return GitHubConnectionStatus.builder()
                    .connected(false)
                    .canAccessPrivateRepos(false)
                    .message("Session not found")
                    .build();
        }
    }
    
    /**
     * GitHub connection status response
     */
    public static class GitHubConnectionStatus {
        private boolean connected;
        private boolean canAccessPrivateRepos;
        private String username;
        private String message;
        
        public GitHubConnectionStatus() {}
        
        private GitHubConnectionStatus(Builder builder) {
            this.connected = builder.connected;
            this.canAccessPrivateRepos = builder.canAccessPrivateRepos;
            this.username = builder.username;
            this.message = builder.message;
        }
        
        // Getters
        public boolean isConnected() { return connected; }
        public boolean isCanAccessPrivateRepos() { return canAccessPrivateRepos; }
        public String getUsername() { return username; }
        public String getMessage() { return message; }
        
        // Setters
        public void setConnected(boolean connected) { this.connected = connected; }
        public void setCanAccessPrivateRepos(boolean canAccessPrivateRepos) { this.canAccessPrivateRepos = canAccessPrivateRepos; }
        public void setUsername(String username) { this.username = username; }
        public void setMessage(String message) { this.message = message; }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private boolean connected, canAccessPrivateRepos;
            private String username, message;
            
            public Builder connected(boolean connected) { this.connected = connected; return this; }
            public Builder canAccessPrivateRepos(boolean canAccessPrivateRepos) { this.canAccessPrivateRepos = canAccessPrivateRepos; return this; }
            public Builder username(String username) { this.username = username; return this; }
            public Builder message(String message) { this.message = message; return this; }
            
            public GitHubConnectionStatus build() {
                return new GitHubConnectionStatus(this);
            }
        }
    }
}