package com.somdiproy.smartcodereview.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Analysis Result entity for DynamoDB storage
 */
@DynamoDbBean
public class AnalysisResult {
    
    private String analysisId;
    private String sessionId;
    private Integer scanNumber;
    private String status;
    private String repository;
    private String branch;
    private String branchSHA;
    private Long startedAt;
    private Long completedAt;
    private Long processingTimeMs;
    private Integer filesSubmitted;
    private Integer filesAnalyzed;
    private Integer filesSkipped;
    private Map<String, Integer> skipReasons = new HashMap<>();
    private Summary summary;
    private Scores scores;
    private TokenUsage tokenUsage;
    private Costs costs;
    private Long expiresAt;
    private Long ttl;
    
    // Constructors
    public AnalysisResult() {}
    
    // Getters and Setters
    @DynamoDbPartitionKey
    public String getAnalysisId() {
        return analysisId;
    }
    
    public void setAnalysisId(String analysisId) {
        this.analysisId = analysisId;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = {"SessionIndex"})
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public Integer getScanNumber() {
        return scanNumber;
    }
    
    public void setScanNumber(Integer scanNumber) {
        this.scanNumber = scanNumber;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getRepository() {
        return repository;
    }
    
    public void setRepository(String repository) {
        this.repository = repository;
    }
    
    public String getBranch() {
        return branch;
    }
    
    public void setBranch(String branch) {
        this.branch = branch;
    }
    
    public String getBranchSHA() {
        return branchSHA;
    }
    
    public void setBranchSHA(String branchSHA) {
        this.branchSHA = branchSHA;
    }
    
    public Long getStartedAt() {
        return startedAt;
    }
    
    public void setStartedAt(Long startedAt) {
        this.startedAt = startedAt;
    }
    
    public Long getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(Long completedAt) {
        this.completedAt = completedAt;
    }
    
    public Long getProcessingTimeMs() {
        return processingTimeMs;
    }
    
    public void setProcessingTimeMs(Long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }
    
    public Integer getFilesSubmitted() {
        return filesSubmitted;
    }
    
    public void setFilesSubmitted(Integer filesSubmitted) {
        this.filesSubmitted = filesSubmitted;
    }
    
    public Integer getFilesAnalyzed() {
        return filesAnalyzed;
    }
    
    public void setFilesAnalyzed(Integer filesAnalyzed) {
        this.filesAnalyzed = filesAnalyzed;
    }
    
    public Integer getFilesSkipped() {
        return filesSkipped;
    }
    
    public void setFilesSkipped(Integer filesSkipped) {
        this.filesSkipped = filesSkipped;
    }
    
    public Map<String, Integer> getSkipReasons() {
        return skipReasons;
    }
    
    public void setSkipReasons(Map<String, Integer> skipReasons) {
        this.skipReasons = skipReasons;
    }
    
    public Summary getSummary() {
        return summary;
    }
    
    public void setSummary(Summary summary) {
        this.summary = summary;
    }
    
    public Scores getScores() {
        return scores;
    }
    
    public void setScores(Scores scores) {
        this.scores = scores;
    }
    
    public TokenUsage getTokenUsage() {
        return tokenUsage;
    }
    
    public void setTokenUsage(TokenUsage tokenUsage) {
        this.tokenUsage = tokenUsage;
    }
    
    public Costs getCosts() {
        return costs;
    }
    
    public void setCosts(Costs costs) {
        this.costs = costs;
    }
    
    public Long getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public Long getTtl() {
        return ttl;
    }
    
    public void setTtl(Long ttl) {
        this.ttl = ttl;
    }
    
    // Static builder method
    public static AnalysisResultBuilder builder() {
        return new AnalysisResultBuilder();
    }
    
    // Builder class
    public static class AnalysisResultBuilder {
        private AnalysisResult result = new AnalysisResult();
        
        public AnalysisResultBuilder analysisId(String analysisId) {
            result.setAnalysisId(analysisId);
            return this;
        }
        
        public AnalysisResultBuilder sessionId(String sessionId) {
            result.setSessionId(sessionId);
            return this;
        }
        
        public AnalysisResultBuilder scanNumber(Integer scanNumber) {
            result.setScanNumber(scanNumber);
            return this;
        }
        
        public AnalysisResultBuilder status(String status) {
            result.setStatus(status);
            return this;
        }
        
        public AnalysisResultBuilder repository(String repository) {
            result.setRepository(repository);
            return this;
        }
        
        public AnalysisResultBuilder branch(String branch) {
            result.setBranch(branch);
            return this;
        }
        
        public AnalysisResultBuilder branchSHA(String branchSHA) {
            result.setBranchSHA(branchSHA);
            return this;
        }
        
        public AnalysisResultBuilder startedAt(Long startedAt) {
            result.setStartedAt(startedAt);
            return this;
        }
        
        public AnalysisResultBuilder completedAt(Long completedAt) {
            result.setCompletedAt(completedAt);
            return this;
        }
        
        public AnalysisResultBuilder processingTimeMs(Long processingTimeMs) {
            result.setProcessingTimeMs(processingTimeMs);
            return this;
        }
        
        public AnalysisResultBuilder filesSubmitted(Integer filesSubmitted) {
            result.setFilesSubmitted(filesSubmitted);
            return this;
        }
        
        public AnalysisResultBuilder filesAnalyzed(Integer filesAnalyzed) {
            result.setFilesAnalyzed(filesAnalyzed);
            return this;
        }
        
        public AnalysisResultBuilder filesSkipped(Integer filesSkipped) {
            result.setFilesSkipped(filesSkipped);
            return this;
        }
        
        public AnalysisResultBuilder skipReasons(Map<String, Integer> skipReasons) {
            result.setSkipReasons(skipReasons);
            return this;
        }
        
        public AnalysisResultBuilder summary(Summary summary) {
            result.setSummary(summary);
            return this;
        }
        
        public AnalysisResultBuilder scores(Scores scores) {
            result.setScores(scores);
            return this;
        }
        
        public AnalysisResultBuilder tokenUsage(TokenUsage tokenUsage) {
            result.setTokenUsage(tokenUsage);
            return this;
        }
        
        public AnalysisResultBuilder costs(Costs costs) {
            result.setCosts(costs);
            return this;
        }
        
        public AnalysisResultBuilder expiresAt(Long expiresAt) {
            result.setExpiresAt(expiresAt);
            return this;
        }
        
        public AnalysisResultBuilder ttl(Long ttl) {
            result.setTtl(ttl);
            return this;
        }
        
        public AnalysisResult build() {
            return result;
        }
    }
    
    /**
     * Nested classes for structured data
     */
    @DynamoDbBean
    public static class Summary {
        private Integer totalIssues;
        private Map<String, Integer> bySeverity;
        private Map<String, Integer> byCategory;
        private Map<String, Integer> byType;
        
        // Default constructor
        public Summary() {
            this.bySeverity = new HashMap<>();
            this.byCategory = new HashMap<>();
            this.byType = new HashMap<>();
        }
        
        // Getters and Setters
        public Integer getTotalIssues() {
            return totalIssues;
        }
        
        public void setTotalIssues(Integer totalIssues) {
            this.totalIssues = totalIssues;
        }
        
        public Map<String, Integer> getBySeverity() {
            return bySeverity;
        }
        
        public void setBySeverity(Map<String, Integer> bySeverity) {
            this.bySeverity = bySeverity;
        }
        
        public Map<String, Integer> getByCategory() {
            return byCategory;
        }
        
        public void setByCategory(Map<String, Integer> byCategory) {
            this.byCategory = byCategory;
        }
        
        public Map<String, Integer> getByType() {
            return byType;
        }
        
        public void setByType(Map<String, Integer> byType) {
            this.byType = byType;
        }
    }
    
    @DynamoDbBean
    public static class Scores {
        private Double security;
        private Double performance;
        private Double quality;
        private Double overall;
        
        // Default constructor
        public Scores() {}
        
        // Getters and Setters
        public Double getSecurity() {
            return security;
        }
        
        public void setSecurity(Double security) {
            this.security = security;
        }
        
        public Double getPerformance() {
            return performance;
        }
        
        public void setPerformance(Double performance) {
            this.performance = performance;
        }
        
        public Double getQuality() {
            return quality;
        }
        
        public void setQuality(Double quality) {
            this.quality = quality;
        }
        
        public Double getOverall() {
            return overall;
        }
        
        public void setOverall(Double overall) {
            this.overall = overall;
        }
    }
    
    @DynamoDbBean
    public static class TokenUsage {
        private Integer screeningTokens;
        private Integer detectionTokens;
        private Integer suggestionTokens;
        private Integer totalTokens;
        
        // Default constructor
        public TokenUsage() {}
        
        // Getters and Setters
        public Integer getScreeningTokens() {
            return screeningTokens;
        }
        
        public void setScreeningTokens(Integer screeningTokens) {
            this.screeningTokens = screeningTokens;
        }
        
        public Integer getDetectionTokens() {
            return detectionTokens;
        }
        
        public void setDetectionTokens(Integer detectionTokens) {
            this.detectionTokens = detectionTokens;
        }
        
        public Integer getSuggestionTokens() {
            return suggestionTokens;
        }
        
        public void setSuggestionTokens(Integer suggestionTokens) {
            this.suggestionTokens = suggestionTokens;
        }
        
        public Integer getTotalTokens() {
            return totalTokens;
        }
        
        public void setTotalTokens(Integer totalTokens) {
            this.totalTokens = totalTokens;
        }
    }
    
    @DynamoDbBean
    public static class Costs {
        private Double screeningCost;
        private Double detectionCost;
        private Double suggestionCost;
        private Double totalCost;
        
        // Default constructor
        public Costs() {}
        
        // Getters and Setters
        public Double getScreeningCost() {
            return screeningCost;
        }
        
        public void setScreeningCost(Double screeningCost) {
            this.screeningCost = screeningCost;
        }
        
        public Double getDetectionCost() {
            return detectionCost;
        }
        
        public void setDetectionCost(Double detectionCost) {
            this.detectionCost = detectionCost;
        }
        
        public Double getSuggestionCost() {
            return suggestionCost;
        }
        
        public void setSuggestionCost(Double suggestionCost) {
            this.suggestionCost = suggestionCost;
        }
        
        public Double getTotalCost() {
            return totalCost;
        }
        
        public void setTotalCost(Double totalCost) {
            this.totalCost = totalCost;
        }
    }
}