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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that orchestrates the three-tier analysis process
 * Coordinates GitHub code fetching and Lambda invocations
 */
@Slf4j
@Service
public class AnalysisOrchestrator {
    
    private final SessionRepository sessionRepository;
    private final AnalysisRepository analysisRepository;
    private final GitHubService gitHubService;
    private final LambdaInvokerService lambdaInvokerService;
    private final SecureTokenService secureTokenService;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SessionService.class);
    
    // In-memory storage for analysis progress (replace with Redis in production)
    private final ConcurrentHashMap<String, Analysis> analysisProgress = new ConcurrentHashMap<>();
    
    @Autowired
    public AnalysisOrchestrator(SessionRepository sessionRepository,
                               AnalysisRepository analysisRepository,
                               GitHubService gitHubService,
                               LambdaInvokerService lambdaInvokerService,
                               SecureTokenService secureTokenService) {
        this.sessionRepository = sessionRepository;
        this.analysisRepository = analysisRepository;
        this.gitHubService = gitHubService;
        this.lambdaInvokerService = lambdaInvokerService;
        this.secureTokenService = secureTokenService;
    }
    
    /**
     * Start repository analysis with GitHub token
     */
    public Analysis analyzeRepository(String repoUrl, String branch, String sessionId, String githubToken) {
        // Validate session and scan limit
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        
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
                .startedAt(Instant.now().getEpochSecond())
                .build();
        
        // Store in progress map
        analysisProgress.put(analysisId, analysis);
        
        // Update session
        session.incrementScanCount();
        session.addScan(analysisId, repoUrl, branch);
        sessionRepository.update(session);
        
        // Start async analysis with provided token
        startAsyncAnalysis(analysisId, repoUrl, branch, githubToken);
        
        return analysis;
    }
    
    /**
     * Start repository analysis (backward compatibility - retrieves token from secure storage)
     */
    public Analysis analyzeRepository(String repoUrl, String branch, String sessionId) {
        // Get GitHub token from secure storage
        String githubToken = secureTokenService.getSessionToken(sessionId);
        if (githubToken == null) {
            throw new RuntimeException("GitHub token not found for session. Please start a new session.");
        }
        
        return analyzeRepository(repoUrl, branch, sessionId, githubToken);
    }
    
    @Async
    protected void startAsyncAnalysis(String analysisId, String repoUrl, String branch, String githubToken) {
        try {
            Analysis analysis = analysisProgress.get(analysisId);
            analysis.setStatus(Analysis.AnalysisStatus.IN_PROGRESS);
            
            // Get session from analysis
            String sessionId = analysis.getSessionId();
            Session session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new RuntimeException("Session not found"));
            
            log.info("🚀 Starting analysis {} for repository {} branch {}", analysisId, repoUrl, branch);
            
            // Stage 1: Fetch code from GitHub
            log.info("📥 Stage 1: Fetching code from GitHub");
            List<GitHubFile> files = gitHubService.fetchBranchCode(repoUrl, branch, githubToken);
            analysis.setTotalFiles(files.size());
            analysis.setProgress(10);
            log.info("✓ Fetched {} files from repository", files.size());
            
            // Stage 2: Screening with Nova Micro
            log.info("🔍 Stage 2: Screening files with Nova Micro");
            List<String> validFiles = lambdaInvokerService.invokeScreening(
                    sessionId, 
                    analysisId, 
                    repoUrl, 
                    branch, 
                    files,
                    session.getScanCount()
            );
            analysis.setProgress(33);
            log.info("✓ Screening complete: {} valid files out of {}", validFiles.size(), files.size());
            
            // Stage 3: Detection with Nova Lite
            log.info("🎯 Stage 3: Detecting issues with Nova Lite");
            List<String> issues = lambdaInvokerService.invokeDetection(validFiles);
            analysis.setIssuesFound(issues.size());
            analysis.setProgress(66);
            log.info("✓ Detection complete: {} issues found", issues.size());
            
            // Stage 4: Suggestions with Nova Premier
            log.info("💡 Stage 4: Generating suggestions with Nova Premier");
            lambdaInvokerService.invokeSuggestions(issues);
            analysis.setProgress(100);
            log.info("✓ Suggestions generated successfully");
            
            // Mark as completed
            analysis.setStatus(Analysis.AnalysisStatus.COMPLETED);
            analysis.setCompletedAt(Instant.now().getEpochSecond());
            
            long duration = analysis.getCompletedAt() - analysis.getStartedAt();
            log.info("✅ Analysis {} completed successfully in {} seconds", analysisId, duration);
            
            // Save to DynamoDB
            saveAnalysisResult(analysis);
            
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
        return analysisRepository.findById(analysisId)
                .map(result -> Analysis.builder()
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
                        .build())
                .orElseThrow(() -> new RuntimeException("Analysis not found: " + analysisId));
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
    
    /**
     * Save analysis results to DynamoDB
     */
    private void saveAnalysisResult(Analysis analysis) {
        AnalysisResult result = AnalysisResult.builder()
                .analysisId(analysis.getAnalysisId())
                .sessionId(analysis.getSessionId())
                .repository(analysis.getRepository())
                .branch(analysis.getBranch())
                .status("completed")
                .startedAt(analysis.getStartedAt())
                .completedAt(analysis.getCompletedAt())
                .filesSubmitted(analysis.getTotalFiles())
                .filesAnalyzed(analysis.getTotalFiles())
                .filesSkipped(0)
                .processingTimeMs((analysis.getCompletedAt() - analysis.getStartedAt()) * 1000)
                .build();
        
        analysisRepository.save(result);
        
        log.info("💾 Analysis result saved to database: {}", analysis.getAnalysisId());
    }
}