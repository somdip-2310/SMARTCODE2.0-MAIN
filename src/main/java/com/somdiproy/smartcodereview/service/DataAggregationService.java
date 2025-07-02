package com.somdiproy.smartcodereview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.somdiproy.smartcodereview.model.Analysis;
import com.somdiproy.smartcodereview.model.AnalysisResult;
import com.somdiproy.smartcodereview.model.Issue;
import com.somdiproy.smartcodereview.model.Suggestion;
import com.somdiproy.smartcodereview.repository.AnalysisRepository;
import com.somdiproy.smartcodereview.repository.IssueDetailsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service to aggregate results from Lambda functions and store them properly
 */
@Slf4j
@Service
public class DataAggregationService {
    
    private final AnalysisRepository analysisRepository;
    private final IssueDetailsRepository issueDetailsRepository;
    private final ObjectMapper objectMapper;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LambdaInvokerService.class);

    
    // Temporary storage for Lambda results during processing
    private final Map<String, LambdaResults> lambdaResultsCache = new ConcurrentHashMap<>();
    
    @Autowired
    public DataAggregationService(AnalysisRepository analysisRepository,
                                 IssueDetailsRepository issueDetailsRepository,
                                 ObjectMapper objectMapper) {
        this.analysisRepository = analysisRepository;
        this.issueDetailsRepository = issueDetailsRepository;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Store screening results
     */
    public void storeScreeningResults(String analysisId, List<Map<String, Object>> screenedFiles) {
        getLambdaResults(analysisId).setScreenedFiles(screenedFiles);
        log.info("Stored {} screened files for analysis {}", screenedFiles.size(), analysisId);
    }
    
    /**
     * Store detection results
     */
    public void storeDetectionResults(String analysisId, List<Map<String, Object>> detectedIssues) {
        getLambdaResults(analysisId).setDetectedIssues(detectedIssues);
        log.info("Stored {} detected issues for analysis {}", detectedIssues.size(), analysisId);
    }
    
    /**
     * Store suggestion results from Lambda
     */
    public void storeSuggestionResults(String analysisId, String suggestionResponseJson) {
        try {
            Map<String, Object> response = objectMapper.readValue(suggestionResponseJson, Map.class);
            getLambdaResults(analysisId).setSuggestionResponse(response);
            log.info("Stored suggestion results for analysis {}", analysisId);
        } catch (Exception e) {
            log.error("Failed to parse suggestion response", e);
        }
    }
    
    /**
     * Aggregate all Lambda results and save to DynamoDB
     */
    public void aggregateAndSaveResults(Analysis analysis) {
        String analysisId = analysis.getAnalysisId();
        LambdaResults results = lambdaResultsCache.get(analysisId);
        
        if (results == null) {
            log.error("No Lambda results found for analysis {}", analysisId);
            return;
        }
        
        try {
            // Create and save AnalysisResult
            AnalysisResult analysisResult = createAnalysisResult(analysis, results);
            analysisRepository.save(analysisResult);
            
            // Create and save individual issues with suggestions
            List<Issue> issues = createIssuesWithSuggestions(analysisId, results);
            issueDetailsRepository.saveAll(issues);
            
            log.info("Successfully aggregated and saved {} issues for analysis {}", 
                    issues.size(), analysisId);
            
            // Clean up cache
            lambdaResultsCache.remove(analysisId);
            
        } catch (Exception e) {
            log.error("Failed to aggregate results for analysis {}", analysisId, e);
        }
    }
    
    /**
     * Create AnalysisResult from Lambda results
     */
    private AnalysisResult createAnalysisResult(Analysis analysis, LambdaResults results) {
        AnalysisResult result = new AnalysisResult();
        result.setAnalysisId(analysis.getAnalysisId());
        result.setSessionId(analysis.getSessionId());
        result.setScanNumber(analysis.getScanNumber());
        result.setStatus("completed");
        result.setRepository(analysis.getRepository());
        result.setBranch(analysis.getBranch());
        result.setStartedAt(analysis.getStartedAt());
        result.setCompletedAt(System.currentTimeMillis() / 1000);
        result.setProcessingTimeMs((result.getCompletedAt() - result.getStartedAt()) * 1000);
        
        // File counts
        result.setFilesSubmitted(analysis.getTotalFiles());
        result.setFilesAnalyzed(results.getScreenedFiles() != null ? results.getScreenedFiles().size() : 0);
        result.setFilesSkipped(result.getFilesSubmitted() - result.getFilesAnalyzed());
        
        // Create summary from detected issues
        AnalysisResult.Summary summary = createSummary(results.getDetectedIssues());
        result.setSummary(summary);
        
        // Calculate scores
        AnalysisResult.Scores scores = calculateScores(results.getDetectedIssues());
        result.setScores(scores);
        
        // Extract token usage and costs from suggestion response
        if (results.getSuggestionResponse() != null) {
            extractTokenUsageAndCosts(result, results.getSuggestionResponse());
        }
        
        // Set TTL (7 days)
        long ttl = (System.currentTimeMillis() / 1000) + (7 * 24 * 60 * 60);
        result.setTtl(ttl);
        result.setExpiresAt(ttl);
        
        return result;
    }
    
    /**
     * Create issues with suggestions from Lambda results
     */
    private List<Issue> createIssuesWithSuggestions(String analysisId, LambdaResults results) {
        List<Issue> issues = new ArrayList<>();
        
        // Map to store suggestions by issue ID
        Map<String, Map<String, Object>> suggestionsByIssueId = new HashMap<>();
        
        // Extract suggestions from response
        if (results.getSuggestionResponse() != null) {
            List<Map<String, Object>> suggestions = 
                (List<Map<String, Object>>) results.getSuggestionResponse().get("suggestions");
            if (suggestions != null) {
                for (Map<String, Object> suggestion : suggestions) {
                    String issueId = (String) suggestion.get("issueId");
                    if (issueId != null) {
                        suggestionsByIssueId.put(issueId, suggestion);
                    }
                }
            }
        }
        
        // Create Issue objects from detected issues
        if (results.getDetectedIssues() != null) {
            for (Map<String, Object> detectedIssue : results.getDetectedIssues()) {
                Issue issue = createIssue(analysisId, detectedIssue);
                
                // Add suggestion if available
                Map<String, Object> suggestionData = suggestionsByIssueId.get(issue.getIssueId());
                if (suggestionData != null) {
                    Suggestion suggestion = createSuggestion(suggestionData);
                    issue.setSuggestion(suggestion);
                }
                
                issues.add(issue);
            }
        }
        
        return issues;
    }
    
    /**
     * Create Issue from detected issue data
     */
    private Issue createIssue(String analysisId, Map<String, Object> issueData) {
        Issue issue = new Issue();
        issue.setAnalysisId(analysisId);
        issue.setIssueId(getStringValue(issueData, "id", UUID.randomUUID().toString()));
        issue.setType(getStringValue(issueData, "type"));
        issue.setTitle(getStringValue(issueData, "title"));
        issue.setDescription(getStringValue(issueData, "description"));
        issue.setSeverity(getStringValue(issueData, "severity", "MEDIUM"));
        issue.setCategory(getStringValue(issueData, "category", "GENERAL"));
        issue.setFile(getStringValue(issueData, "file"));
        issue.setLine(getIntValue(issueData, "line"));
        issue.setColumn(getIntValue(issueData, "column"));
        issue.setCode(getStringValue(issueData, "code"));
        issue.setLanguage(getStringValue(issueData, "language"));
        issue.setCwe(getStringValue(issueData, "cwe"));
        issue.setCvssScore(getDoubleValue(issueData, "cvssScore"));
        
        return issue;
    }
    
    /**
     * Create Suggestion from suggestion data
     */
    private Suggestion createSuggestion(Map<String, Object> suggestionData) {
        Suggestion suggestion = new Suggestion();
        
        // Immediate Fix
        Map<String, Object> immediateFix = (Map<String, Object>) suggestionData.get("immediateFix");
        if (immediateFix != null) {
            Suggestion.ImmediateFix fix = new Suggestion.ImmediateFix();
            fix.setTitle(getStringValue(immediateFix, "title"));
            fix.setSearchCode(getStringValue(immediateFix, "searchCode"));
            fix.setReplaceCode(getStringValue(immediateFix, "replaceCode"));
            fix.setExplanation(getStringValue(immediateFix, "explanation"));
            suggestion.setImmediateFix(fix);
        }
        
        // Best Practice
        Map<String, Object> bestPractice = (Map<String, Object>) suggestionData.get("bestPractice");
        if (bestPractice != null) {
            Suggestion.BestPractice practice = new Suggestion.BestPractice();
            practice.setTitle(getStringValue(bestPractice, "title"));
            practice.setCode(getStringValue(bestPractice, "code"));
            practice.setBenefits((List<String>) bestPractice.get("benefits"));
            suggestion.setBestPractice(practice);
        }
        
        // Testing
        Map<String, Object> testing = (Map<String, Object>) suggestionData.get("testing");
        if (testing != null) {
            Suggestion.Testing test = new Suggestion.Testing();
            test.setTestCase(getStringValue(testing, "testCase"));
            test.setValidationSteps((List<String>) testing.get("validationSteps"));
            suggestion.setTesting(test);
        }
        
        // Prevention
        Map<String, Object> prevention = (Map<String, Object>) suggestionData.get("prevention");
        if (prevention != null) {
            Suggestion.Prevention prev = new Suggestion.Prevention();
            
            // Handle tools list
            List<Map<String, Object>> tools = (List<Map<String, Object>>) prevention.get("tools");
            List<Suggestion.Tool> toolList = null;
            if (tools != null) {
                toolList = new ArrayList<>();
                for (Map<String, Object> t : tools) {
                    Suggestion.Tool tool = new Suggestion.Tool();
                    tool.setName(getStringValue(t, "name"));
                    tool.setDescription(getStringValue(t, "description"));
                    toolList.add(tool);
                }
            }
            
            prev.setGuidelines((List<String>) prevention.get("guidelines"));
            prev.setTools(toolList);
            prev.setCodeReviewChecklist((List<String>) prevention.get("codeReviewChecklist"));
            suggestion.setPrevention(prev);
        }
        
        return suggestion;
    }
    
    /**
     * Create summary from detected issues
     */
    private AnalysisResult.Summary createSummary(List<Map<String, Object>> issues) {
        AnalysisResult.Summary summary = new AnalysisResult.Summary();
        
        if (issues == null || issues.isEmpty()) {
            summary.setTotalIssues(0);
            summary.setBySeverity(new HashMap<>());
            summary.setByCategory(new HashMap<>());
            summary.setByType(new HashMap<>());
            return summary;
        }
        
        summary.setTotalIssues(issues.size());
        
        // Count by severity
        Map<String, Integer> bySeverity = new HashMap<>();
        Map<String, Integer> byCategory = new HashMap<>();
        Map<String, Integer> byType = new HashMap<>();
        
        for (Map<String, Object> issue : issues) {
            String severity = getStringValue(issue, "severity", "MEDIUM");
            String category = getStringValue(issue, "category", "GENERAL");
            String type = getStringValue(issue, "type", "UNKNOWN");
            
            bySeverity.merge(severity, 1, Integer::sum);
            byCategory.merge(category, 1, Integer::sum);
            byType.merge(type, 1, Integer::sum);
        }
        
        summary.setBySeverity(bySeverity);
        summary.setByCategory(byCategory);
        summary.setByType(byType);
        
        return summary;
    }
    
    /**
     * Calculate quality scores based on issues
     */
    private AnalysisResult.Scores calculateScores(List<Map<String, Object>> issues) {
        AnalysisResult.Scores scores = new AnalysisResult.Scores();
        
        if (issues == null || issues.isEmpty()) {
            scores.setSecurity(100.0);
            scores.setPerformance(100.0);
            scores.setQuality(100.0);
            scores.setOverall(100.0);
            return scores;
        }
        
        // Count issues by category and severity
        int securityIssues = 0;
        int performanceIssues = 0;
        int qualityIssues = 0;
        int criticalCount = 0;
        int highCount = 0;
        
        for (Map<String, Object> issue : issues) {
            String category = getStringValue(issue, "category", "GENERAL");
            String severity = getStringValue(issue, "severity", "MEDIUM");
            
            if ("SECURITY".equalsIgnoreCase(category)) securityIssues++;
            else if ("PERFORMANCE".equalsIgnoreCase(category)) performanceIssues++;
            else qualityIssues++;
            
            if ("CRITICAL".equalsIgnoreCase(severity)) criticalCount++;
            else if ("HIGH".equalsIgnoreCase(severity)) highCount++;
        }
        
        // Calculate scores (simple formula - can be improved)
        scores.setSecurity(Math.max(0, 100 - (securityIssues * 10) - (criticalCount * 20)));
        scores.setPerformance(Math.max(0, 100 - (performanceIssues * 8)));
        scores.setQuality(Math.max(0, 100 - (qualityIssues * 5) - (highCount * 10)));
        scores.setOverall((scores.getSecurity() + scores.getPerformance() + scores.getQuality()) / 3);
        
        return scores;
    }
    
    /**
     * Extract token usage and costs from suggestion response
     */
    private void extractTokenUsageAndCosts(AnalysisResult result, Map<String, Object> suggestionResponse) {
        Map<String, Object> summary = (Map<String, Object>) suggestionResponse.get("summary");
        if (summary != null) {
            // Token usage
            AnalysisResult.TokenUsage tokenUsage = new AnalysisResult.TokenUsage();
            tokenUsage.setSuggestionTokens(getIntValue(summary, "tokensUsed"));
            tokenUsage.setTotalTokens(tokenUsage.getSuggestionTokens());
            result.setTokenUsage(tokenUsage);
            
            // Costs
            AnalysisResult.Costs costs = new AnalysisResult.Costs();
            costs.setSuggestionCost(getDoubleValue(summary, "totalCost"));
            costs.setTotalCost(costs.getSuggestionCost());
            result.setCosts(costs);
        }
    }
    
    // Helper methods
    private LambdaResults getLambdaResults(String analysisId) {
        return lambdaResultsCache.computeIfAbsent(analysisId, k -> new LambdaResults());
    }
    
    private String getStringValue(Map<String, Object> map, String key) {
        return getStringValue(map, key, null);
    }
    
    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    private Integer getIntValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return value != null ? Integer.parseInt(value.toString()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return value != null ? Double.parseDouble(value.toString()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Inner class to hold Lambda results temporarily
     */
    private static class LambdaResults {
        private List<Map<String, Object>> screenedFiles;
        private List<Map<String, Object>> detectedIssues;
        private Map<String, Object> suggestionResponse;
        
        // Getters and setters
        public List<Map<String, Object>> getScreenedFiles() {
            return screenedFiles;
        }
        
        public void setScreenedFiles(List<Map<String, Object>> screenedFiles) {
            this.screenedFiles = screenedFiles;
        }
        
        public List<Map<String, Object>> getDetectedIssues() {
            return detectedIssues;
        }
        
        public void setDetectedIssues(List<Map<String, Object>> detectedIssues) {
            this.detectedIssues = detectedIssues;
        }
        
        public Map<String, Object> getSuggestionResponse() {
            return suggestionResponse;
        }
        
        public void setSuggestionResponse(Map<String, Object> suggestionResponse) {
            this.suggestionResponse = suggestionResponse;
        }
    }
}