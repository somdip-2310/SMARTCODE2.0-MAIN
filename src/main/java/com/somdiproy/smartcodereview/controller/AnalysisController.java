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

         // Separate security issues with actionable fixes
            List<Issue> securityIssues = report.getIssues().stream()
                .filter(issue -> "security".equalsIgnoreCase(issue.getCategory()))
                .filter(issue -> hasActionableFix(issue)) // Only issues with real fixes
                .sorted((issue1, issue2) -> {
                    int severityCompare = Integer.compare(
                        getSeverityPriority(issue2.getSeverity()), 
                        getSeverityPriority(issue1.getSeverity())
                    );
                    return severityCompare != 0 ? severityCompare : 
                        (issue1.getFile() != null && issue2.getFile() != null ? 
                            issue1.getFile().compareTo(issue2.getFile()) : 0);
                })
                .collect(Collectors.toList());

            // Additional security issues without automated fixes
            List<Issue> additionalSecurityIssues = report.getIssues().stream()
                .filter(issue -> "security".equalsIgnoreCase(issue.getCategory()))
                .filter(issue -> !hasActionableFix(issue)) // Issues without automated fixes
                .filter(issue -> issue.getCveId() != null || issue.getTitle() != null) // But with meaningful data
                .sorted((issue1, issue2) -> {
                    int severityCompare = Integer.compare(
                        getSeverityPriority(issue2.getSeverity()), 
                        getSeverityPriority(issue1.getSeverity())
                    );
                    return severityCompare != 0 ? severityCompare : 
                        (issue1.getFile() != null && issue2.getFile() != null ? 
                            issue1.getFile().compareTo(issue2.getFile()) : 0);
                })
                .collect(Collectors.toList());

            // Filter other issues to only those with actionable suggestions
            List<Issue> otherIssues = report.getIssues().stream()
                .filter(issue -> !"security".equalsIgnoreCase(issue.getCategory()))
                .filter(issue -> hasActionableFix(issue)) // Only issues with real fixes
                .sorted((issue1, issue2) -> {
                    int categoryCompare = Integer.compare(
                        getCategoryPriority(issue2.getCategory()), 
                        getCategoryPriority(issue1.getCategory())
                    );
                    if (categoryCompare != 0) return categoryCompare;
                    
                    int severityCompare = Integer.compare(
                        getSeverityPriority(issue2.getSeverity()), 
                        getSeverityPriority(issue1.getSeverity())
                    );
                    return severityCompare != 0 ? severityCompare : 
                        (issue1.getFile() != null && issue2.getFile() != null ? 
                            issue1.getFile().compareTo(issue2.getFile()) : 0);
                })
                .collect(Collectors.toList());

         // Debug logging
            log.info("Total issues found: {}", report.getIssues().size());
            log.info("Security issues: {}", securityIssues.size());
            log.info("Other issues: {}", otherIssues.size());

            // Log sample data for debugging
            if (!securityIssues.isEmpty()) {
                Issue sampleSecurity = securityIssues.get(0);
                log.info("Sample security issue: title={}, type={}, cveId={}, suggestion={}", 
                    sampleSecurity.getTitle(), sampleSecurity.getType(), 
                    sampleSecurity.getCveId(), sampleSecurity.getSuggestion() != null);
            }

            if (!otherIssues.isEmpty()) {
                Issue sampleOther = otherIssues.get(0);
                log.info("Sample other issue: title={}, type={}, category={}, suggestion={}", 
                    sampleOther.getTitle(), sampleOther.getType(), 
                    sampleOther.getCategory(), sampleOther.getSuggestion() != null);
            }

            // Add all required attributes for the report template
            model.addAttribute("analysisId", analysisId);
            model.addAttribute("sessionId", sessionId);
            model.addAttribute("report", report);
            model.addAttribute("remainingScans", session.getRemainingScans());
            model.addAttribute("securityIssues", securityIssues);
            model.addAttribute("additionalSecurityIssues", additionalSecurityIssues);
            model.addAttribute("otherIssues", otherIssues);

            return "report";
            
        } catch (Exception e) {
            log.error("Error showing report", e);
            model.addAttribute("error", "Error loading report: " + e.getMessage());
            return "error";
        }
    }
    
 // Helper method for severity priority
    private int getSeverityPriority(String severity) {
        if (severity == null) return 0;
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

 // Helper method for category priority
    private int getCategoryPriority(String category) {
        if (category == null) return 0;
        return switch (category.toLowerCase()) {
            case "performance" -> 3;
            case "quality" -> 2;
            case "best-practices" -> 1;
            default -> 0;
        };
    }

    // Helper method to check if issue has actionable fix
    private boolean hasActionableFix(Issue issue) {
        if (issue.getSuggestion() == null || 
            issue.getSuggestion().getImmediateFix() == null) {
            return false;
        }
        
        String searchCode = issue.getSuggestion().getImmediateFix().getSearchCode();
        String replaceCode = issue.getSuggestion().getImmediateFix().getReplaceCode();
        
        // Check for generic/dummy content
        if (searchCode == null || replaceCode == null ||
            searchCode.contains("Review the identified") ||
            searchCode.contains("Manual review required") ||
            replaceCode.contains("Apply appropriate") ||
            replaceCode.contains("TODO:") ||
            searchCode.equals(replaceCode) ||
            searchCode.length() < 10 ||
            replaceCode.length() < 10) {
            return false;
        }
        
        return true;
    }
}