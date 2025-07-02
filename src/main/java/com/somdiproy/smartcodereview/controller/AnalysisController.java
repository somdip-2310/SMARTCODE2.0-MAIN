package com.somdiproy.smartcodereview.controller;

import com.somdiproy.smartcodereview.dto.AnalysisRequest;
import com.somdiproy.smartcodereview.dto.AnalysisStatusResponse;
import com.somdiproy.smartcodereview.dto.ReportResponse;
import com.somdiproy.smartcodereview.exception.ScanLimitExceededException;
import com.somdiproy.smartcodereview.exception.SessionNotFoundException;
import com.somdiproy.smartcodereview.model.Analysis;
import com.somdiproy.smartcodereview.model.Branch;
import com.somdiproy.smartcodereview.model.Issue;
import com.somdiproy.smartcodereview.model.Repository;
import com.somdiproy.smartcodereview.model.Session;
import com.somdiproy.smartcodereview.service.AnalysisOrchestrator;
import com.somdiproy.smartcodereview.service.GitHubService;
import com.somdiproy.smartcodereview.service.ReportService;
import com.somdiproy.smartcodereview.service.SecureTokenService;
import com.somdiproy.smartcodereview.service.SessionService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller for repository analysis operations
 */
@Slf4j
@Controller
public class AnalysisController {
    
