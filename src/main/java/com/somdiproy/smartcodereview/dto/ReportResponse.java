package com.somdiproy.smartcodereview.dto;

import com.somdiproy.smartcodereview.model.Issue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@Builder
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
}