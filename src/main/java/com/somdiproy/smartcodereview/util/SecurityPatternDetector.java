package com.somdiproy.smartcodereview.util;

import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SecurityPatternDetector {
    
    private static final Map<String, SecurityPattern> PATTERNS = new HashMap<>();
    
    static {
        PATTERNS.put("sql_injection", SecurityPattern.builder()
                .patterns(Arrays.asList(
                    "query\\s*=\\s*[\"'].*\\+\\s*\\w+",
                    "execute\\([\"'].*\\%[sd].*[\"'].*\\%",
                    "SELECT.*WHERE.*\\+\\s*\\w+"
                ))
                .severity("CRITICAL")
                .cweId("CWE-89")
                .cvssScore(9.8f)
                .build());
    }
    
    public List<SecurityIssue> detectPatterns(String code, String filename) {
        List<SecurityIssue> issues = new ArrayList<>();
        
        for (Map.Entry<String, SecurityPattern> entry : PATTERNS.entrySet()) {
            SecurityPattern pattern = entry.getValue();
            for (String regex : pattern.getPatterns()) {
                Pattern p = Pattern.compile(regex, Pattern.MULTILINE);
                Matcher m = p.matcher(code);
                
                while (m.find()) {
                    issues.add(SecurityIssue.builder()
                            .type(entry.getKey())
                            .severity(pattern.getSeverity())
                            .cweId(pattern.getCweId())
                            .cvssScore(pattern.getCvssScore())
                            .filename(filename)
                            .lineNumber(getLineNumber(code, m.start()))
                            .code(m.group())
                            .build());
                }
            }
        }
        
        return issues;
    }
    
    private int getLineNumber(String code, int position) {
        String substring = code.substring(0, Math.min(position, code.length()));
        return substring.split("\n").length;
    }
    
    @Data
    @Builder
    public static class SecurityPattern {
        private List<String> patterns;
        private String severity;
        private String cweId;
        private float cvssScore;
    }
    
    @Data
    @Builder
    public static class SecurityIssue {
        private String type;
        private String severity;
        private String cweId;
        private float cvssScore;
        private String filename;
        private int lineNumber;
        private String code;
    }
}