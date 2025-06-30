package com.somdiproy.smartcodereview.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TokenOptimizationService {
    
    public String optimizeForScreening(String code) {
        // Extract only essential elements for screening
        if (code == null || code.isEmpty()) return "";
        
        // Remove comments, extra whitespace, etc.
        String optimized = code.replaceAll("/\\*.*?\\*/", "")
                              .replaceAll("//.*", "")
                              .replaceAll("\\s+", " ")
                              .trim();
        
        // Limit to first 1000 characters
        return optimized.length() > 1000 ? optimized.substring(0, 1000) : optimized;
    }
    
    public String optimizeForDetection(String code) {
        // Keep structure but remove redundant parts
        if (code == null || code.isEmpty()) return "";
        
        // Focus on executable code
        String optimized = code.replaceAll("/\\*.*?\\*/", "")
                              .replaceAll("//.*", "")
                              .trim();
        
        // Limit to first 5000 characters
        return optimized.length() > 5000 ? optimized.substring(0, 5000) : optimized;
    }
    
    public String optimizeForSuggestions(String issueContext) {
        // Provide focused context for suggestion generation
        if (issueContext == null || issueContext.isEmpty()) return "";
        
        // Limit to relevant code snippet
        return issueContext.length() > 500 ? issueContext.substring(0, 500) : issueContext;
    }
}