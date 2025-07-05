// src/main/java/com/somdiproy/smartcodereview/service/AnalysisOrchestrator.java
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AnalysisOrchestrator.class);

    // In-memory storage for analysis progress (replace with Redis in production)
    private final ConcurrentHashMap<String, Analysis> analysisProgress = new ConcurrentHashMap<>();
    @Autowired
    private BalancedAllocationService balancedAllocationService;
    
    @Autowired
    private ApplicationContext applicationContext;
    
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
        
     // Start async analysis - use self-invocation proxy to ensure @Async works
        AnalysisOrchestrator self = applicationContext.getBean(AnalysisOrchestrator.class);
        self.processAnalysisAsync(analysisId, sessionId, repoUrl, branch, githubToken, scanNumber);
        
        return analysisId;
    }
    
    @Async("lambdaTaskExecutor")
    public void processAnalysisAsync(String analysisId, String sessionId, String repoUrl, 
                                       String branch, String githubToken, int scanNumber) {
        Analysis analysis = analysisProgress.get(analysisId);
        String suggestionResponse = null; // Declare at method level for proper scope
        
        try {
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
         // Stage 4: Suggestions with Hybrid Strategy (90% Nova Lite, 9% Templates, 1% Nova Premier)
            log.info("🔄 Stage 4: Generating suggestions using hybrid strategy for {} issues (Cost-optimized)", 
                     issues.size());
            log.info("📊 Hybrid Distribution: 90% Nova Lite, 9% Templates, 1% Nova Premier for critical security");

            // Prevent multiple Lambda instances for same analysis
            String lockKey = "suggestions_" + analysisId;
            if (!acquireAnalysisLock(lockKey)) {
                log.warn("⚠️ Another Lambda instance is already processing suggestions for analysis {}", analysisId);
                // Continue with analysis completion even if suggestions are skipped
            } else {
                try {
                	// Apply balanced allocation strategy instead of simple limit
                	List<Map<String, Object>> issuesForSuggestions = applyBalancedAllocationForSuggestions(issues, log);

                	log.info("🎯 Processing {} issues with balanced allocation strategy ensuring CRITICAL/HIGH coverage across all categories", 
                	         issuesForSuggestions.size());

                	// Use balanced suggestions covering all categories
                	suggestionResponse = lambdaInvokerService.invokeSuggestions(
                	        sessionId,
                	        analysisId,
                	        repoUrl,
                	        branch,
                	        issuesForSuggestions,  // Balanced allocation across categories
                	        scanNumber
                	);
                    
                    log.info("✓ Suggestions generated successfully");
                    
                } catch (Exception suggestionsError) {
                    log.error("❌ Error generating suggestions for analysis {}: {}", analysisId, suggestionsError.getMessage());
                    // Continue with analysis completion even if suggestions fail
                } finally {
                    releaseAnalysisLock(lockKey);
                }
            }
            
            // Store suggestion results if available
            if (suggestionResponse != null) {
                dataAggregationService.storeSuggestionResults(analysisId, suggestionResponse);
                log.info("✓ Suggestions stored successfully");
            } else {
                log.warn("⚠️ No suggestions generated for analysis {}", analysisId);
            }
            
            // Complete analysis
            analysis.setProgress(100);
            analysis.setStatus(Analysis.AnalysisStatus.COMPLETED);
            analysis.setCompletedAt(System.currentTimeMillis() / 1000);
            
            long duration = analysis.getCompletedAt() - analysis.getStartedAt();
            log.info("✅ Analysis {} completed successfully in {} seconds", analysisId, duration);
            
            // Aggregate and save all results to DynamoDB
            dataAggregationService.aggregateAndSaveResults(analysis);
            
        } catch (Exception e) {
            log.error("❌ Analysis failed for {}: {}", analysisId, e.getMessage(), e);
            if (analysis != null) {
                analysis.setStatus(Analysis.AnalysisStatus.FAILED);
                analysis.setError(e.getMessage());
                analysis.setCompletedAt(System.currentTimeMillis() / 1000);
            }
            
            // Store partial results even on failure
            try {
                if (analysis != null) {
                    dataAggregationService.aggregateAndSaveResults(analysis);
                }
            } catch (Exception saveError) {
                log.error("❌ Failed to save partial results for analysis {}: {}", analysisId, saveError.getMessage());
            }
        }
    }

    /**
     * Apply balanced allocation strategy for suggestions across all categories
     * Replaces the old security-only filtering approach
     */
    private List<Map<String, Object>> applyBalancedAllocationForSuggestions(List<Map<String, Object>> allIssues, 
                                                                            org.slf4j.Logger logger) {
        // Use the new balanced allocation service
        BalancedAllocationService.AllocationResult allocationResult = 
                balancedAllocationService.allocateIssuesForSuggestions(allIssues);
        
        // Log the balanced distribution
        Map<String, Integer> categoryCounts = allocationResult.getCategoryCounts();
        logger.info("🎯 Balanced allocation complete - Security: {}, Performance: {}, Quality: {}, Total: {}",
                    categoryCounts.get("security"),
                    categoryCounts.get("performance"), 
                    categoryCounts.get("quality"),
                    allocationResult.getTotalSelected());
        
        // Return all selected issues for suggestion generation
        return allocationResult.getAllSelectedIssues();
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
     * Calculate estimated time remaining based on actual progress
     */
    private Integer calculateETA(Analysis analysis) {
        if (analysis.getProgress() >= 100) return 0;
        
     // More accurate ETA based on stage and file count
        int filesCount = analysis.getTotalFiles() != null ? analysis.getTotalFiles() : 20;
        int baseTime = 5 + (filesCount / 10) * 2; // Base time increases with file count
        
        if (analysis.getProgress() < 33) {
            // Screening phase
            return baseTime + 60; // 1-2 minutes
        } else if (analysis.getProgress() < 66) {
            // Detection phase  
            return baseTime + 30; // 30-60 seconds
        } else {
            // Suggestion phase
            return baseTime + 15; // 15-30 seconds
        }
    }
    
    /**
     * Get analysis preview data
     */
    public List<Map<String, String>> getAnalysisPreview(String analysisId) {
        try {
            // Check if we have detection results in the data aggregation service
            List<Map<String, Object>> detectionResults = dataAggregationService.getDetectionResults(analysisId);
            if (detectionResults == null || detectionResults.isEmpty()) {
                return new ArrayList<>();
            }
            
            // Convert first 3 issues to preview format
            return detectionResults.stream()
                .limit(3)
                .map(issue -> {
                    Map<String, String> preview = new HashMap<>();
                    preview.put("type", (String) issue.getOrDefault("type", "Unknown"));
                    preview.put("severity", (String) issue.getOrDefault("severity", "MEDIUM"));
                    preview.put("file", (String) issue.getOrDefault("file", "Unknown file"));
                    return preview;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error getting analysis preview: ", e);
            return new ArrayList<>();
        }
    }
    
    // Analysis lock management for preventing concurrent suggestions processing
    private final ConcurrentHashMap<String, Long> analysisLocks = new ConcurrentHashMap<>();
    private static final long LOCK_TIMEOUT_MS = 1800000; // 30 minutes

    private boolean acquireAnalysisLock(String lockKey) {
        long currentTime = System.currentTimeMillis();
        Long existingLock = analysisLocks.get(lockKey);
        
        if (existingLock != null && (currentTime - existingLock) < LOCK_TIMEOUT_MS) {
            return false; // Lock still active
        }
        
        analysisLocks.put(lockKey, currentTime);
        return true;
    }

    private void releaseAnalysisLock(String lockKey) {
        analysisLocks.remove(lockKey);
    }
}