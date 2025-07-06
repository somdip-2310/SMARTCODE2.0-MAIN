package com.somdiproy.smartcodereview.controller;

import com.somdiproy.smartcodereview.dto.ReportResponse;
import com.somdiproy.smartcodereview.service.LambdaInvokerService;
import com.somdiproy.smartcodereview.service.ReportService;
import com.somdiproy.smartcodereview.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import com.somdiproy.smartcodereview.model.Issue;
import com.somdiproy.smartcodereview.util.SeverityComparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/report")
public class ReportController {
    
    private final ReportService reportService;
    private final SessionService sessionService;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ReportController.class);
    @Autowired
    public ReportController(ReportService reportService, SessionService sessionService) {
        this.reportService = reportService;
        this.sessionService = sessionService;
    }
    
    @GetMapping("/{analysisId}")
    public String showReport(@PathVariable String analysisId,
                            @RequestParam String sessionId,
                            Model model) {
        // Validate session
        sessionService.getSession(sessionId);
        
        ReportResponse report = reportService.getReport(analysisId);
        
        if (report != null && report.getIssues() != null) {
            // Separate and sort issues by category
            List<Issue> allIssues = report.getIssues();
            
            // Separate security issues
            List<Issue> securityIssues = allIssues.stream()
                    .filter(issue -> "security".equalsIgnoreCase(issue.getCategory()))
                    .sorted(SeverityComparator.BY_SEVERITY_AND_CVE)
                    .collect(Collectors.toList());
            
            // Separate other issues (performance and quality)
            List<Issue> otherIssues = allIssues.stream()
                    .filter(issue -> !"security".equalsIgnoreCase(issue.getCategory()))
                    .sorted(SeverityComparator.BY_SEVERITY_DESC)
                    .collect(Collectors.toList());
            
            // Additional security issues without automated fixes
            List<Issue> additionalSecurityIssues = securityIssues.stream()
                    .filter(issue -> issue.getSuggestion() == null || 
                                   issue.getSuggestion().getImmediateFix() == null ||
                                   issue.getSuggestion().getImmediateFix().getSearchCode() == null ||
                                   issue.getSuggestion().getImmediateFix().getSearchCode().isEmpty() ||
                                   issue.getSuggestion().getImmediateFix().getSearchCode().contains("Review the identified") ||
                                   issue.getSuggestion().getImmediateFix().getSearchCode().contains("Manual review required"))
                    .collect(Collectors.toList());
            
            // Security issues with automated fixes
            securityIssues = securityIssues.stream()
                    .filter(issue -> issue.getSuggestion() != null && 
                                   issue.getSuggestion().getImmediateFix() != null &&
                                   issue.getSuggestion().getImmediateFix().getSearchCode() != null &&
                                   !issue.getSuggestion().getImmediateFix().getSearchCode().isEmpty() &&
                                   !issue.getSuggestion().getImmediateFix().getSearchCode().contains("Review the identified") &&
                                   !issue.getSuggestion().getImmediateFix().getSearchCode().contains("Manual review required"))
                    .collect(Collectors.toList());
            log.warn("📊 Report issues breakdown - Security: {}, Other: {}, Additional Security: {}",
                    securityIssues.size(), otherIssues.size(), additionalSecurityIssues.size());
            
            model.addAttribute("securityIssues", securityIssues);
            // Sort other issues by severity before adding to model
            otherIssues.sort(com.somdiproy.smartcodereview.util.SeverityComparator.BY_SEVERITY_DESC);
            model.addAttribute("otherIssues", otherIssues);
            model.addAttribute("additionalSecurityIssues", additionalSecurityIssues);
        }
        
        model.addAttribute("analysisId", analysisId);
        model.addAttribute("report", report);
        model.addAttribute("sessionId", sessionId);
        
        return "report";
    }
    
    @GetMapping("/api/download/{analysisId}")
    @ResponseBody
    public String downloadReport(@PathVariable String analysisId,
                                @RequestParam String sessionId) {
        // Validate session
        sessionService.getSession(sessionId);
        
        // TODO: Implement PDF generation
        return "PDF download not yet implemented";
    }
}