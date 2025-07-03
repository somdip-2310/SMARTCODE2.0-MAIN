package com.somdiproy.smartcodereview.service;

import com.somdiproy.smartcodereview.dto.AnalysisStatusResponse;
import com.somdiproy.smartcodereview.exception.ScanLimitExceededException;
import com.somdiproy.smartcodereview.model.Analysis;
import com.somdiproy.smartcodereview.model.AnalysisResult;
import com.somdiproy.smartcodereview.model.Session;
import com.somdiproy.smartcodereview.repository.AnalysisRepository;
import com.somdiproy.smartcodereview.repository.SessionRepository;
import com.somdiproy.smartcodereview.service.GitHubService.GitHubFile;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service that orchestrates the three-tier analysis process
 * Coordinates GitHub code fetching and Lambda invocations
 */
@Slf4j
@Service
public class AnalysisOrchestrator {
    
    private final SessionService sessionService;
    private final AnalysisRepository analysisRepository;
    private final DataAggregationService dataAggregationService;
    private final GitHubService gitHubService;
    private final LambdaInvokerService lambdaInvokerService;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LambdaInvokerService.class);

    // In-memory storage for analysis progress (replace with Redis in production)
    private final ConcurrentHashMap<String, Analysis> analysisProgress = new ConcurrentHashMap<>();
    
    @Autowired
    public AnalysisOrchestrator(SessionService sessionService,
                               GitHubService gitHubService,
                               LambdaInvokerService lambdaInvokerService,
                               AnalysisRepository analysisRepository,
                               DataAggregationService dataAggregationService) {
        this.sessionService = sessionService;
        this.analysisRepository = analysisRepository;
        this.gitHubService = gitHubService;
        this.lambdaInvokerService = lambdaInvokerService;
        this.dataAggregationService = dataAggregationService;
    }
    
    /**
     * Start analysis for a repository
     */
    public String startAnalysis(String sessionId, String repoUrl, String branch, 
                               String githubToken, int scanNumber) {
        // Validate session and scan limit
        Session session = sessionService.getSession(sessionId);
        
        if (session.getScanCount() >= 3) {
            throw new ScanLimitExceededException("Maximum 3 scans per session reached");
        }
        
        // Create analysis record
        String analysisId = UUID.randomUUID().toString();
        Analysis analysis = Analysis.builder()
                .analysisId(analysisId)
                .sessionId(sessionId)
                .repository(repoUrl)
                .branch(branch)
                .status(Analysis.AnalysisStatus.PENDING)
                .progress(0)
                .startedAt(System.currentTimeMillis() / 1000)
                .scanNumber(scanNumber)
                .build();
        
        // Store in progress map
        analysisProgress.put(analysisId, analysis);
        
        // Start async analysis
        processAnalysisAsync(analysisId, sessionId, repoUrl, branch, githubToken, scanNumber);
        
        return analysisId;
    }
    
    @Async
    protected void processAnalysisAsync(String analysisId, String sessionId, String repoUrl, 
                                       String branch, String githubToken, int scanNumber) {
        try {
            Analysis analysis = analysisProgress.get(analysisId);
            analysis.setStatus(Analysis.AnalysisStatus.IN_PROGRESS);
            
            log.info("🚀 Starting analysis {} for repository {} branch {}", analysisId, repoUrl, branch);
            
            // Stage 1: Fetch code from GitHub
            log.info("📥 Stage 1: Fetching code from GitHub");
            List<GitHubFile> files = gitHubService.fetchBranchCode(repoUrl, branch, githubToken);
            analysis.setTotalFiles(files.size());
            analysis.setProgress(10);
            log.info("✓ Fetched {} files from repository", files.size());
            
            // Stage 2: Screening with Nova Micro
            log.info("🔍 Stage 2: Screening files with Nova Micro");
            List<Map<String, Object>> screenedFiles = lambdaInvokerService.invokeScreening(
                    sessionId,
                    analysisId,
                    repoUrl, 
                    branch, 
                    files,
                    scanNumber
            );
            analysis.setProgress(33);
            log.info("✓ Screening complete: {} valid files out of {}", screenedFiles.size(), files.size());
            
            // Store screening results for aggregation
            dataAggregationService.storeScreeningResults(analysisId, screenedFiles);
            
            // Stage 3: Detection with Nova Lite
            log.info("🎯 Stage 3: Detecting issues with Nova Lite");
            List<Map<String, Object>> issues = lambdaInvokerService.invokeDetection(
                    sessionId,
                    analysisId,
                    repoUrl,
                    branch,
                    screenedFiles,
                    scanNumber
            );
            analysis.setIssuesFound(issues.size());
            analysis.setProgress(66);
            log.info("✓ Detection complete: {} issues found", issues.size());
            
            // Store detection results for aggregation
            dataAggregationService.storeDetectionResults(analysisId, issues);
            
            // Filter issues for suggestions - only high-severity security issues
            List<Map<String, Object>> securityIssuesForSuggestions = filterSecurityIssuesForSuggestions(issues, logger);

            // Stage 4: Suggestions with Nova Premier (security issues only)
            log.info("🔒 Stage 4: Generating suggestions for {} high-severity security issues (from {} total issues)", 
                     securityIssuesForSuggestions.size(), issues.size());
                     
            String suggestionResponse = lambdaInvokerService.invokeSuggestions(
                    sessionId,
                    analysisId,
                    repoUrl,
                    branch,
                    securityIssuesForSuggestions,  // Only security issues
                    scanNumber
            );

            // Store ALL issues for display (including performance)
            dataAggregationService.storeDetectionResults(analysisId, issues);  // ALL issues for display
            
            if (suggestionResponse != null) {
                dataAggregationService.storeSuggestionResults(analysisId, suggestionResponse);
            }
            
            analysis.setProgress(100);
            log.info("✓ Suggestions generated successfully");
            
            // Mark as completed
            analysis.setStatus(Analysis.AnalysisStatus.COMPLETED);
            analysis.setCompletedAt(System.currentTimeMillis() / 1000);
            
            long duration = analysis.getCompletedAt() - analysis.getStartedAt();
            log.info("✅ Analysis {} completed successfully in {} seconds", analysisId, duration);
            
            // Aggregate and save all results to DynamoDB
            dataAggregationService.aggregateAndSaveResults(analysis);
            
        } catch (Exception e) {
            log.error("❌ Analysis failed for {}: {}", analysisId, e.getMessage(), e);
            Analysis analysis = analysisProgress.get(analysisId);
            if (analysis != null) {
                analysis.setStatus(Analysis.AnalysisStatus.FAILED);
                analysis.setError(e.getMessage());
            }
        }
    }
    
    /**
     * Filter issues to only include high-severity security issues for suggestions
     * while keeping ALL issues for display in results
     */
    private List<Map<String, Object>> filterSecurityIssuesForSuggestions(List<Map<String, Object>> allIssues, 
                                                                         org.slf4j.Logger logger) {
        List<Map<String, Object>> securityIssues = allIssues.stream()
            .filter(issue -> {
                String category = (String) issue.getOrDefault("category", "");
                return "security".equalsIgnoreCase(category);
            })
            .filter(issue -> {
                String severity = (String) issue.getOrDefault("severity", "LOW");
                return "CRITICAL".equalsIgnoreCase(severity) || "HIGH".equalsIgnoreCase(severity);
            })
            .filter(issue -> {
                // Estimate CVE score
                double cveScore = estimateCVEScore(issue);
                return cveScore > 7.0;
            })
            .sorted((a, b) -> {
                String severityA = (String) a.getOrDefault("severity", "LOW");
                String severityB = (String) b.getOrDefault("severity", "LOW");
                return getSeverityPriority(severityB) - getSeverityPriority(severityA);
            })
            .limit(15) // Max 15 security issues for suggestions
            .collect(Collectors.toList());
        
        logger.info("📊 Filtered {} high-severity security issues for suggestions from {} total issues", 
                   securityIssues.size(), allIssues.size());
        
        return securityIssues;
    }

    /**
     * Estimate CVE score for filtering
     */
    private double estimateCVEScore(Map<String, Object> issue) {
        String type = ((String) issue.getOrDefault("type", "")).toUpperCase();
        String severity = ((String) issue.getOrDefault("severity", "")).toUpperCase();
        
        double baseScore = switch (severity) {
            case "CRITICAL" -> 9.0;
            case "HIGH" -> 7.5;
            case "MEDIUM" -> 5.0;
            case "LOW" -> 2.0;
            default -> 4.0;
        };
        
        double typeMultiplier = switch (type) {
            case "SQL_INJECTION" -> 1.1;
            case "XSS", "CROSS_SITE_SCRIPTING" -> 1.0;
            case "INSECURE_DESERIALIZATION" -> 1.1;
            case "AUTHENTICATION_BYPASS" -> 1.2;
            case "AUTHORIZATION_BYPASS" -> 1.1;
            case "REMOTE_CODE_EXECUTION", "RCE" -> 1.3;
            case "COMMAND_INJECTION" -> 1.2;
            default -> 0.9;
        };
        
        return baseScore * typeMultiplier;
    }

    /**
     * Get severity priority for sorting
     */
    private int getSeverityPriority(String severity) {
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }
    
    /**
     * Get current analysis status
     */
    public AnalysisStatusResponse getAnalysisStatus(String analysisId) {
        Analysis analysis = analysisProgress.get(analysisId);
        if (analysis == null) {
            // Try to fetch from database
            return analysisRepository.findById(analysisId)
                    .map(result -> AnalysisStatusResponse.builder()
                            .analysisId(analysisId)
                            .status("completed")
                            .progress(AnalysisStatusResponse.Progress.builder()
                                    .overall(100)
                                    .screening("completed")
                                    .detection("completed")
                                    .suggestions("completed")
                                    .build())
                            .filesProcessed(result.getFilesAnalyzed())
                            .totalFiles(result.getFilesSubmitted())
                            .issuesFound(result.getSummary() != null ? result.getSummary().getTotalIssues() : 0)
                            .estimatedTimeRemaining(0)
                            .build())
                    .orElse(AnalysisStatusResponse.builder()
                            .analysisId(analysisId)
                            .status("not_found")
                            .error("Analysis not found")
                            .build());
        }
        
        return AnalysisStatusResponse.builder()
                .analysisId(analysisId)
                .status(analysis.getStatus().toString().toLowerCase())
                .progress(AnalysisStatusResponse.Progress.builder()
                        .overall(analysis.getProgress())
                        .screening(analysis.getProgress() >= 33 ? "completed" : 
                                  analysis.getProgress() >= 10 ? "in_progress" : "pending")
                        .detection(analysis.getProgress() >= 66 ? "completed" : 
                                  analysis.getProgress() >= 33 ? "in_progress" : "pending")
                        .suggestions(analysis.getProgress() >= 100 ? "completed" : 
                                    analysis.getProgress() >= 66 ? "in_progress" : "pending")
                        .build())
                .filesProcessed(analysis.getFilesProcessed() != null ? analysis.getFilesProcessed().size() : 0)
                .totalFiles(analysis.getTotalFiles())
                .issuesFound(analysis.getIssuesFound())
                .estimatedTimeRemaining(calculateETA(analysis))
                .error(analysis.getError())
                .build();
    }
    
    /**
     * Get full analysis details
     */
    public Analysis getAnalysis(String analysisId) {
        // First check in-memory cache
        Analysis analysis = analysisProgress.get(analysisId);
        if (analysis != null) {
            return analysis;
        }
        
        // Then check database
        AnalysisResult result = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new RuntimeException("Analysis not found: " + analysisId));

        return Analysis.builder()
                .analysisId(result.getAnalysisId())
                .sessionId(result.getSessionId())
                .repository(result.getRepository())
                .branch(result.getBranch())
                .status(Analysis.AnalysisStatus.COMPLETED)
                .progress(100)
                .startedAt(result.getStartedAt())
                .completedAt(result.getCompletedAt())
                .totalFiles(result.getFilesSubmitted())
                .issuesFound(result.getSummary() != null ? result.getSummary().getTotalIssues() : 0)
                .scanNumber(result.getScanNumber())
                .build();
    }
    
    /**
     * Calculate estimated time remaining
     */
    private Integer calculateETA(Analysis analysis) {
        if (analysis.getProgress() >= 100) return 0;
        if (analysis.getProgress() < 33) return 120; // 2 minutes
        if (analysis.getProgress() < 66) return 60;  // 1 minute
        return 30; // 30 seconds
    }
}