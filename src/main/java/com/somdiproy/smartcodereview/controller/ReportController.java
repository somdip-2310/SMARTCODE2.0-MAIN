package com.somdiproy.smartcodereview.controller;

import com.somdiproy.smartcodereview.dto.ReportResponse;
import com.somdiproy.smartcodereview.service.ReportService;
import com.somdiproy.smartcodereview.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Controller
@RequestMapping("/report")
public class ReportController {
    
    private final ReportService reportService;
    private final SessionService sessionService;
    
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