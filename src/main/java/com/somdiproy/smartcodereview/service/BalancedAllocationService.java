package com.somdiproy.smartcodereview.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for balanced token allocation across Security, Performance, and Code Quality categories
 * Ensures CRITICAL/HIGH severity issues get priority while respecting budget constraints
 */
@Service
public class BalancedAllocationService {
    
    private static final Logger log = LoggerFactory.getLogger(BalancedAllocationService.class);
    
    // Token budget configuration
    private static final int TOTAL_TOKEN_BUDGET = 70000; // 75K - 5K buffer
    private static final int TOKENS_PER_SUGGESTION = 3500; // Conservative estimate for Nova Premier
    private static final int MAX_SUGGESTIONS_PER_SCAN = TOTAL_TOKEN_BUDGET / TOKENS_PER_SUGGESTION; // ~20
    
    // Category allocation percentages
    private static final double SECURITY_ALLOCATION = 0.50;    // 50% of tokens
    private static final double PERFORMANCE_ALLOCATION = 0.30; // 30% of tokens  
    private static final double QUALITY_ALLOCATION = 0.20;    // 20% of tokens
    
    // Minimum guaranteed suggestions per category
    private static final int MIN_SECURITY_SUGGESTIONS = 3;
    private static final int MIN_PERFORMANCE_SUGGESTIONS = 2;
    private static final int MIN_QUALITY_SUGGESTIONS = 1;
    
    /**
     * Allocate issues for suggestions using balanced strategy
     */
    public AllocationResult allocateIssuesForSuggestions(List<Map<String, Object>> allIssues) {
        log.info("🎯 Starting balanced allocation for {} total issues", allIssues.size());
        
        // Categorize all issues by type and severity
        Map<String, List<Map<String, Object>>> categorizedIssues = categorizeIssues(allIssues);
        
        // Calculate base allocation per category
        Map<String, Integer> baseAllocation = calculateBaseAllocation();
        
        // Prioritize CRITICAL/HIGH within each category
        Map<String, List<Map<String, Object>>> prioritizedIssues = prioritizeWithinCategories(categorizedIssues);
        
        // Apply balanced selection with overflow redistribution
        AllocationResult result = performBalancedSelection(prioritizedIssues, baseAllocation);
        
        log.info("✅ Balanced allocation complete: Security={}, Performance={}, Quality={}, Total={}",
                result.getSecurityIssues().size(),
                result.getPerformanceIssues().size(), 
                result.getQualityIssues().size(),
                result.getTotalSelected());
        
        return result;
    }
    
    /**
     * Categorize issues by type (security, performance, quality)
     */
    private Map<String, List<Map<String, Object>>> categorizeIssues(List<Map<String, Object>> allIssues) {
        Map<String, List<Map<String, Object>>> categorized = new HashMap<>();
        categorized.put("security", new ArrayList<>());
        categorized.put("performance", new ArrayList<>());
        categorized.put("quality", new ArrayList<>());
        
        for (Map<String, Object> issue : allIssues) {
            String category = (String) issue.getOrDefault("category", "quality");
            String type = (String) issue.getOrDefault("type", "");
            
            // Enhanced categorization logic
            if ("security".equalsIgnoreCase(category) || isSecurityIssue(type)) {
                categorized.get("security").add(issue);
            } else if ("performance".equalsIgnoreCase(category) || isPerformanceIssue(type)) {
                categorized.get("performance").add(issue);
            } else {
                categorized.get("quality").add(issue);
            }
        }
        
        log.info("📊 Issue categorization: Security={}, Performance={}, Quality={}",
                categorized.get("security").size(),
                categorized.get("performance").size(),
                categorized.get("quality").size());
        
        return categorized;
    }
    
    /**
     * Calculate base token allocation per category
     */
    private Map<String, Integer> calculateBaseAllocation() {
        Map<String, Integer> allocation = new HashMap<>();
        
        // Calculate suggestions per category based on percentage allocation
        int securitySuggestions = Math.max(MIN_SECURITY_SUGGESTIONS, 
                (int) (MAX_SUGGESTIONS_PER_SCAN * SECURITY_ALLOCATION));
        int performanceSuggestions = Math.max(MIN_PERFORMANCE_SUGGESTIONS,
                (int) (MAX_SUGGESTIONS_PER_SCAN * PERFORMANCE_ALLOCATION));
        int qualitySuggestions = Math.max(MIN_QUALITY_SUGGESTIONS,
                (int) (MAX_SUGGESTIONS_PER_SCAN * QUALITY_ALLOCATION));
        
        // Adjust if total exceeds budget
        int totalAllocated = securitySuggestions + performanceSuggestions + qualitySuggestions;
        if (totalAllocated > MAX_SUGGESTIONS_PER_SCAN) {
            double scaleFactor = (double) MAX_SUGGESTIONS_PER_SCAN / totalAllocated;
            securitySuggestions = Math.max(MIN_SECURITY_SUGGESTIONS, (int) (securitySuggestions * scaleFactor));
            performanceSuggestions = Math.max(MIN_PERFORMANCE_SUGGESTIONS, (int) (performanceSuggestions * scaleFactor));
            qualitySuggestions = Math.max(MIN_QUALITY_SUGGESTIONS, (int) (qualitySuggestions * scaleFactor));
        }
        
        allocation.put("security", securitySuggestions);
        allocation.put("performance", performanceSuggestions);
        allocation.put("quality", qualitySuggestions);
        
        log.info("💰 Token allocation: Security={} suggestions, Performance={} suggestions, Quality={} suggestions",
                securitySuggestions, performanceSuggestions, qualitySuggestions);
        
        return allocation;
    }
    
