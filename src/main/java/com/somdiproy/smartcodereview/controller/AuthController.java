package com.somdiproy.smartcodereview.controller;

import com.somdiproy.smartcodereview.dto.OtpVerificationRequest;
import com.somdiproy.smartcodereview.dto.SessionCreateRequest;
import com.somdiproy.smartcodereview.exception.InvalidOtpException;
import com.somdiproy.smartcodereview.exception.SessionNotFoundException;
import com.somdiproy.smartcodereview.model.Session;
import com.somdiproy.smartcodereview.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


/**
 * Controller for authentication and session management
 */
@Slf4j
@Controller
@RequestMapping("/auth")
public class AuthController {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthController.class);
    
    private final SessionService sessionService;
    
    @Autowired
    public AuthController(SessionService sessionService) {
        this.sessionService = sessionService;
    }
    
    /**
     * Create session and send OTP
     */
    @PostMapping("/session/create")
    public String createSession(@Valid @ModelAttribute SessionCreateRequest request,
                               BindingResult bindingResult,
                               HttpServletRequest httpRequest,
                               RedirectAttributes redirectAttributes) {
        
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Please provide a valid email address");
            return "redirect:/";
        }
        
        try {
        	secureTokenService.storeSessionToken(request.getEmail(), request.getGithubToken());
            Session session = sessionService.createSession(request.getEmail(), httpRequest);
            
            // Redirect to OTP verification page
            return "redirect:/auth/verify?sessionId=" + session.getSessionId();
            
        } catch (Exception e) {
            log.error("Failed to create session", e);
            redirectAttributes.addFlashAttribute("error", "Failed to create session. Please try again.");
            return "redirect:/";
        }
    }
    
    @PostMapping("/validate-token")
    @ResponseBody
    public Map<String, Object> validateToken(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        Map<String, Object> response = new HashMap<>();
        
        try {
            GitHub github = new GitHubBuilder().withOAuthToken(token).build();
            GHMyself myself = github.getMyself();
            GHRateLimit rateLimit = github.getRateLimit();
            
            response.put("valid", true);
            response.put("username", myself.getLogin());
            response.put("rateLimit", rateLimit.getRemaining() + "/" + rateLimit.getLimit());
            response.put("scopes", github.getMeta().getScopes());
            
        } catch (Exception e) {
            response.put("valid", false);
            response.put("error", "Invalid token or insufficient permissions");
        }
        
        return response;
    }
    
    /**
     * Show OTP verification page
     */
    @GetMapping("/verify")
    public String showVerificationPage(@RequestParam String sessionId, Model model) {
        try {
            Session session = sessionService.getSession(sessionId);
            
            if (session.isVerified()) {
                // Already verified, redirect to repository selection
                return "redirect:/repository/select?sessionId=" + sessionId;
            }
            
            model.addAttribute("sessionId", sessionId);
            model.addAttribute("email", session.getEmailMasked());
            model.addAttribute("remainingAttempts", 3 - session.getOtpAttempts());
            
            return "otp-verification";
            
        } catch (SessionNotFoundException e) {
            model.addAttribute("error", "Session not found or expired");
            return "home";
        }
    }
    
    /**
     * Verify OTP
     */
    @PostMapping("/verify")
    public String verifyOtp(@Valid @ModelAttribute OtpVerificationRequest request,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {
        
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Please enter a valid 6-digit OTP");
            return "redirect:/auth/verify?sessionId=" + request.getSessionId();
        }
        
        try {
            Session session = sessionService.verifyOtp(request.getSessionId(), request.getOtp());
            
            // Redirect to repository selection with clear logging
            log.info("🎯 OTP verified successfully! Redirecting to repository selection for session: {}", session.getSessionId());
            return "redirect:/repository?sessionId=" + session.getSessionId();
            
        } catch (InvalidOtpException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/auth/verify?sessionId=" + request.getSessionId();
            
        } catch (SessionNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", "Session expired. Please start again.");
            return "redirect:/";
        }
    }
    
    /**
     * Resend OTP
     */
    @PostMapping("/resend-otp")
    public String resendOtp(@RequestParam String sessionId,
                           RedirectAttributes redirectAttributes) {
        try {
            sessionService.resendOtp(sessionId);
            redirectAttributes.addFlashAttribute("success", "OTP has been resent to your email");
            
        } catch (Exception e) {
            log.error("Failed to resend OTP", e);
            redirectAttributes.addFlashAttribute("error", "Failed to resend OTP. Please try again.");
        }
        
        return "redirect:/auth/verify?sessionId=" + sessionId;
    }
    
    /**
     * Logout and delete session
     */
    @PostMapping("/logout")
    public String logout(@RequestParam String sessionId,
                        RedirectAttributes redirectAttributes) {
        try {
            sessionService.deleteSession(sessionId);
            redirectAttributes.addFlashAttribute("success", "You have been logged out successfully");
        } catch (Exception e) {
            log.error("Failed to logout", e);
        }
        
        return "redirect:/";
    }
}