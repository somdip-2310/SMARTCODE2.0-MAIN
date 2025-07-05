package com.somdiproy.smartcodereview.dto;

import java.util.List;
import java.util.Map;

public class AnalysisStatusResponse {
    private String analysisId;
    private String status;
    private Progress progress;
    private Integer filesProcessed;
    private Integer totalFiles;
    private Integer issuesFound;
    private Integer estimatedTimeRemaining;
    private String error;
    private Integer scanNumber;
    private List<Map<String, String>> preview;
    
    // Constructors
    public AnalysisStatusResponse() {}
    
    // Getters and Setters
    public String getAnalysisId() {
        return analysisId;
    }
    
    public void setAnalysisId(String analysisId) {
        this.analysisId = analysisId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Progress getProgress() {
        return progress;
    }
    
    public void setProgress(Progress progress) {
        this.progress = progress;
    }
    
    public Integer getFilesProcessed() {
        return filesProcessed;
    }
    
    public void setFilesProcessed(Integer filesProcessed) {
        this.filesProcessed = filesProcessed;
    }
    
    public Integer getTotalFiles() {
        return totalFiles;
    }
    
    public void setTotalFiles(Integer totalFiles) {
        this.totalFiles = totalFiles;
    }
    
    public Integer getIssuesFound() {
        return issuesFound;
    }
    
    public void setIssuesFound(Integer issuesFound) {
        this.issuesFound = issuesFound;
    }
    
    public Integer getEstimatedTimeRemaining() {
        return estimatedTimeRemaining;
    }
    
    public void setEstimatedTimeRemaining(Integer estimatedTimeRemaining) {
        this.estimatedTimeRemaining = estimatedTimeRemaining;
    }
    
    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
    
    public Integer getScanNumber() {
		return scanNumber;
	}

	public void setScanNumber(Integer scanNumber) {
		this.scanNumber = scanNumber;
	}

	public List<Map<String, String>> getPreview() {
		return preview;
	}

	public void setPreview(List<Map<String, String>> preview) {
		this.preview = preview;
	}

	// Builder pattern
    public static AnalysisStatusResponseBuilder builder() {
        return new AnalysisStatusResponseBuilder();
    }
    
    public static class AnalysisStatusResponseBuilder {
        private AnalysisStatusResponse response = new AnalysisStatusResponse();
        
        public AnalysisStatusResponseBuilder analysisId(String analysisId) {
            response.setAnalysisId(analysisId);
            return this;
        }
        
        public AnalysisStatusResponseBuilder status(String status) {
            response.setStatus(status);
            return this;
        }
        
        public AnalysisStatusResponseBuilder progress(Progress progress) {
            response.setProgress(progress);
            return this;
        }
        
        public AnalysisStatusResponseBuilder filesProcessed(Integer filesProcessed) {
            response.setFilesProcessed(filesProcessed);
            return this;
        }
        
        public AnalysisStatusResponseBuilder totalFiles(Integer totalFiles) {
            response.setTotalFiles(totalFiles);
            return this;
        }
        
        public AnalysisStatusResponseBuilder issuesFound(Integer issuesFound) {
            response.setIssuesFound(issuesFound);
            return this;
        }
        
        public AnalysisStatusResponseBuilder estimatedTimeRemaining(Integer estimatedTimeRemaining) {
            response.setEstimatedTimeRemaining(estimatedTimeRemaining);
            return this;
        }
        
        public AnalysisStatusResponseBuilder error(String error) {
            response.setError(error);
            return this;
        }
        
        public AnalysisStatusResponseBuilder scanNumber(Integer scanNumber) {
            response.setScanNumber(scanNumber);
            return this;
        }
        
        public AnalysisStatusResponseBuilder preview(List<Map<String, String>> preview) {
            response.setPreview(preview);
            return this;
        }
        
        public AnalysisStatusResponse build() {
            return response;
        }
    }
    
    // Nested Progress class
    public static class Progress {
        private String screening;
        private String detection;
        private String suggestions;
        private Integer overall;
        
        // Constructors
        public Progress() {}
        
        // Getters and Setters
        public String getScreening() {
            return screening;
        }
        
        public void setScreening(String screening) {
            this.screening = screening;
        }
        
        public String getDetection() {
            return detection;
        }
        
        public void setDetection(String detection) {
            this.detection = detection;
        }
        
        public String getSuggestions() {
            return suggestions;
        }
        
        public void setSuggestions(String suggestions) {
            this.suggestions = suggestions;
        }
        
        public Integer getOverall() {
            return overall;
        }
        
        public void setOverall(Integer overall) {
            this.overall = overall;
        }
        
        // Builder pattern for Progress
        public static ProgressBuilder builder() {
            return new ProgressBuilder();
        }
        
        public static class ProgressBuilder {
            private Progress progress = new Progress();
            
            public ProgressBuilder screening(String screening) {
                progress.setScreening(screening);
                return this;
            }
            
            public ProgressBuilder detection(String detection) {
                progress.setDetection(detection);
                return this;
            }
            
            public ProgressBuilder suggestions(String suggestions) {
                progress.setSuggestions(suggestions);
                return this;
            }
            
            public ProgressBuilder overall(Integer overall) {
                progress.setOverall(overall);
                return this;
            }
            
            public Progress build() {
                return progress;
            }
        }
    }
}