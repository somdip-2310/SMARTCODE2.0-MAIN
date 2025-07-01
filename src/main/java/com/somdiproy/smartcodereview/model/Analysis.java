package com.somdiproy.smartcodereview.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Analysis model (for tracking in-progress analyses)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Analysis {
    private String analysisId;
    private String sessionId;
    private String repository;
    private String branch;
    private AnalysisStatus status;
    private Integer progress;
    private Long startedAt;
    private Long completedAt;
    private List<String> filesProcessed;
    private Integer totalFiles;
    private Integer issuesFound;
    
    @Builder.Default
    private List<String> stages = new ArrayList<>();
    
    public enum AnalysisStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    // Add getter methods
    public String getAnalysisId() {
        return analysisId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getRepository() {
        return repository;
    }

    public String getBranch() {
        return branch;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public Integer getProgress() {
        return progress;
    }

    public Long getStartedAt() {
        return startedAt;
    }

    public Long getCompletedAt() {
        return completedAt;
    }

    public List<String> getFilesProcessed() {
        return filesProcessed;
    }

    public Integer getTotalFiles() {
        return totalFiles;
    }

    public Integer getIssuesFound() {
        return issuesFound;
    }

    public List<String> getStages() {
        return stages;
    }
}