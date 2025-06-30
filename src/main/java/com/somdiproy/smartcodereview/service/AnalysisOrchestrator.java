package com.somdiproy.smartcodereview.service;

import com.somdiproy.smartcodereview.dto.AnalysisStatusResponse;
import com.somdiproy.smartcodereview.exception.ScanLimitExceededException;
import com.somdiproy.smartcodereview.model.Analysis;
import com.somdiproy.smartcodereview.model.AnalysisResult;
import com.somdiproy.smartcodereview.model.Session;
import com.somdiproy.smartcodereview.repository.AnalysisRepository;
import com.somdiproy.smartcodereview.repository.SessionRepository;
import com.somdiproy.smartcodereview.service.GitHubService.GitHubFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisOrchestrator {
    
    private final SessionRepository sessionRepository;
    private final AnalysisRepository analysisRepository;
    private final GitHubService gitHubService;
    private final LambdaInvokerService lambdaInvokerService;
    
    // In-memory storage for analysis progress (replace with Redis in production)
    private final ConcurrentHashMap<String, Analysis> analysisProgress = new ConcurrentHashMap<>();
    
    public Analysis analyzeRepository(String repoUrl, String branch, String sessionId) {
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
        
        // Start async analysis
        startAsyncAnalysis(analysisId, repoUrl, branch, session.getGithubToken());
        
        return analysis;
    }
    
    @Async
    protected void startAsyncAnalysis(String analysisId, String repoUrl, String branch, String githubToken) {
        try {
            Analysis analysis = analysisProgress.get(analysisId);
            analysis.setStatus(Analysis.AnalysisStatus.IN_PROGRESS);
            
            // Stage 1: Fetch code from GitHub
            List<GitHubFile> files = gitHubService.fetchBranchCode(repoUrl, branch, githubToken);
            analysis.setTotalFiles(files.size());
            analysis.setProgress(10);
            
            // Stage 2: Screening with Nova Micro
            log.info("Starting screening for analysis: {}", analysisId);
            List<String> validFiles = lambdaInvokerService.invokeScreening(files);
            analysis.setProgress(33);
            
            // Stage 3: Detection with Nova Lite
            log.info("Starting detection for analysis: {}", analysisId);
            List<String> issues = lambdaInvokerService.invokeDetection(validFiles);
            analysis.setIssuesFound(issues.size());
            analysis.setProgress(66);
            
            // Stage 4: Suggestions with Nova Premier
            log.info("Starting suggestion generation for analysis: {}", analysisId);
            lambdaInvokerService.invokeSuggestions(issues);
            analysis.setProgress(100);
            
            // Mark as completed
            analysis.setStatus(Analysis.AnalysisStatus.COMPLETED);
            analysis.setCompletedAt(Instant.now().getEpochSecond());
            
            // Save to DynamoDB
            saveAnalysisResult(analysis);
            
        } catch (Exception e) {
            log.error("Analysis failed for {}: {}", analysisId, e.getMessage());
            Analysis analysis = analysisProgress.get(analysisId);
            if (analysis != null) {
                analysis.setStatus(Analysis.AnalysisStatus.FAILED);
            }
        }
    }
    
    public AnalysisStatusResponse getAnalysisStatus(String analysisId) {
        Analysis analysis = analysisProgress.get(analysisId);
        if (analysis == null) {
            // Try to fetch from database
            return AnalysisStatusResponse.builder()
                    .analysisId(analysisId)
                    .status("not_found")
                    .build();
        }
        
        return AnalysisStatusResponse.builder()
                .analysisId(analysisId)
                .status(analysis.getStatus().toString().toLowerCase())
                .progress(AnalysisStatusResponse.Progress.builder()
                        .overall(analysis.getProgress())
                        .screening(analysis.getProgress() >= 33 ? "completed" : "pending")
                        .detection(analysis.getProgress() >= 66 ? "completed" : "pending")
                        .suggestions(analysis.getProgress() >= 100 ? "completed" : "pending")
                        .build())
                .filesProcessed(analysis.getFilesProcessed() != null ? analysis.getFilesProcessed().size() : 0)
                .totalFiles(analysis.getTotalFiles())
                .issuesFound(analysis.getIssuesFound())
                .estimatedTimeRemaining(calculateETA(analysis))
                .build();
    }
    
    private Integer calculateETA(Analysis analysis) {
        if (analysis.getProgress() >= 100) return 0;
        if (analysis.getProgress() < 33) return 120; // 2 minutes
        if (analysis.getProgress() < 66) return 60;  // 1 minute
        return 30; // 30 seconds
    }
    
    private void saveAnalysisResult(Analysis analysis) {
        AnalysisResult result = AnalysisResult.builder()
                .analysisId(analysis.getAnalysisId())
                .sessionId(analysis.getSessionId())
                .repository(analysis.getRepository())
                .branch(analysis.getBranch())
                .status("completed")
                .startedAt(analysis.getStartedAt())
                .completedAt(analysis.getCompletedAt())
                .filesAnalyzed(analysis.getTotalFiles())
                .build();
        
        analysisRepository.save(result);
    }
}