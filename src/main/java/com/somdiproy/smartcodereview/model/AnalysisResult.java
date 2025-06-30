package com.somdiproy.smartcodereview.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Analysis Result entity for DynamoDB storage
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    
    @Builder.Default
    private Map<String, Integer> skipReasons = new HashMap<>();
    
    private Summary summary;
    private Scores scores;
    private TokenUsage tokenUsage;
    private Costs costs;
    private Long expiresAt;
    private Long ttl;
    
    @DynamoDbPartitionKey
    public String getAnalysisId() {
        return analysisId;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = {"SessionIndex"})
    public String getSessionId() {
        return sessionId;
    }
    
    /**
     * Nested classes for structured data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @DynamoDbBean
    public static class Summary {
        private Integer totalIssues;
        @Builder.Default
        private Map<String, Integer> byType = new HashMap<>();
        @Builder.Default
        private Map<String, Integer> bySeverity = new HashMap<>();
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @DynamoDbBean
    public static class Scores {
        private Double security;
        private Double performance;
        private Double quality;
        private Double overall;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @DynamoDbBean
    public static class TokenUsage {
        private Integer screening;
        private Integer detection;
        private Integer suggestions;
        private Integer total;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @DynamoDbBean
    public static class Costs {
        private Double bedrock;
        private Double infrastructure;
        private Double total;
    }
}