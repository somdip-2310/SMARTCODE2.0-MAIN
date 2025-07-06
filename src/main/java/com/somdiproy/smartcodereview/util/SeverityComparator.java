package com.somdiproy.smartcodereview.util;

import com.somdiproy.smartcodereview.model.Issue;
import java.util.Comparator;
import java.util.Map;

/**
 * Utility class for consistent severity-based sorting across the application
 */
public class SeverityComparator {
    
    private static final Map<String, Integer> SEVERITY_PRIORITY = Map.of(
        "CRITICAL", 4,
        "HIGH", 3,
        "MEDIUM", 2,
        "LOW", 1
    );
    
    /**
     * Get priority value for a severity level
     */
    public static int getSeverityPriority(String severity) {
        if (severity == null) return 0;
        return SEVERITY_PRIORITY.getOrDefault(severity.toUpperCase(), 0);
    }
    
    /**
     * Comparator for sorting issues by severity (highest first)
     */
    public static final Comparator<Issue> BY_SEVERITY_DESC = (issue1, issue2) -> {
        if (issue1 == null && issue2 == null) return 0;
        if (issue1 == null) return 1;
        if (issue2 == null) return -1;
        
        // Primary sort by severity
        int severityCompare = Integer.compare(
            getSeverityPriority(issue2.getSeverity()), 
            getSeverityPriority(issue1.getSeverity())
        );
        
        if (severityCompare != 0) return severityCompare;
        
        // Secondary sort by category (security > performance > quality)
        int categoryCompare = compareCategoriesPriority(issue1.getCategory(), issue2.getCategory());
        if (categoryCompare != 0) return categoryCompare;
        
        // Tertiary sort by type
        String type1 = issue1.getType() != null ? issue1.getType() : "";
        String type2 = issue2.getType() != null ? issue2.getType() : "";
        int typeCompare = type1.compareTo(type2);
        
        if (typeCompare != 0) return typeCompare;
        
        // Finally sort by file name
        String file1 = issue1.getFile() != null ? issue1.getFile() : "";
        String file2 = issue2.getFile() != null ? issue2.getFile() : "";
        
        return file1.compareTo(file2);
    };
    
    /**
     * Comparator for sorting issues by severity and CVE score
     */
    public static final Comparator<Issue> BY_SEVERITY_AND_CVE = (issue1, issue2) -> {
        // First compare by severity
        int severityCompare = BY_SEVERITY_DESC.compare(issue1, issue2);
        if (severityCompare != 0) return severityCompare;
        
        // Then by CVE score if available
        Double cve1 = issue1.getCveScore();
        Double cve2 = issue2.getCveScore();
        
        if (cve1 != null && cve2 != null) {
            return Double.compare(cve2, cve1); // Higher score first
        } else if (cve1 != null) {
            return -1; // Issue with CVE score comes first
        } else if (cve2 != null) {
            return 1;
        }
        
        return 0;
    };
    
    private static int compareCategoriesPriority(String cat1, String cat2) {
        if (cat1 == null && cat2 == null) return 0;
        if (cat1 == null) return 1;
        if (cat2 == null) return -1;
        
        Map<String, Integer> categoryPriority = Map.of(
            "security", 3,
            "performance", 2,
            "quality", 1
        );
        
        int priority1 = categoryPriority.getOrDefault(cat1.toLowerCase(), 0);
        int priority2 = categoryPriority.getOrDefault(cat2.toLowerCase(), 0);
        
        return Integer.compare(priority2, priority1); // Higher priority first
    }
}