    private final AnalysisOrchestrator analysisOrchestrator;
    private final SessionService sessionService;
    private final GitHubService gitHubService;
    private final SecureTokenService secureTokenService;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SessionService.class);
    private final ReportService reportService;
    
    @Autowired
    public AnalysisController(AnalysisOrchestrator analysisOrchestrator,
                             SessionService sessionService,
                             GitHubService gitHubService,
                             SecureTokenService secureTokenService,
                             ReportService reportService) {
        this.analysisOrchestrator = analysisOrchestrator;
        this.sessionService = sessionService;
        this.gitHubService = gitHubService;
        this.secureTokenService = secureTokenService;
        this.reportService = reportService;
    }
    
    /**
     * Show repository selection page
     */
    @GetMapping("/repository")
    public String showRepositorySelect(@RequestParam String sessionId, Model model) {
        try {
            log.info("🏛️ AnalysisController: Accessing repository page for sessionId: {}", sessionId);
            
            Session session = sessionService.getSession(sessionId);
            log.info("📋 Session Details: verificationStatus={}, isVerified={}, email={}, scanCount={}", 
                     session.getVerificationStatus(), session.isVerified(), session.getEmailMasked(), session.getScanCount());

            log.debug("🔍 Raw session data: sessionId={}, verificationStatus={}, ttl={}", 
                      session.getSessionId(), session.getVerificationStatus(), session.getTtl());

            if (!session.isVerified()) {
                log.warn("❌ Session not verified, redirecting to home");
                return "redirect:/";
            }
            
            model.addAttribute("sessionId", sessionId);
            model.addAttribute("session", session);
            model.addAttribute("remainingScans", session.getRemainingScans());
            
            log.info("✅ Repository page prepared successfully, rendering template: repository-select");
            return "repository-select";
            
        } catch (Exception e) {
            log.error("💥 Error in repository page for sessionId: {}", sessionId, e);
            model.addAttribute("error", "Session error: " + e.getMessage());
            return "redirect:/";
        }
    }

    /**
     * Show branches for selected repository
     */
    @GetMapping("/repository/branches")
    public String showBranches(@RequestParam String sessionId, @RequestParam String repoUrl, Model model) {
        try {
            Session session = sessionService.getSession(sessionId);
            if (!session.isVerified()) {
                return "redirect:/";
            }

            int remainingScans = 3 - session.getScanCount();
            if (remainingScans <= 0) {
                model.addAttribute("error", "You have reached the maximum number of scans for this session");
                return "error";
            }

            // Validate repository URL
            if (!gitHubService.isValidRepositoryUrl(repoUrl)) {
                model.addAttribute("error", "Invalid GitHub repository URL format");
                return "error";
            }

            // Get user's GitHub token from secure storage
            String githubToken = secureTokenService.getSessionToken(sessionId);
            if (githubToken == null) {
                model.addAttribute("error", "GitHub token not found. Please start a new session.");
                return "error";
            }

            // Fetch repository info and branches using user's token
            Repository repo = gitHubService.getRepository(repoUrl, githubToken);
            List<Branch> branches = gitHubService.fetchBranches(repoUrl, githubToken);
            
            // Get file analysis stats for cost estimation
            GitHubService.FileAnalysisStats stats = gitHubService.getFileStats(repoUrl, repo.getDefaultBranch(), githubToken);

            model.addAttribute("sessionId", sessionId);
            model.addAttribute("repository", repo);
            model.addAttribute("branches", branches);
            model.addAttribute("remainingScans", remainingScans);
            model.addAttribute("scanCount", session.getScanCount());
            model.addAttribute("fileStats", stats);
            model.addAttribute("publicOnly", false); // We support both public and private with tokens

            log.info("📊 Repository analysis preview: {} eligible files, estimated cost: ${}", 
                     stats.getEligibleFiles(), String.format("%.4f", stats.getEstimatedCost()));

            return "branch-selection";
            
        } catch (Exception e) {
            log.error("Error fetching repository branches", e);
            
            // Check if it's a private repo access issue
            if (e.getMessage().contains("Not Found") || e.getMessage().contains("404")) {
                model.addAttribute("error", "Repository not found or you don't have access. Please check the URL and ensure your GitHub token has the necessary permissions.");
                return "error";
            }
            
            // Check if it's a rate limit issue
            if (e.getMessage().contains("rate limit")) {
                model.addAttribute("error", "GitHub API rate limit exceeded. Please wait a few minutes and try again.");
                return "error";
            }
            
            // Check if it's an authentication issue
            if (e.getMessage().contains("401") || e.getMessage().contains("authentication")) {
                model.addAttribute("error", "GitHub authentication failed. Please ensure your token is valid and has the 'repo' scope.");
                return "error";
            }
            
            model.addAttribute("error", "Error accessing repository: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Start code analysis
     */
    @PostMapping("/analyze")
    public String startAnalysis(@RequestParam String sessionId, 
                               @RequestParam String repoUrl,
                               @RequestParam String branch, 
                               RedirectAttributes redirectAttributes) {
        try {
            // Verify session and check scan limit
            Session session = sessionService.getSession(sessionId);
            if (!session.canPerformScan()) {
                throw new ScanLimitExceededException("Maximum 3 scans per session reached");
            }
            
            // Get GitHub token
            String githubToken = secureTokenService.getSessionToken(sessionId);
            if (githubToken == null) {
                redirectAttributes.addFlashAttribute("error", "GitHub token not found. Please start a new session.");
                return "redirect:/error";
            }
            
            // Start analysis with token
            String analysisId = analysisOrchestrator.startAnalysis(sessionId, repoUrl, branch, githubToken, session.getScanCount() + 1);
            return "redirect:/analysis/progress?analysisId=" + analysisId + "&sessionId=" + sessionId;
            
        } catch (ScanLimitExceededException e) {
            redirectAttributes.addFlashAttribute("error", "Maximum 3 scans per session reached");
            return "redirect:/error";
        } catch (Exception e) {
            log.error("Error starting analysis", e);
            redirectAttributes.addFlashAttribute("error", "Failed to start analysis: " + e.getMessage());
            return "redirect:/error";
        }
    }

    /**
     * Show analysis progress page
     */
    @GetMapping("/analysis/progress")
    public String showProgress(@RequestParam String analysisId, 
                              @RequestParam String sessionId, 
                              Model model) {
        try {
            // Verify session
            Session session = sessionService.getSession(sessionId);
            
            model.addAttribute("analysisId", analysisId);
            model.addAttribute("sessionId", sessionId);
            model.addAttribute("email", session.getEmailMasked());
            
            return "analysis-progress";
            
        } catch (Exception e) {
            log.error("Error showing progress page", e);
            model.addAttribute("error", "Invalid session or analysis");
            return "error";
        }
    }

    /**
     * Get analysis status (AJAX endpoint)
     */
    @GetMapping("/api/analysis/{analysisId}/status")
    @ResponseBody
    public AnalysisStatusResponse getStatus(@PathVariable String analysisId) {
        try {
            return analysisOrchestrator.getAnalysisStatus(analysisId);
        } catch (Exception e) {
            log.error("Error getting analysis status", e);
            return AnalysisStatusResponse.builder()
                .analysisId(analysisId)
                .status("ERROR")
                .error(e.getMessage())
                .build();
        }
    }
    
    /**
     * Show analysis report
     */
    @GetMapping("/report/{analysisId}")
    public String showReport(@PathVariable String analysisId,
                            @RequestParam String sessionId,
                            Model model) {
        try {
            // Verify session
            Session session = sessionService.getSession(sessionId);
            
            // Get analysis to verify ownership
            Analysis analysis = analysisOrchestrator.getAnalysis(analysisId);

            // Verify this analysis belongs to the session
            if (!analysis.getSessionId().equals(sessionId)) {
                throw new SecurityException("Analysis does not belong to this session");
            }

         // Get comprehensive report using ReportService
            ReportResponse report = reportService.getReport(analysisId);

            // Segregate issues by type
            List<Issue> securityIssues = report.getIssues().stream()
                .filter(i -> "SECURITY".equalsIgnoreCase(i.getCategory()) && 
                            i.getCveId() != null && !i.getCveId().isEmpty())
                .collect(Collectors.toList());
            
            List<Issue> otherIssues = report.getIssues().stream()
                .filter(i -> !securityIssues.contains(i))
                .collect(Collectors.toList());

            // Add all required attributes for the report template
            model.addAttribute("analysisId", analysisId);
            model.addAttribute("sessionId", sessionId);
            model.addAttribute("report", report);  // Changed from 'analysis' to 'report'
            model.addAttribute("remainingScans", session.getRemainingScans());
            model.addAttribute("securityIssues", securityIssues);
            model.addAttribute("otherIssues", otherIssues);

            return "report";
            
        } catch (Exception e) {
            log.error("Error showing report", e);
            model.addAttribute("error", "Error loading report: " + e.getMessage());
            return "error";
        }
    }
}