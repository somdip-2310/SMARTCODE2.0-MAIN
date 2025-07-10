package com.somdiproy.smartcodereview.dto;

import com.somdiproy.smartcodereview.model.Issue;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {
    private String analysisId;
    private String repository;
    private String branch;
    private Date date;
    private Integer filesAnalyzed;
    private Integer totalIssues;
    private Integer scanNumber;
    private Integer criticalCount;
    private Integer highCount;
    private Integer mediumCount;
    private Integer lowCount;
    private Long processingTime;
    private List<Issue> issues;
    private Map<String, Double> scores;
    
    private Integer securityCount;
    private Integer performanceCount; 
    private Integer qualityCount;
    
    // Default constructor
    //public ReportResponse() {}
    
    // Builder pattern
    public static ReportResponseBuilder builder() {
        return new ReportResponseBuilder();
    }
    
    public static class ReportResponseBuilder {
        private ReportResponse response = new ReportResponse();
        
        public ReportResponseBuilder analysisId(String analysisId) {
            response.setAnalysisId(analysisId);
            return this;
        }
        
        public ReportResponseBuilder repository(String repository) {
            response.setRepository(repository);
            return this;
        }
        
        public ReportResponseBuilder branch(String branch) {
            response.setBranch(branch);
            return this;
        }
        
        public ReportResponseBuilder date(Date date) {
            response.setDate(date);
            return this;
        }
        
        public ReportResponseBuilder filesAnalyzed(Integer filesAnalyzed) {
            response.setFilesAnalyzed(filesAnalyzed);
            return this;
        }
        
        public ReportResponseBuilder totalIssues(Integer totalIssues) {
            response.setTotalIssues(totalIssues);
            return this;
        }
        
        public ReportResponseBuilder scanNumber(Integer scanNumber) {
            response.setScanNumber(scanNumber);
            return this;
        }
        
        public ReportResponseBuilder criticalCount(Integer criticalCount) {
            response.setCriticalCount(criticalCount);
            return this;
        }
        
        public ReportResponseBuilder highCount(Integer highCount) {
            response.setHighCount(highCount);
            return this;
        }
        
        public ReportResponseBuilder mediumCount(Integer mediumCount) {
            response.setMediumCount(mediumCount);
            return this;
        }
        
        public ReportResponseBuilder lowCount(Integer lowCount) {
            response.setLowCount(lowCount);
            return this;
        }
        
        public ReportResponseBuilder processingTime(Long processingTime) {
            response.setProcessingTime(processingTime);
            return this;
        }
        
        public ReportResponseBuilder issues(List<Issue> issues) {
            response.setIssues(issues);
            return this;
        }
        
        public ReportResponseBuilder scores(Map<String, Double> scores) {
            response.setScores(scores);
            return this;
        }
        
        public ReportResponse build() {
            return response;
        }
    }
    
    // Getters and Setters
    public String getAnalysisId() {
        return analysisId;
    }
    
    public void setAnalysisId(String analysisId) {
        this.analysisId = analysisId;
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
    
    public Date getDate() {
        return date;
    }
    
    public void setDate(Date date) {
        this.date = date;
    }
    
    public Integer getFilesAnalyzed() {
        return filesAnalyzed;
    }
    
    public void setFilesAnalyzed(Integer filesAnalyzed) {
        this.filesAnalyzed = filesAnalyzed;
    }
    
    public Integer getTotalIssues() {
        return totalIssues;
    }
    
    public void setTotalIssues(Integer totalIssues) {
        this.totalIssues = totalIssues;
    }
    
    public Integer getScanNumber() {
        return scanNumber;
    }
    
    public void setScanNumber(Integer scanNumber) {
        this.scanNumber = scanNumber;
    }
    
    public Integer getCriticalCount() {
        return criticalCount;
    }
    
    public void setCriticalCount(Integer criticalCount) {
        this.criticalCount = criticalCount;
    }
    
    public Integer getHighCount() {
        return highCount;
    }
    
    public void setHighCount(Integer highCount) {
        this.highCount = highCount;
    }
    
    public Integer getMediumCount() {
        return mediumCount;
    }
    
    public void setMediumCount(Integer mediumCount) {
        this.mediumCount = mediumCount;
    }
    
    public Integer getLowCount() {
        return lowCount;
    }
    
    public void setLowCount(Integer lowCount) {
        this.lowCount = lowCount;
    }
    
    public Long getProcessingTime() {
        return processingTime;
    }
    
    public void setProcessingTime(Long processingTime) {
        this.processingTime = processingTime;
    }
    
    public List<Issue> getIssues() {
        return issues;
    }
    
    public void setIssues(List<Issue> issues) {
        this.issues = issues;
    }
    
    public Map<String, Double> getScores() {
        return scores;
    }
    
    public void setScores(Map<String, Double> scores) {
        this.scores = scores;
    }
 // Add these getter/setter methods
    public Integer getSecurityCount() {
        return securityCount;
    }

    public void setSecurityCount(Integer securityCount) {
        this.securityCount = securityCount;
    }

    public Integer getPerformanceCount() {
        return performanceCount;
    }

    public void setPerformanceCount(Integer performanceCount) {
        this.performanceCount = performanceCount;
    }

    public Integer getQualityCount() {
        return qualityCount;
    }

    public void setQualityCount(Integer qualityCount) {
        this.qualityCount = qualityCount;
    }
}