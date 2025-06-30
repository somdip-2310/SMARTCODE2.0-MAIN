package com.somdiproy.smartcodereview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisStatusResponse {
    private String analysisId;
    private String status;
    private Progress progress;
    private Integer filesProcessed;
    private Integer totalFiles;
    private Integer issuesFound;
    private Integer estimatedTimeRemaining;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Progress {
        private String screening;
        private String detection;
        private String suggestions;
        private Integer overall;
    }
}