package com.somdiproy.smartcodereview.dto;

import com.somdiproy.smartcodereview.model.Issue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Map;


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
    
    
}