    /**
     * Prioritize CRITICAL/HIGH severity issues within each category
     */
    private Map<String, List<Map<String, Object>>> prioritizeWithinCategories(
            Map<String, List<Map<String, Object>>> categorizedIssues) {
        
        Map<String, List<Map<String, Object>>> prioritized = new HashMap<>();
        
        for (Map.Entry<String, List<Map<String, Object>>> entry : categorizedIssues.entrySet()) {
            String category = entry.getKey();
            List<Map<String, Object>> issues = entry.getValue();
            
            // Sort by severity priority: CRITICAL > HIGH > MEDIUM > LOW
            List<Map<String, Object>> sortedIssues = issues.stream()
                    .sorted((a, b) -> {
                        int severityA = getSeverityPriority((String) a.getOrDefault("severity", "LOW"));
                        int severityB = getSeverityPriority((String) b.getOrDefault("severity", "LOW"));
                        
                        // Higher priority first
                        int severityCompare = Integer.compare(severityB, severityA);
                        if (severityCompare != 0) return severityCompare;
                        
                        // Secondary sort by CVE score for security issues
                        if ("security".equals(category)) {
                            double cveA = estimateCVEScore(a);
                            double cveB = estimateCVEScore(b);
                            return Double.compare(cveB, cveA);
                        }
                        
                        // Tertiary sort by complexity for performance/quality
                        return compareComplexity(a, b);
                    })
                    .collect(Collectors.toList());
            
            prioritized.put(category, sortedIssues);
            
            // Log priority distribution
            long criticalHigh = sortedIssues.stream()
                    .filter(issue -> {
                        String severity = (String) issue.getOrDefault("severity", "LOW");
                        return "CRITICAL".equalsIgnoreCase(severity) || "HIGH".equalsIgnoreCase(severity);
                    })
                    .count();
            
            log.info("🔥 {} category: {} CRITICAL/HIGH priority issues out of {} total",
                    category, criticalHigh, sortedIssues.size());
        }
        
        return prioritized;
    }
    
    /**
     * Perform balanced selection with overflow redistribution
     */
    private AllocationResult performBalancedSelection(
            Map<String, List<Map<String, Object>>> prioritizedIssues,
            Map<String, Integer> baseAllocation) {
        
        List<Map<String, Object>> selectedSecurity = new ArrayList<>();
        List<Map<String, Object>> selectedPerformance = new ArrayList<>();
        List<Map<String, Object>> selectedQuality = new ArrayList<>();
        
        // Phase 1: Select issues according to base allocation
        selectedSecurity.addAll(selectTopIssues(prioritizedIssues.get("security"), 
                baseAllocation.get("security")));
        selectedPerformance.addAll(selectTopIssues(prioritizedIssues.get("performance"), 
                baseAllocation.get("performance")));
        selectedQuality.addAll(selectTopIssues(prioritizedIssues.get("quality"), 
                baseAllocation.get("quality")));
        
        int currentTotal = selectedSecurity.size() + selectedPerformance.size() + selectedQuality.size();
        
        // Phase 2: Redistribute unused allocation to categories with remaining high-priority issues
        if (currentTotal < MAX_SUGGESTIONS_PER_SCAN) {
            int remainingBudget = MAX_SUGGESTIONS_PER_SCAN - currentTotal;
            
            // Check for remaining CRITICAL/HIGH issues in each category
            List<Map<String, Object>> remainingSecurity = getRemainingHighPriorityIssues(
                    prioritizedIssues.get("security"), selectedSecurity.size());
            List<Map<String, Object>> remainingPerformance = getRemainingHighPriorityIssues(
                    prioritizedIssues.get("performance"), selectedPerformance.size());
            List<Map<String, Object>> remainingQuality = getRemainingHighPriorityIssues(
                    prioritizedIssues.get("quality"), selectedQuality.size());
            
            // Redistribute based on remaining high-priority issues
            remainingBudget = redistributeTokens(remainingSecurity, selectedSecurity, remainingBudget);
            remainingBudget = redistributeTokens(remainingPerformance, selectedPerformance, remainingBudget);
            remainingBudget = redistributeTokens(remainingQuality, selectedQuality, remainingBudget);
            
            log.info("🔄 Redistributed {} suggestions to ensure high-priority coverage", 
                    MAX_SUGGESTIONS_PER_SCAN - currentTotal - remainingBudget);
        }
        
        return new AllocationResult(selectedSecurity, selectedPerformance, selectedQuality);
    }
    
