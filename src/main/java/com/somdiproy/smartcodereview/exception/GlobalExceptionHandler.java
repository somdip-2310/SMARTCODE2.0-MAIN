package com.somdiproy.smartcodereview.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;


@ControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(SessionNotFoundException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String handleSessionNotFound(SessionNotFoundException ex, Model model) {
        log.error("Session not found: {}", ex.getMessage());
        model.addAttribute("error", "Session expired or not found. Please start a new session.");
        return "error";
    }
    
    @ExceptionHandler(InvalidOtpException.class)
    public String handleInvalidOtp(InvalidOtpException ex, RedirectAttributes redirectAttributes) {
        log.error("Invalid OTP: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("error", ex.getMessage());
        return "redirect:/auth/verify";
    }
    
    @ExceptionHandler(ScanLimitExceededException.class)
    public String handleScanLimitExceeded(ScanLimitExceededException ex, Model model) {
        log.error("Scan limit exceeded: {}", ex.getMessage());
        model.addAttribute("error", ex.getMessage());
        return "error";
    }
    
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGenericException(Exception ex, Model model) {
        // Ignore harmless favicon and static resource errors
        if (ex instanceof org.springframework.web.servlet.resource.NoResourceFoundException) {
            String resourcePath = ex.getMessage();
            if (resourcePath != null && (resourcePath.contains("favicon.ico") || 
                                       resourcePath.contains("/static/") || 
                                       resourcePath.contains("/css/") || 
                                       resourcePath.contains("/js/") || 
                                       resourcePath.contains("/images/"))) {
                log.debug("Ignoring static resource error: {}", resourcePath);
                // Return a simple 404 response instead of error page
                return "redirect:/";
            }
        }
        
        log.error("Unexpected error occurred", ex);
        model.addAttribute("error", "An unexpected error occurred. Please try again.");
        return "error";
    }
    
    @ExceptionHandler(NoResourceFoundException.class)
    public void handleStaticResourceNotFound(NoResourceFoundException ex, 
                                           HttpServletResponse response) throws IOException {
        String resourcePath = ex.getMessage();
        
        // Log only non-favicon errors for debugging
        if (resourcePath != null && !resourcePath.contains("favicon.ico")) {
            log.debug("Static resource not found: {}", resourcePath);
        }
        
        // Send 404 without redirecting to error page
        response.sendError(HttpStatus.NOT_FOUND.value());
    }
}