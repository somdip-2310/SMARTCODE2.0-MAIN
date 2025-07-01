package com.somdiproy.smartcodereview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ScreeningLambdaResponse {
    private String status;
    private String analysisId;
    private String sessionId;
    private List<ScreenedFile> files;
    private Summary summary;
    private TokenUsage tokenUsage;
    
    @Data
    public static class ScreenedFile {
        private String path;
        private String name;
        private String language;
        private Float confidence;
        private String complexity;
        private Long size;
    }
    
    @Data
    public static class Summary {
        private Integer inputFiles;
        private Integer validFiles;
        private Integer skippedFiles;
        private Map<String, Integer> languageDistribution;
    }
    
    @Data
    public static class TokenUsage {
        private Integer totalTokens;
        private Double estimatedCost;
    }
}