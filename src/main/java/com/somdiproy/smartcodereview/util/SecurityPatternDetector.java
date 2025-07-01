package com.somdiproy.smartcodereview.util;

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
    
    public static class SecurityPattern {
        private List<String> patterns;
        private String severity;
        private String cweId;
        private float cvssScore;
        
        // Constructors
        public SecurityPattern() {}
        
        private SecurityPattern(List<String> patterns, String severity, String cweId, float cvssScore) {
            this.patterns = patterns;
            this.severity = severity;
            this.cweId = cweId;
            this.cvssScore = cvssScore;
        }
        
        // Getters
        public List<String> getPatterns() { return patterns; }
        public String getSeverity() { return severity; }
        public String getCweId() { return cweId; }
        public float getCvssScore() { return cvssScore; }
        
        // Builder
        public static SecurityPatternBuilder builder() {
            return new SecurityPatternBuilder();
        }
        
        public static class SecurityPatternBuilder {
            private List<String> patterns;
            private String severity;
            private String cweId;
            private float cvssScore;
            
            public SecurityPatternBuilder patterns(List<String> patterns) {
                this.patterns = patterns;
                return this;
            }
            
            public SecurityPatternBuilder severity(String severity) {
                this.severity = severity;
                return this;
            }
            
            public SecurityPatternBuilder cweId(String cweId) {
                this.cweId = cweId;
                return this;
            }
            
            public SecurityPatternBuilder cvssScore(float cvssScore) {
                this.cvssScore = cvssScore;
                return this;
            }
            
            public SecurityPattern build() {
                return new SecurityPattern(patterns, severity, cweId, cvssScore);
            }
        }
    }
    
    public static class SecurityIssue {
        private String type;
        private String severity;
        private String cweId;
        private float cvssScore;
        private String filename;
        private int lineNumber;
        private String code;
        
        // Constructors
        public SecurityIssue() {}
        
        private SecurityIssue(String type, String severity, String cweId, float cvssScore, 
                             String filename, int lineNumber, String code) {
            this.type = type;
            this.severity = severity;
            this.cweId = cweId;
            this.cvssScore = cvssScore;
            this.filename = filename;
            this.lineNumber = lineNumber;
            this.code = code;
        }
        
        // Getters
        public String getType() { return type; }
        public String getSeverity() { return severity; }
        public String getCweId() { return cweId; }
        public float getCvssScore() { return cvssScore; }
        public String getFilename() { return filename; }
        public int getLineNumber() { return lineNumber; }
        public String getCode() { return code; }
        
        // Builder
        public static SecurityIssueBuilder builder() {
            return new SecurityIssueBuilder();
        }
        
        public static class SecurityIssueBuilder {
            private String type;
            private String severity;
            private String cweId;
            private float cvssScore;
            private String filename;
            private int lineNumber;
            private String code;
            
            public SecurityIssueBuilder type(String type) {
                this.type = type;
                return this;
            }
            
            public SecurityIssueBuilder severity(String severity) {
                this.severity = severity;
                return this;
            }
            
            public SecurityIssueBuilder cweId(String cweId) {
                this.cweId = cweId;
                return this;
            }
            
            public SecurityIssueBuilder cvssScore(float cvssScore) {
                this.cvssScore = cvssScore;
                return this;
            }
            
            public SecurityIssueBuilder filename(String filename) {
                this.filename = filename;
                return this;
            }
            
            public SecurityIssueBuilder lineNumber(int lineNumber) {
                this.lineNumber = lineNumber;
                return this;
            }
            
            public SecurityIssueBuilder code(String code) {
                this.code = code;
                return this;
            }
            
            public SecurityIssue build() {
                return new SecurityIssue(type, severity, cweId, cvssScore, filename, lineNumber, code);
            }
        }
    }
}