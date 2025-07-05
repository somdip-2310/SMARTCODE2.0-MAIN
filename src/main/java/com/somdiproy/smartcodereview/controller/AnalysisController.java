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
import com.somdiproy.smartcodereview.model.Suggestion.ImmediateFix;
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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    
    @GetMapping("/report/{analysisId}")
    public String showReport(@PathVariable String analysisId,
                            @RequestParam String sessionId,
                            Model model) {
        try {
            // Verify session with enhanced validation
            Session session = sessionService.getSession(sessionId);
            if (session == null) {
                log.warn("⚠️ Invalid session for report request: {}", sessionId);
                model.addAttribute("error", "Invalid session. Please log in again.");
                return "error";
            }
            
            // Get analysis with validation
            Analysis analysis = analysisOrchestrator.getAnalysis(analysisId);
            if (analysis == null) {
                log.warn("⚠️ Analysis not found: {}", analysisId);
                model.addAttribute("error", "Analysis not found.");
                return "error";
            }

            // Verify this analysis belongs to the session
            if (!analysis.getSessionId().equals(sessionId)) {
                log.warn("⚠️ Unauthorized access attempt to analysis {} by session {}", analysisId, sessionId);
                throw new SecurityException("Analysis does not belong to this session");
            }

            // Get comprehensive report using ReportService with fallback
            ReportResponse report = reportService.getReport(analysisId);
            if (report == null) {
                log.error("❌ Failed to generate report for analysis {}", analysisId);
                report = createEmptyReport(analysisId, analysis);
            }

            // Ensure all required fields are populated with null safety
            ensureReportDefaults(report, analysis);

         // Get all issues with null safety
            List<Issue> allIssues = report.getIssues() != null ? report.getIssues() : new ArrayList<>();
            
            // Ensure all issues have descriptions
            allIssues.forEach(issue -> {
                if (issue != null && (issue.getDescription() == null || issue.getDescription().isEmpty())) {
                    if (issue.getTitle() != null && !issue.getTitle().isEmpty()) {
                        issue.setDescription(issue.getTitle());
                    } else if (issue.getType() != null) {
                        issue.setDescription("Issue detected: " + issue.getType());
                    } else {
                        issue.setDescription("No description available");
                    }
                }
            });
            
            // Filter security issues for main security table (CVE issues with actionable fixes)
            List<Issue> securityIssues = allIssues.stream()
                .filter(issue -> issue != null && "security".equalsIgnoreCase(issue.getCategory()))
                .filter(this::isMainSecurityIssue) // Issues that belong in main security table
                .sorted(this::compareIssuesBySeverity)
                .collect(Collectors.toList());

            // Additional security issues for manual review (lower severity or no CVE data)
            List<Issue> additionalSecurityIssues = allIssues.stream()
                .filter(issue -> issue != null && "security".equalsIgnoreCase(issue.getCategory()))
                .filter(issue -> !isMainSecurityIssue(issue)) // Issues not in main security table
                .filter(issue -> issue.getTitle() != null || issue.getType() != null) // But with meaningful data
                .sorted(this::compareIssuesBySeverity)
                .collect(Collectors.toList());

            // Filter other issues to only those with actionable suggestions
            List<Issue> otherIssues = allIssues.stream()
                .filter(issue -> issue != null && !"security".equalsIgnoreCase(issue.getCategory()))
                .filter(this::hasActionableFix) // Only issues with real fixes
                .sorted((issue1, issue2) -> {
                    int categoryCompare = Integer.compare(
                        getCategoryPriority(issue2.getCategory()), 
                        getCategoryPriority(issue1.getCategory())
                    );
                    if (categoryCompare != 0) return categoryCompare;
                    
                    return compareIssuesBySeverity(issue1, issue2);
                })
                .collect(Collectors.toList());

            // Debug logging
            log.info("Total issues found: {}", allIssues.size());
            log.info("Security issues: {}", securityIssues.size());
            log.info("Additional security issues: {}", additionalSecurityIssues.size());
            log.info("Other issues: {}", otherIssues.size());

            // Log sample data for debugging
            if (!securityIssues.isEmpty()) {
                Issue sampleSecurity = securityIssues.get(0);
                log.info("Sample security issue: title={}, description={}, type={}, cveId={}, suggestion={}", 
                    sampleSecurity.getTitle(), sampleSecurity.getDescription(), sampleSecurity.getType(), 
                    sampleSecurity.getCveId(), sampleSecurity.getSuggestion() != null);
            }

            if (!otherIssues.isEmpty()) {
                Issue sampleOther = otherIssues.get(0);
                log.info("Sample other issue: title={}, type={}, severity={}, category={}, suggestion={}", 
                    sampleOther.getTitle(), sampleOther.getType(), sampleOther.getSeverity(),
                    sampleOther.getCategory(), sampleOther.getSuggestion() != null);
            }

            // Add all required attributes for the report template with null safety
            model.addAttribute("analysisId", analysisId);
            model.addAttribute("sessionId", sessionId);
            model.addAttribute("report", report);
            model.addAttribute("analysis", analysis);
            model.addAttribute("session", session);
            model.addAttribute("remainingScans", session.getRemainingScans() != null ? session.getRemainingScans() : 0);
            model.addAttribute("securityIssues", securityIssues);
            model.addAttribute("additionalSecurityIssues", additionalSecurityIssues);
            model.addAttribute("otherIssues", otherIssues);

            log.info("✅ Report displayed for analysis {} with {} total issues", 
                    analysisId, allIssues.size());

            return "report";
            
        } catch (SecurityException e) {
            log.warn("⚠️ Security violation in report access: {}", e.getMessage());
            model.addAttribute("error", "Access denied.");
            return "error";
        } catch (Exception e) {
            log.error("❌ Error showing report for analysis {}", analysisId, e);
            model.addAttribute("error", "Error loading report: " + e.getMessage());
            return "error";
        }
    }

    // Helper method to ensure report has all required defaults
    private void ensureReportDefaults(ReportResponse report, Analysis analysis) {
        if (report.getRepository() == null || report.getRepository().trim().isEmpty()) {
            report.setRepository(analysis.getRepository() != null ? analysis.getRepository() : "Unknown Repository");
        }
        if (report.getBranch() == null || report.getBranch().trim().isEmpty()) {
            report.setBranch(analysis.getBranch() != null ? analysis.getBranch() : "main");
        }
        if (report.getIssues() == null) {
            report.setIssues(new ArrayList<>());
        }
        if (report.getDate() == null) {
            // Use getStartedAt() instead of getStartTime()
            report.setDate(new Date(analysis.getStartedAt() != null ? analysis.getStartedAt() : System.currentTimeMillis()));
        }
        if (report.getScores() == null) {
            Map<String, Double> defaultScores = new HashMap<>();
            defaultScores.put("security", 50.0);
            defaultScores.put("performance", 50.0);
            defaultScores.put("quality", 50.0);
            defaultScores.put("overall", 50.0);
            report.setScores(defaultScores);
        }
        
        // Ensure counts are set with null safety
        if (report.getCriticalCount() == null) report.setCriticalCount(0);
        if (report.getHighCount() == null) report.setHighCount(0);
        if (report.getMediumCount() == null) report.setMediumCount(0);
        if (report.getLowCount() == null) report.setLowCount(0);
        
        // Initialize category counts if they don't exist
        if (report.getSecurityCount() == null) report.setSecurityCount(0);
        if (report.getPerformanceCount() == null) report.setPerformanceCount(0);
        if (report.getQualityCount() == null) report.setQualityCount(0);
        
        if (report.getTotalIssues() == null) report.setTotalIssues(report.getIssues().size());
        if (report.getFilesAnalyzed() == null) report.setFilesAnalyzed(0);
        if (report.getProcessingTime() == null) {
            // Use getStartedAt() and getCompletedAt() instead of getStartTime()/getEndTime()
            if (analysis.getStartedAt() != null && analysis.getCompletedAt() != null) {
                report.setProcessingTime(analysis.getCompletedAt() - analysis.getStartedAt());
            } else {
                report.setProcessingTime(0L);
            }
        }
    }

    // Helper method to create empty report for error cases
    private ReportResponse createEmptyReport(String analysisId, Analysis analysis) {
        ReportResponse report = new ReportResponse();
        report.setAnalysisId(analysisId);
        report.setRepository(analysis.getRepository() != null ? analysis.getRepository() : "Unknown Repository");
        report.setBranch(analysis.getBranch() != null ? analysis.getBranch() : "main");
        report.setDate(new Date());
        report.setIssues(new ArrayList<>());
        report.setCriticalCount(0);
        report.setHighCount(0);
        report.setMediumCount(0);
        report.setLowCount(0);
        report.setSecurityCount(0);
        report.setPerformanceCount(0);
        report.setQualityCount(0);
        report.setTotalIssues(0);
        report.setFilesAnalyzed(0);
        report.setProcessingTime(0L);
        
        Map<String, Double> scores = new HashMap<>();
        scores.put("overall", 100.0);
        scores.put("security", 100.0);
        scores.put("performance", 100.0);
        scores.put("quality", 100.0);
        report.setScores(scores);
        
        return report;
    }

    // Enhanced comparison method with null safety
    private int compareIssuesBySeverity(Issue issue1, Issue issue2) {
        if (issue1 == null && issue2 == null) return 0;
        if (issue1 == null) return 1;
        if (issue2 == null) return -1;
        
        int severityCompare = Integer.compare(
            getSeverityPriority(issue2.getSeverity()), 
            getSeverityPriority(issue1.getSeverity())
        );
        
        if (severityCompare != 0) return severityCompare;
        
        // Secondary sort by file name
        String file1 = issue1.getFile();
        String file2 = issue2.getFile();
        
        if (file1 == null && file2 == null) return 0;
        if (file1 == null) return 1;
        if (file2 == null) return -1;
        
        return file1.compareTo(file2);
    }

    // Enhanced getSeverityPriority with null safety
    private int getSeverityPriority(String severity) {
        if (severity == null) return 0;
        switch (severity.toUpperCase()) {
            case "CRITICAL": return 4;
            case "HIGH": return 3;
            case "MEDIUM": return 2;
            case "LOW": return 1;
            default: return 0;
        }
    }

    // Enhanced getCategoryPriority with null safety
    private int getCategoryPriority(String category) {
        if (category == null) return 0;
        switch (category.toLowerCase()) {
            case "security": return 4;
            case "performance": return 3;
            case "quality": return 2;
            default: return 1;
        }
    }

    // Enhanced isMainSecurityIssue with comprehensive null safety
    private boolean isMainSecurityIssue(Issue issue) {
        if (issue == null) return false;
        
        return issue.getSuggestion() != null && 
               issue.getSuggestion().getImmediateFix() != null &&
               issue.getSuggestion().getImmediateFix().getSearchCode() != null &&
               !issue.getSuggestion().getImmediateFix().getSearchCode().trim().isEmpty() &&
               !issue.getSuggestion().getImmediateFix().getSearchCode().contains("Review the identified") &&
               !issue.getSuggestion().getImmediateFix().getSearchCode().contains("Manual review required") &&
               issue.getSuggestion().getImmediateFix().getReplaceCode() != null &&
               !issue.getSuggestion().getImmediateFix().getReplaceCode().trim().isEmpty();
    }

    // Enhanced hasActionableFix with comprehensive null safety
    private boolean hasActionableFix(Issue issue) {
        if (issue == null) return false;
        
        // Must have a suggestion with immediate fix
        if (issue.getSuggestion() == null || issue.getSuggestion().getImmediateFix() == null) {
            return false;
        }
        
        ImmediateFix fix = issue.getSuggestion().getImmediateFix();
        
        // Must have both search and replace code that are meaningful
        boolean hasSearchCode = fix.getSearchCode() != null && 
                               !fix.getSearchCode().trim().isEmpty() &&
                               !fix.getSearchCode().contains("Review the identified") &&
                               !fix.getSearchCode().contains("Manual review required");
        
        boolean hasReplaceCode = fix.getReplaceCode() != null && 
                                !fix.getReplaceCode().trim().isEmpty() &&
                                !fix.getReplaceCode().contains("Manual review required") &&
                                !fix.getReplaceCode().contains("Apply appropriate security measures");
        
        return hasSearchCode && hasReplaceCode;
    }

}