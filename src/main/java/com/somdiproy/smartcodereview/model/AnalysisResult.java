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
    
    // Static builder method for backward compatibility
    public static AnalysisResultBuilder builder() {
        return new AnalysisResultBuilder();
    }
    
    // Builder class
    public static class AnalysisResultBuilder {
        private AnalysisResult analysisResult = new AnalysisResult();
        
        public AnalysisResultBuilder analysisId(String analysisId) {
            analysisResult.setAnalysisId(analysisId);
            return this;
        }
        
        public AnalysisResultBuilder sessionId(String sessionId) {
            analysisResult.setSessionId(sessionId);
            return this;
        }
        
        public AnalysisResultBuilder repository(String repository) {
            analysisResult.setRepository(repository);
            return this;
        }
        
        public AnalysisResultBuilder branch(String branch) {
            analysisResult.setBranch(branch);
            return this;
        }
        
        public AnalysisResultBuilder status(String status) {
            analysisResult.setStatus(status);
            return this;
        }
        
        public AnalysisResultBuilder startedAt(Long startedAt) {
            analysisResult.setStartedAt(startedAt);
            return this;
        }
        
        public AnalysisResultBuilder completedAt(Long completedAt) {
            analysisResult.setCompletedAt(completedAt);
            return this;
        }
        
        public AnalysisResultBuilder filesAnalyzed(Integer filesAnalyzed) {
            analysisResult.setFilesAnalyzed(filesAnalyzed);
            return this;
        }
        
        public AnalysisResultBuilder filesSkipped(Integer filesSkipped) {
            analysisResult.setFilesSkipped(filesSkipped);
            return this;
        }
        
        public AnalysisResultBuilder filesSubmitted(Integer filesSubmitted) {
            analysisResult.setFilesSubmitted(filesSubmitted);
            return this;
        }
        
        public AnalysisResult build() {
            return analysisResult;
        }
    }
    
    /**
     * Nested classes for structured data
     */
    @DynamoDbBean
    public static class Summary {
        private Integer totalIssues;
        private Map<String, Integer> byType = new HashMap<>();
        private Map<String, Integer> bySeverity = new HashMap<>();
        
        public Summary() {}
        
        public Integer getTotalIssues() {
            return totalIssues;
        }
        
        public void setTotalIssues(Integer totalIssues) {
            this.totalIssues = totalIssues;
        }
        
        public Map<String, Integer> getByType() {
            return byType;
        }
        
        public void setByType(Map<String, Integer> byType) {
            this.byType = byType;
        }
        
        public Map<String, Integer> getBySeverity() {
            return bySeverity;
        }
        
        public void setBySeverity(Map<String, Integer> bySeverity) {
            this.bySeverity = bySeverity;
        }
    }
    
    @DynamoDbBean
    public static class Scores {
        private Double security;
        private Double performance;
        private Double quality;
        private Double overall;
        
        public Scores() {}
        
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
        private Integer screening;
        private Integer detection;
        private Integer suggestions;
        private Integer total;
        
        public TokenUsage() {}
        
        public Integer getScreening() {
            return screening;
        }
        
        public void setScreening(Integer screening) {
            this.screening = screening;
        }
        
        public Integer getDetection() {
            return detection;
        }
        
        public void setDetection(Integer detection) {
            this.detection = detection;
        }
        
        public Integer getSuggestions() {
            return suggestions;
        }
        
        public void setSuggestions(Integer suggestions) {
            this.suggestions = suggestions;
        }
        
        public Integer getTotal() {
            return total;
        }
        
        public void setTotal(Integer total) {
            this.total = total;
        }
    }
    
    @DynamoDbBean
    public static class Costs {
        private Double bedrock;
        private Double infrastructure;
        private Double total;
        
        public Costs() {}
        
        public Double getBedrock() {
            return bedrock;
        }
        
        public void setBedrock(Double bedrock) {
            this.bedrock = bedrock;
        }
        
        public Double getInfrastructure() {
            return infrastructure;
        }
        
        public void setInfrastructure(Double infrastructure) {
            this.infrastructure = infrastructure;
        }
        
        public Double getTotal() {
            return total;
        }
        
        public void setTotal(Double total) {
            this.total = total;
        }
    }
}