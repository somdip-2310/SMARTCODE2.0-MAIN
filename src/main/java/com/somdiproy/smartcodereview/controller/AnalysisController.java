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
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AnalysisController {
    
    @GetMapping("/repository")
    public String showRepositorySelect(@RequestParam String sessionId, Model model) {
        Session session = sessionService.getSession(sessionId);
        if (!session.getVerified()) {
            return "redirect:/";
        }
        
        model.addAttribute("sessionId", sessionId);
        return "repository-select";
    }

	private final AnalysisOrchestrator analysisOrchestrator;
	private final SessionService sessionService;
	private final GitHubService gitHubService;

	@GetMapping("/repository/branches")
	public String showBranches(@RequestParam String sessionId, @RequestParam String repoUrl, Model model) {
		Session session = sessionService.getSession(sessionId);
		if (!session.getVerified()) {
			return "redirect:/";
		}

		int remainingScans = 3 - session.getScanCount();
		if (remainingScans <= 0) {
			model.addAttribute("error", "You have reached the maximum number of scans for this session");
			return "error";
		}

		Repository repo = gitHubService.getRepository(repoUrl);
		List<Branch> branches = gitHubService.fetchBranches(repoUrl, session.getGithubToken());

		model.addAttribute("sessionId", sessionId);
		model.addAttribute("repository", repo);
		model.addAttribute("branches", branches);
		model.addAttribute("remainingScans", remainingScans);
		model.addAttribute("scanCount", session.getScanCount());

		return "branch-selection";
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