    /**
     * Select top N issues from a prioritized list
     */
    private List<Map<String, Object>> selectTopIssues(List<Map<String, Object>> issues, int count) {
        return issues.stream()
                .limit(count)
                .collect(Collectors.toList());
    }
    
    /**
     * Get remaining high-priority issues after initial selection
     */
    private List<Map<String, Object>> getRemainingHighPriorityIssues(
            List<Map<String, Object>> allIssues, int alreadySelected) {
        
        return allIssues.stream()
                .skip(alreadySelected)
                .filter(issue -> {
                    String severity = (String) issue.getOrDefault("severity", "LOW");
                    return "CRITICAL".equalsIgnoreCase(severity) || "HIGH".equalsIgnoreCase(severity);
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Redistribute unused tokens to categories with remaining high-priority issues
     */
    private int redistributeTokens(List<Map<String, Object>> remainingIssues, 
                                   List<Map<String, Object>> selectedIssues, 
                                   int availableBudget) {
        
        int canAdd = Math.min(remainingIssues.size(), availableBudget);
        if (canAdd > 0) {
            selectedIssues.addAll(remainingIssues.subList(0, canAdd));
            return availableBudget - canAdd;
        }
        return availableBudget;
    }
    
    /**
     * Determine if an issue type is security-related
     */
    private boolean isSecurityIssue(String type) {
        return type != null && (
                type.toUpperCase().contains("SQL_INJECTION") ||
                type.toUpperCase().contains("XSS") ||
                type.toUpperCase().contains("SECURITY") ||
                type.toUpperCase().contains("VULNERABILITY") ||
                type.toUpperCase().contains("HARDCODED") ||
                type.toUpperCase().contains("CRYPTO") ||
                type.toUpperCase().contains("AUTH")
        );
    }
    
    /**
     * Determine if an issue type is performance-related
     */
    private boolean isPerformanceIssue(String type) {
        return type != null && (
                type.toUpperCase().contains("PERFORMANCE") ||
                type.toUpperCase().contains("COMPLEXITY") ||
                type.toUpperCase().contains("LOOP") ||
                type.toUpperCase().contains("MEMORY") ||
                type.toUpperCase().contains("INEFFICIENT") ||
                type.toUpperCase().contains("OPTIMIZATION")
        );
    }
    
    /**
     * Get severity priority (higher number = higher priority)
     */
    private int getSeverityPriority(String severity) {
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }
    
    /**
     * Estimate CVE score for security issues
     */
    private double estimateCVEScore(Map<String, Object> issue) {
        Object cveScore = issue.get("cveScore");
        if (cveScore instanceof Number) {
            return ((Number) cveScore).doubleValue();
        }
        
        // Estimate based on issue type and severity
        String severity = (String) issue.getOrDefault("severity", "LOW");
        String type = (String) issue.getOrDefault("type", "");
        
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> type.contains("SQL_INJECTION") ? 9.8 : 9.0;
            case "HIGH" -> type.contains("XSS") ? 8.5 : 7.5;
            case "MEDIUM" -> 5.0;
            default -> 2.0;
        };
    }
    
    /**
     * Compare complexity for performance and quality issues
     */
    private int compareComplexity(Map<String, Object> a, Map<String, Object> b) {
        // Simple heuristic: longer code or higher line numbers suggest more complexity
        int lineA = (Integer) a.getOrDefault("line", 0);
        int lineB = (Integer) b.getOrDefault("line", 0);
        return Integer.compare(lineB, lineA);
    }
    
    /**
     * Result class for allocation output
     */
    public static class AllocationResult {
        private final List<Map<String, Object>> securityIssues;
        private final List<Map<String, Object>> performanceIssues;
        private final List<Map<String, Object>> qualityIssues;
        
        public AllocationResult(List<Map<String, Object>> securityIssues,
                                List<Map<String, Object>> performanceIssues,
                                List<Map<String, Object>> qualityIssues) {
            this.securityIssues = new ArrayList<>(securityIssues);
            this.performanceIssues = new ArrayList<>(performanceIssues);
            this.qualityIssues = new ArrayList<>(qualityIssues);
        }
        
        public List<Map<String, Object>> getSecurityIssues() { return securityIssues; }
        public List<Map<String, Object>> getPerformanceIssues() { return performanceIssues; }
        public List<Map<String, Object>> getQualityIssues() { return qualityIssues; }
        
        public List<Map<String, Object>> getAllSelectedIssues() {
            List<Map<String, Object>> all = new ArrayList<>();
            all.addAll(securityIssues);
            all.addAll(performanceIssues);
            all.addAll(qualityIssues);
            return all;
        }
        
        public int getTotalSelected() {
            return securityIssues.size() + performanceIssues.size() + qualityIssues.size();
        }
        
        public Map<String, Integer> getCategoryCounts() {
            Map<String, Integer> counts = new HashMap<>();
            counts.put("security", securityIssues.size());
            counts.put("performance", performanceIssues.size());
            counts.put("quality", qualityIssues.size());
            return counts;
        }
    }
}