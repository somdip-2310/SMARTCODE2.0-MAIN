package com.somdiproy.smartcodereview.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Analysis model (for tracking in-progress analyses)
 */
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
    private List<String> stages = new ArrayList<>();
    
    public enum AnalysisStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
    
    // Constructors
    public Analysis() {
        this.stages = new ArrayList<>();
    }
    
    // Getters
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
    
    // Setters
    public void setAnalysisId(String analysisId) {
        this.analysisId = analysisId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public void setRepository(String repository) {
        this.repository = repository;
    }
    
    public void setBranch(String branch) {
        this.branch = branch;
    }
    
    public void setStatus(AnalysisStatus status) {
        this.status = status;
    }
    
    public void setProgress(Integer progress) {
        this.progress = progress;
    }
    
    public void setStartedAt(Long startedAt) {
        this.startedAt = startedAt;
    }
    
    public void setCompletedAt(Long completedAt) {
        this.completedAt = completedAt;
    }
    
    public void setFilesProcessed(List<String> filesProcessed) {
        this.filesProcessed = filesProcessed;
    }
    
    public void setTotalFiles(Integer totalFiles) {
        this.totalFiles = totalFiles;
    }
    
    public void setIssuesFound(Integer issuesFound) {
        this.issuesFound = issuesFound;
    }
    
    public void setStages(List<String> stages) {
        this.stages = stages;
    }
    
    // Builder pattern
    public static AnalysisBuilder builder() {
        return new AnalysisBuilder();
    }
    
    public static class AnalysisBuilder {
        private Analysis analysis = new Analysis();
        
        public AnalysisBuilder analysisId(String analysisId) {
            analysis.setAnalysisId(analysisId);
            return this;
        }
        
        public AnalysisBuilder sessionId(String sessionId) {
            analysis.setSessionId(sessionId);
            return this;
        }
        
        public AnalysisBuilder repository(String repository) {
            analysis.setRepository(repository);
            return this;
        }
        
        public AnalysisBuilder branch(String branch) {
            analysis.setBranch(branch);
            return this;
        }
        
        public AnalysisBuilder status(AnalysisStatus status) {
            analysis.setStatus(status);
            return this;
        }
        
        public AnalysisBuilder progress(Integer progress) {
            analysis.setProgress(progress);
            return this;
        }
        
        public AnalysisBuilder startedAt(Long startedAt) {
            analysis.setStartedAt(startedAt);
            return this;
        }
        
        public AnalysisBuilder completedAt(Long completedAt) {
            analysis.setCompletedAt(completedAt);
            return this;
        }
        
        public AnalysisBuilder filesProcessed(List<String> filesProcessed) {
            analysis.setFilesProcessed(filesProcessed);
            return this;
        }
        
        public AnalysisBuilder totalFiles(Integer totalFiles) {
            analysis.setTotalFiles(totalFiles);
            return this;
        }
        
        public AnalysisBuilder issuesFound(Integer issuesFound) {
            analysis.setIssuesFound(issuesFound);
            return this;
        }
        
        public AnalysisBuilder stages(List<String> stages) {
            analysis.setStages(stages);
            return this;
        }
        
        public Analysis build() {
            return analysis;
        }
    }
}