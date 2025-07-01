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

public class Analysis {
    private String analysisId;
    private String sessionId;
    private String repository;
    private String branch;
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