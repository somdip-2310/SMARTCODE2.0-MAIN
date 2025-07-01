package com.somdiproy.smartcodereview.controller;

import com.somdiproy.smartcodereview.dto.AnalysisRequest;
import com.somdiproy.smartcodereview.dto.AnalysisStatusResponse;
import com.somdiproy.smartcodereview.exception.ScanLimitExceededException;
import com.somdiproy.smartcodereview.exception.SessionNotFoundException;
import com.somdiproy.smartcodereview.model.Analysis;
import com.somdiproy.smartcodereview.model.Branch;
import com.somdiproy.smartcodereview.model.Repository;
import com.somdiproy.smartcodereview.model.Session;
import com.somdiproy.smartcodereview.service.AnalysisOrchestrator;
import com.somdiproy.smartcodereview.service.GitHubService;
import com.somdiproy.smartcodereview.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Slf4j
@Controller
public class AnalysisController {
	 private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthController.class);
    private final AnalysisOrchestrator analysisOrchestrator;
    private final SessionService sessionService;
    private final GitHubService gitHubService;
    
    @Autowired
    public AnalysisController(AnalysisOrchestrator analysisOrchestrator,
                             SessionService sessionService,
                             GitHubService gitHubService) {
        this.analysisOrchestrator = analysisOrchestrator;
        this.sessionService = sessionService;
        this.gitHubService = gitHubService;
    }
    
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
            GitHubService.FileAnalysisStats stats = gitHubService.getFileStats(repoUrl, repo.getDefaultBranch(), null);

            model.addAttribute("sessionId", sessionId);
            model.addAttribute("repository", repo);
            model.addAttribute("branches", branches);
            model.addAttribute("remainingScans", remainingScans);
            model.addAttribute("scanCount", session.getScanCount());
            model.addAttribute("fileStats", stats);
            model.addAttribute("publicOnly", true);

            log.info("📊 Repository analysis preview: {} eligible files, estimated cost: ${}", 
                     stats.getEligibleFiles(), String.format("%.4f", stats.getEstimatedCost()));

            return "branch-selection";
            
        } catch (Exception e) {
            log.error("Error fetching repository branches", e);
            
            // Check if it's a private repo access issue
         // Check if it's a private repo that we can't access
            if (e.getMessage().contains("Not Found")) {
                model.addAttribute("error", "Repository not found or is private. Please ensure the repository URL is correct and the repository is public.");
                return "error";
            }
            
            model.addAttribute("error", "Error accessing repository: " + e.getMessage());
            return "error";
        }
    }

	@PostMapping("/analyze")
	public String startAnalysis(@RequestParam String sessionId, @RequestParam String repoUrl,
			@RequestParam String branch, RedirectAttributes redirectAttributes) {
		try {
			Analysis result = analysisOrchestrator.analyzeRepository(repoUrl, branch, sessionId);
			return "redirect:/analysis/progress?analysisId=" + result.getAnalysisId() + "&sessionId=" + sessionId;
		} catch (ScanLimitExceededException e) {
			redirectAttributes.addFlashAttribute("error", "Maximum 3 scans per session reached");
			return "redirect:/error";
		}
	}

	@GetMapping("/analysis/progress")
	public String showProgress(@RequestParam String analysisId, @RequestParam String sessionId, Model model) {
		model.addAttribute("analysisId", analysisId);
		model.addAttribute("sessionId", sessionId);
		return "analysis-progress";
	}

	@GetMapping("/api/analysis/{analysisId}/status")
	@ResponseBody
	public AnalysisStatusResponse getStatus(@PathVariable String analysisId) {
		return analysisOrchestrator.getAnalysisStatus(analysisId);
	}
}