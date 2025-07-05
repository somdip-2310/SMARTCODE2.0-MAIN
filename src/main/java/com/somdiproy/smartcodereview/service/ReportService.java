package com.somdiproy.smartcodereview.service;

import com.somdiproy.smartcodereview.dto.ReportResponse;
import com.somdiproy.smartcodereview.model.AnalysisResult;
import com.somdiproy.smartcodereview.model.Issue;
import com.somdiproy.smartcodereview.repository.AnalysisRepository;
import com.somdiproy.smartcodereview.repository.IssueDetailsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {
    
    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    
    private final AnalysisRepository analysisRepository;
    private final IssueDetailsRepository issueDetailsRepository;
    
    private final CVEMappingService cveMappingService;

    @Autowired
    public ReportService(AnalysisRepository analysisRepository, 
                        IssueDetailsRepository issueDetailsRepository,
                        CVEMappingService cveMappingService) {
        this.analysisRepository = analysisRepository;
        this.issueDetailsRepository = issueDetailsRepository;
        this.cveMappingService = cveMappingService;
    }
    
   
    
	public ReportResponse getReport(String analysisId) {
		AnalysisResult analysis = analysisRepository.findById(analysisId)
				.orElseThrow(() -> new RuntimeException("Analysis not found"));

		List<Issue> issues = issueDetailsRepository.findByAnalysisId(analysisId);

		// Enrich issues with CVE data
		issues.forEach(issue -> {
			// Ensure description is populated
			if (issue.getDescription() == null || issue.getDescription().isEmpty()) {
				if (issue.getTitle() != null && !issue.getTitle().isEmpty()) {
					issue.setDescription(issue.getTitle());
				} else {
					issue.setDescription("Issue detected: " + issue.getType());
				}
			}
			
			if ("SECURITY".equalsIgnoreCase(issue.getCategory())
					&& (issue.getCveId() == null || issue.getCveId().isEmpty())) {
				String cveId = cveMappingService.getCVEId(issue.getType());
				if (cveId != null) {
					issue.setCveId(cveId);
					issue.setCveScore(cveMappingService.getCVEScore(cveId));
				}
			}
		});
        // Count issues by severity
        long criticalCount = issues.stream().filter(i -> "CRITICAL".equals(i.getSeverity())).count();
        long highCount = issues.stream().filter(i -> "HIGH".equals(i.getSeverity())).count();
        long mediumCount = issues.stream().filter(i -> "MEDIUM".equals(i.getSeverity())).count();
        long lowCount = issues.stream().filter(i -> "LOW".equals(i.getSeverity())).count();
        
        Map<String, Double> scores = new HashMap<>();
        if (analysis.getScores() != null) {
            // Convert from 0-10 scale to 0-100 scale
            scores.put("security", analysis.getScores().getSecurity() != null ? analysis.getScores().getSecurity() * 10 : 85.0);
            scores.put("performance", analysis.getScores().getPerformance() != null ? analysis.getScores().getPerformance() * 10 : 85.0);
            scores.put("quality", analysis.getScores().getQuality() != null ? analysis.getScores().getQuality() * 10 : 85.0);
        } else {
            // Default scores if none exist
            scores.put("security", 85.0);
            scores.put("performance", 85.0);
            scores.put("quality", 85.0);
        }
        
        return ReportResponse.builder()
                .analysisId(analysisId)
                .repository(analysis.getRepository())
                .branch(analysis.getBranch())
                .date(new Date(analysis.getCompletedAt() * 1000))
                .filesAnalyzed(analysis.getFilesAnalyzed())
                .totalIssues(issues.size())
                .scanNumber(analysis.getScanNumber())
                .criticalCount((int) criticalCount)
                .highCount((int) highCount)
                .mediumCount((int) mediumCount)
                .lowCount((int) lowCount)
                .processingTime(analysis.getProcessingTimeMs())
                .issues(issues)
                .scores(scores)
                .build();
    }
}