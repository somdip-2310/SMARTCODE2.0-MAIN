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
    /**
     * Create AnalysisResult from Lambda results with enhanced validation
     */
    private AnalysisResult createAnalysisResult(Analysis analysis, LambdaResults results) {
        AnalysisResult result = new AnalysisResult();
        
        // Required fields with null checks and defaults
        result.setAnalysisId(analysis.getAnalysisId());
        result.setSessionId(analysis.getSessionId() != null ? analysis.getSessionId() : "unknown-session");
        result.setScanNumber(analysis.getScanNumber() != null ? analysis.getScanNumber() : 1);
        result.setStatus("completed");
        
        // Repository and branch with validation
        String repository = analysis.getRepository();
        if (repository == null || repository.trim().isEmpty()) {
            repository = "Unknown Repository";
            log.warn("⚠️ Repository is null for analysis {}, using default", analysis.getAnalysisId());
        }
        result.setRepository(repository);
        
        String branch = analysis.getBranch();
        if (branch == null || branch.trim().isEmpty()) {
            branch = "main";
            log.warn("⚠️ Branch is null for analysis {}, using default", analysis.getAnalysisId());
        }
        result.setBranch(branch);
        
        // Timestamps with validation
        Long startedAt = analysis.getStartedAt();
        if (startedAt == null || startedAt <= 0) {
            startedAt = System.currentTimeMillis() / 1000;
            log.warn("⚠️ StartedAt is null/invalid for analysis {}, using current time", analysis.getAnalysisId());
        }
        result.setStartedAt(startedAt);
        
        long completedAt = System.currentTimeMillis() / 1000;
        result.setCompletedAt(completedAt);
        
        // Calculate processing time with validation
        long processingTimeMs = (completedAt - startedAt) * 1000;
        if (processingTimeMs < 0) {
            processingTimeMs = 0;
            log.warn("⚠️ Negative processing time calculated for analysis {}, setting to 0", analysis.getAnalysisId());
        }
        result.setProcessingTimeMs(processingTimeMs);
        
        // File counts with validation
        Integer totalFiles = analysis.getTotalFiles();
        if (totalFiles == null || totalFiles < 0) {
            totalFiles = 0;
            log.warn("⚠️ TotalFiles is null/negative for analysis {}, setting to 0", analysis.getAnalysisId());
        }
        result.setFilesSubmitted(totalFiles);
        
        int filesAnalyzed = 0;
        if (results.getScreenedFiles() != null) {
            filesAnalyzed = results.getScreenedFiles().size();
        }
        result.setFilesAnalyzed(filesAnalyzed);
        
        int filesSkipped = Math.max(0, totalFiles - filesAnalyzed);
        result.setFilesSkipped(filesSkipped);
        
        // Create summary with null check
        try {
            AnalysisResult.Summary summary = createSummary(results.getDetectedIssues());
            if (summary == null) {
                summary = createEmptySummary();
                log.warn("⚠️ Summary creation failed for analysis {}, using empty summary", analysis.getAnalysisId());
            }
            result.setSummary(summary);
        } catch (Exception e) {
            log.error("❌ Failed to create summary for analysis {}: {}", analysis.getAnalysisId(), e.getMessage());
            result.setSummary(createEmptySummary());
        }
        
        // Calculate scores with null check
        try {
            AnalysisResult.Scores scores = calculateScores(results.getDetectedIssues());
            if (scores == null) {
                scores = createDefaultScores();
                log.warn("⚠️ Scores calculation failed for analysis {}, using defaults", analysis.getAnalysisId());
            }
            result.setScores(scores);
        } catch (Exception e) {
            log.error("❌ Failed to calculate scores for analysis {}: {}", analysis.getAnalysisId(), e.getMessage());
            result.setScores(createDefaultScores());
        }
        
        // Extract token usage and costs with error handling
        try {
            if (results.getSuggestionResponse() != null) {
                extractTokenUsageAndCosts(result, results.getSuggestionResponse());
            } else {
                // Set default token usage if no suggestion response
                result.setTokenUsage(createDefaultTokenUsage());
                result.setCosts(createDefaultCosts());
                log.debug("No suggestion response for analysis {}, using default token/cost data", analysis.getAnalysisId());
            }
        } catch (Exception e) {
            log.error("❌ Failed to extract token usage/costs for analysis {}: {}", analysis.getAnalysisId(), e.getMessage());
            result.setTokenUsage(createDefaultTokenUsage());
            result.setCosts(createDefaultCosts());
        }
        
        // Set TTL (7 days) with validation
        long currentTime = System.currentTimeMillis() / 1000;
        long ttl = currentTime + (7 * 24 * 60 * 60); // 7 days from now
        
        // Ensure TTL is in the future
        if (ttl <= currentTime) {
            ttl = currentTime + (24 * 60 * 60); // Fallback to 1 day
            log.warn("⚠️ TTL calculation issue for analysis {}, using 1 day fallback", analysis.getAnalysisId());
        }
        
        result.setTtl(ttl);
        result.setExpiresAt(ttl);
        
        // Final validation
        if (!validateAnalysisResult(result)) {
            log.error("❌ AnalysisResult validation failed for analysis {}", analysis.getAnalysisId());
            throw new IllegalStateException("AnalysisResult validation failed for analysis: " + analysis.getAnalysisId());
        }
        
        log.debug("✅ Created valid AnalysisResult for analysis {}", analysis.getAnalysisId());
        return result;
    }

    /**
     * Create empty summary for fallback
     */
    private AnalysisResult.Summary createEmptySummary() {
        AnalysisResult.Summary summary = new AnalysisResult.Summary();
        summary.setTotalIssues(0);
        summary.setBySeverity(new HashMap<>());
        summary.setByCategory(new HashMap<>());
        summary.setByType(new HashMap<>());
        return summary;
    }

    /**
     * Create default scores for fallback
     */
    private AnalysisResult.Scores createDefaultScores() {
        AnalysisResult.Scores scores = new AnalysisResult.Scores();
        scores.setSecurity(10.0);
        scores.setPerformance(10.0);
        scores.setQuality(10.0);
        scores.setOverall(10.0);
        return scores;
    }

    /**
     * Create default token usage for fallback
     */
    private AnalysisResult.TokenUsage createDefaultTokenUsage() {
        AnalysisResult.TokenUsage tokenUsage = new AnalysisResult.TokenUsage();
        tokenUsage.setScreeningTokens(0);
        tokenUsage.setDetectionTokens(0);
        tokenUsage.setSuggestionTokens(0);
        tokenUsage.setTotalTokens(0);
        return tokenUsage;
    }

    /**
     * Create default costs for fallback
     */
    private AnalysisResult.Costs createDefaultCosts() {
        AnalysisResult.Costs costs = new AnalysisResult.Costs();
        costs.setScreeningCost(0.0);
        costs.setDetectionCost(0.0);
        costs.setSuggestionCost(0.0);
        costs.setTotalCost(0.0);
        return costs;
    }

    /**
     * Validate AnalysisResult before saving to DynamoDB
     */
    private boolean validateAnalysisResult(AnalysisResult result) {
        if (result == null) {
            log.error("AnalysisResult is null");
            return false;
        }
        
        if (result.getAnalysisId() == null || result.getAnalysisId().trim().isEmpty()) {
            log.error("AnalysisId is null or empty");
            return false;
        }
        
        if (result.getSessionId() == null || result.getSessionId().trim().isEmpty()) {
            log.error("SessionId is null or empty");
            return false;
        }
        
        if (result.getRepository() == null || result.getRepository().trim().isEmpty()) {
            log.error("Repository is null or empty");
            return false;
        }
        
        if (result.getBranch() == null || result.getBranch().trim().isEmpty()) {
            log.error("Branch is null or empty");
            return false;
        }
        
        if (result.getStatus() == null || result.getStatus().trim().isEmpty()) {
            log.error("Status is null or empty");
            return false;
        }
        
        if (result.getStartedAt() == null || result.getStartedAt() <= 0) {
            log.error("StartedAt is null or invalid: {}", result.getStartedAt());
            return false;
        }
        
        if (result.getCompletedAt() == null || result.getCompletedAt() <= 0) {
            log.error("CompletedAt is null or invalid: {}", result.getCompletedAt());
            return false;
        }
        
        if (result.getTtl() == null || result.getTtl() <= 0) {
            log.error("TTL is null or invalid: {}", result.getTtl());
            return false;
        }
        
        return true;
    }
    
    /**
     * Create issues with suggestions from Lambda results
     */
    private List<Issue> createIssuesWithSuggestions(String analysisId, LambdaResults results) {
        List<Issue> issues = new ArrayList<>();
        
        // Map to store suggestions by issue ID
        Map<String, Map<String, Object>> suggestionsByIssueId = new HashMap<>();
        
     
        // Extract suggestions from response with enhanced parsing
        if (results.getSuggestionResponse() != null) {
            List<Map<String, Object>> suggestions = extractSuggestionsFromResponse(results.getSuggestionResponse());
            if (suggestions != null) {
                for (Map<String, Object> suggestion : suggestions) {
                    String issueId = (String) suggestion.get("issueId");
                    if (issueId != null) {
                        suggestionsByIssueId.put(issueId, suggestion);
                    }
                }
            }
        }

        // FALLBACK: If no suggestions found in response, try to fetch from DynamoDB directly
        if (suggestionsByIssueId.isEmpty() && results.getDetectedIssues() != null) {
            log.info("No suggestions found in response, attempting to fetch from DynamoDB for analysis {}", analysisId);
            suggestionsByIssueId = fetchSuggestionsFromDynamoDB(analysisId, results.getDetectedIssues());
        }
        
        // Create Issue objects from detected issues
        if (results.getDetectedIssues() != null) {
            for (Map<String, Object> detectedIssue : results.getDetectedIssues()) {
                Issue issue = createIssue(analysisId, detectedIssue);
                
                // Add suggestion if available
             // Add suggestion if available - check multiple possible ID formats
                String issueId = issue.getIssueId();
                Map<String, Object> suggestionData = suggestionsByIssueId.get(issueId);
                
                // Try alternative ID matching if direct match fails
                if (suggestionData == null) {
                    String alternativeId = issue.getType() + "_" + issue.getFile() + "_" + issue.getLine();
                    suggestionData = suggestionsByIssueId.get(alternativeId);
                }
                
                // Try issue type matching for security issues
                if (suggestionData == null && "security".equals(issue.getCategory())) {
                    for (Map.Entry<String, Map<String, Object>> entry : suggestionsByIssueId.entrySet()) {
                        Map<String, Object> data = entry.getValue();
                        if (issue.getType().equals(data.get("issueType")) || 
                            issue.getFile().equals(data.get("file"))) {
                            suggestionData = data;
                            break;
                        }
                    }
                }
                
                if (suggestionData != null) {
                    Suggestion suggestion = createSuggestion(suggestionData);
                    issue.setSuggestion(suggestion);
                    log.debug("✅ Linked suggestion to issue: {}", issueId);
                } else {
                    log.warn("⚠️ No suggestion found for issue: {} (type: {}, file: {})", 
                        issueId, issue.getType(), issue.getFile());
                }
                
                issues.add(issue);
            }
        }
        
        return issues;
    }
    /**
     * Extract suggestions from Lambda response with multiple format support
     */
    private List<Map<String, Object>> extractSuggestionsFromResponse(Map<String, Object> response) {
        // Try multiple possible paths for suggestions
        List<Map<String, Object>> suggestions = null;
        
        // Path 1: Direct suggestions array
        if (response.get("suggestions") instanceof List) {
            suggestions = (List<Map<String, Object>>) response.get("suggestions");
        }
        
        // Path 2: Nested in summary
        if (suggestions == null && response.get("summary") != null) {
            Map<String, Object> summary = (Map<String, Object>) response.get("summary");
            if (summary.get("suggestions") instanceof List) {
                suggestions = (List<Map<String, Object>>) summary.get("suggestions");
            }
        }
        
        // Path 3: Nested in data
        if (suggestions == null && response.get("data") != null) {
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data.get("suggestions") instanceof List) {
                suggestions = (List<Map<String, Object>>) data.get("suggestions");
            }
        }
        
        return suggestions != null ? suggestions : new ArrayList<>();
    }
    /**
     * Fallback method to fetch suggestions directly from DynamoDB
     */
    private Map<String, Map<String, Object>> fetchSuggestionsFromDynamoDB(String analysisId, List<Map<String, Object>> detectedIssues) {
        Map<String, Map<String, Object>> suggestionsByIssueId = new HashMap<>();
        
        try {
            // Try to query issue-details table for existing suggestions
            for (Map<String, Object> issue : detectedIssues) {
                String issueId = (String) issue.get("id");
                if (issueId != null) {
                    // Check if this issue has a suggestion stored
                    Optional<Issue> existingIssue = issueDetailsRepository.findByAnalysisIdAndIssueId(analysisId, issueId);
                    if (existingIssue.isPresent() && existingIssue.get().getSuggestion() != null) {
                        // Convert existing suggestion to the format expected by createIssuesWithSuggestions
                        Map<String, Object> suggestionData = convertSuggestionToMap(existingIssue.get().getSuggestion());
                        suggestionData.put("issueId", issueId);
                        suggestionsByIssueId.put(issueId, suggestionData);
                        log.debug("Found existing suggestion for issue: {}", issueId);
                    }
                }
            }
            
            log.info("Retrieved {} existing suggestions from DynamoDB for analysis {}", 
                    suggestionsByIssueId.size(), analysisId);
            
        } catch (Exception e) {
            log.warn("Failed to fetch suggestions from DynamoDB for analysis {}: {}", analysisId, e.getMessage());
        }
        
        return suggestionsByIssueId;
    }

    /**
     * Convert Suggestion object to Map format for processing
     */
    private Map<String, Object> convertSuggestionToMap(Suggestion suggestion) {
        Map<String, Object> suggestionMap = new HashMap<>();
        
        if (suggestion.getImmediateFix() != null) {
            Map<String, Object> immediateFix = new HashMap<>();
            immediateFix.put("title", suggestion.getImmediateFix().getTitle());
            immediateFix.put("searchCode", suggestion.getImmediateFix().getSearchCode());
            immediateFix.put("replaceCode", suggestion.getImmediateFix().getReplaceCode());
            immediateFix.put("explanation", suggestion.getImmediateFix().getExplanation());
            suggestionMap.put("immediateFix", immediateFix);
        }
        
        return suggestionMap;
    }
    
    /**
     * Create Issue from detected issue data
     */
    private Issue createIssue(String analysisId, Map<String, Object> issueData) {
        Issue issue = new Issue();
        issue.setAnalysisId(analysisId);
        issue.setIssueId(getStringValue(issueData, "id", UUID.randomUUID().toString()));
        issue.setType(getStringValue(issueData, "type"));
        // Set title with fallback to type if title is null
     // Set title with enhanced fallback logic
        String title = getStringValue(issueData, "title");
        if (title == null || title.isEmpty()) {
            title = generateHumanReadableTitle(issueData);
        }
        issue.setTitle(title);
        issue.setDescription(getStringValue(issueData, "description"));
        issue.setSeverity(getStringValue(issueData, "severity", "MEDIUM"));
        issue.setCategory(getStringValue(issueData, "category", "GENERAL"));
        issue.setFile(getStringValue(issueData, "file"));
        // Enhanced line number extraction
        Integer lineNumber = extractLineNumber(issueData);
        issue.setLine(lineNumber != null ? lineNumber : 1); // Default to 1 instead of 0
        issue.setColumn(getIntValue(issueData, "column"));
        issue.setCode(getStringValue(issueData, "code"));
        issue.setLanguage(getStringValue(issueData, "language"));
        issue.setCwe(getStringValue(issueData, "cwe"));
        issue.setCvssScore(getDoubleValue(issueData, "cvssScore"));
        
        // Extract CVE information if available
        issue.setCveId(getStringValue(issueData, "cveId"));
        issue.setCveScore(getDoubleValue(issueData, "cveScore"));
        
        return issue;
    }
    /**
     * Extract line number from various possible formats
     */
    private Integer extractLineNumber(Map<String, Object> issueData) {
        // Try multiple possible keys for line number
        String[] lineKeys = {"line", "lineNumber", "startLine", "line_number", "lineNum"};
        
        for (String key : lineKeys) {
            Object lineValue = issueData.get(key);
            if (lineValue != null) {
                try {
                    if (lineValue instanceof Number) {
                        int line = ((Number) lineValue).intValue();
                        return line > 0 ? line : 1; // Ensure positive line numbers
                    } else {
                        int line = Integer.parseInt(lineValue.toString());
                        return line > 0 ? line : 1;
                    }
                } catch (NumberFormatException e) {
                    // Continue to next key
                }
            }
        }
        
        // Try to extract from location string if available
        String location = getStringValue(issueData, "location");
        if (location != null && location.contains(":")) {
            try {
                String lineStr = location.substring(location.lastIndexOf(":") + 1);
                int line = Integer.parseInt(lineStr.trim());
                return line > 0 ? line : 1;
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }
        
        // Try to extract from file path with line number (format: file.java:123)
        String filePath = getStringValue(issueData, "file");
        if (filePath != null && filePath.contains(":")) {
            try {
                String lineStr = filePath.substring(filePath.lastIndexOf(":") + 1);
                int line = Integer.parseInt(lineStr.trim());
                return line > 0 ? line : 1;
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }
        
        // Try to extract from code snippet context if available
        String codeSnippet = getStringValue(issueData, "codeSnippet");
        if (codeSnippet != null && codeSnippet.contains("line")) {
            try {
                // Look for patterns like "line 45" or "Line: 123"
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("line[\\s:]+([0-9]+)", java.util.regex.Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher matcher = pattern.matcher(codeSnippet);
                if (matcher.find()) {
                    int line = Integer.parseInt(matcher.group(1));
                    return line > 0 ? line : 1;
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }
        
        log.debug("⚠️ Could not extract line number from issue data: {}", issueData.keySet());
        return 1; // Default to line 1 instead of null/0
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
     * Generate human-readable title from issue data
     */
    private String generateHumanReadableTitle(Map<String, Object> issueData) {
        String type = getStringValue(issueData, "type", "Unknown Issue");
        String severity = getStringValue(issueData, "severity", "");
        String category = getStringValue(issueData, "category", "");
        
        // Handle specific issue types with better naming
        String humanTitle = switch (type.toUpperCase()) {
            case "SQL_INJECTION" -> "SQL Injection Vulnerability";
            case "XSS", "CROSS_SITE_SCRIPTING" -> "Cross-Site Scripting (XSS)";
            case "HARDCODED_CREDENTIALS" -> "Hardcoded Credentials";
            case "INSECURE_DESERIALIZATION" -> "Insecure Deserialization";
            case "INEFFICIENT_LOOP" -> "Inefficient Loop";
            case "MEMORY_LEAK" -> "Memory Leak";
            case "BLOCKING_IO" -> "Blocking I/O Operation";
            case "RESOURCE_LEAK" -> "Resource Leak";
            case "MISSING_ERROR_HANDLING" -> "Missing Error Handling";
            case "INEFFICIENT_DATABASE_QUERY" -> "Inefficient Database Query";
            case "MISSING_CACHE" -> "Missing Cache";
            case "POTENTIAL_MEMORY_LEAK" -> "Potential Memory Leak";
            default -> {
                // Generic cleanup for other types
                String cleaned = type.replaceAll("_", " ").toLowerCase();
                yield cleaned.substring(0, 1).toUpperCase() + cleaned.substring(1);
            }
        };
        
        // Add severity qualifier for critical issues
        if ("CRITICAL".equalsIgnoreCase(severity)) {
            humanTitle = "Critical: " + humanTitle;
        } else if ("HIGH".equalsIgnoreCase(severity) && "SECURITY".equalsIgnoreCase(category)) {
            humanTitle = "High Risk: " + humanTitle;
        }
        
        return humanTitle;
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
            scores.setSecurity(10.0);
            scores.setPerformance(10.0);
            scores.setQuality(10.0);
            scores.setOverall(10.0);
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
			else if ("HIGH".equalsIgnoreCase(severity))
				highCount++;
		}

		// Calculate scores (10 = perfect, 0 = worst)
		// Use logarithmic scale to handle high issue counts better
		double securityScore = 10.0 - Math.min(10.0, Math.log10(securityIssues + 1) * 2.5 + (criticalCount * 0.5));
		double performanceScore = 10.0 - Math.min(10.0, Math.log10(performanceIssues + 1) * 2.0);
		double qualityScore = 10.0 - Math.min(10.0, Math.log10(qualityIssues + 1) * 1.5 + (highCount * 0.3));

		scores.setSecurity(Math.max(0.0, Math.min(10.0, securityScore)));
		scores.setPerformance(Math.max(0.0, Math.min(10.0, performanceScore)));
		scores.setQuality(Math.max(0.0, Math.min(10.0, qualityScore)));
        
        // Overall score is weighted average
        double overallScore = (scores.getSecurity() * 0.5 + scores.getPerformance() * 0.3 + scores.getQuality() * 0.2);
        scores.setOverall(Math.max(0.0, Math.min(10.0, overallScore)));
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
            int intValue = ((Number) value).intValue();
            return intValue > 0 ? intValue : null; // Don't return 0 or negative values
        }
        try {
            if (value != null) {
                int intValue = Integer.parseInt(value.toString());
                return intValue > 0 ? intValue : null; // Don't return 0 or negative values
            }
            return null;
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
    /**
     * Get analysis progress from DynamoDB
     */
    public Map<String, Object> getAnalysisProgress(String analysisId) {
        try {
            log.debug("🔍 Checking analysis progress for: {}", analysisId);
            
            // Query the analysis repository for the current analysis
            Optional<AnalysisResult> analysisResultOpt = analysisRepository.findById(analysisId);
            
            if (analysisResultOpt.isEmpty()) {
                log.debug("❌ No analysis found for ID: {}", analysisId);
                return null;
            }

            AnalysisResult analysisResult = analysisResultOpt.get();
            
            if (analysisResult != null) {
                Map<String, Object> progress = new HashMap<>();
                progress.put("analysisId", analysisId);
                progress.put("status", analysisResult.getStatus());
                progress.put("progress", calculateProgressPercentage(analysisResult));
                progress.put("completedAt", analysisResult.getCompletedAt());
                progress.put("startedAt", analysisResult.getStartedAt());
                
                // Add detailed status information
                if ("completed".equals(analysisResult.getStatus())) {
                    progress.put("suggestions_complete", true);
                    progress.put("totalIssues", analysisResult.getSummary() != null ? 
                                analysisResult.getSummary().getTotalIssues() : 0);
                }
                
                log.debug("✅ Found analysis progress: {} - {}", analysisId, analysisResult.getStatus());
                return progress;
            }
            
            // If not found in main results, check if it's still in progress
            // This part depends on how you track in-progress analyses
            Map<String, Object> inProgressStatus = checkInProgressAnalysis(analysisId);
            if (inProgressStatus != null) {
                log.debug("📊 Analysis {} is still in progress", analysisId);
                return inProgressStatus;
            }
            
            log.debug("❌ No analysis progress found for: {}", analysisId);
            return null;
            
        } catch (Exception e) {
            log.error("❌ Failed to get analysis progress for {}: {}", analysisId, e.getMessage());
            return null;
        }
    }

    /**
     * Check if analysis is still in progress (you may need to adjust this based on your tracking)
     */
    private Map<String, Object> checkInProgressAnalysis(String analysisId) {
        try {
            // Check if we have Lambda results in cache (indicates processing)
            LambdaResults lambdaResults = lambdaResultsCache.get(analysisId);
            if (lambdaResults != null) {
                Map<String, Object> progress = new HashMap<>();
                progress.put("analysisId", analysisId);
                progress.put("status", "in_progress");
                progress.put("progress", 50); // Estimate based on what's completed
                
                // Check what stages are complete
                if (lambdaResults.getScreenedFiles() != null) {
                    progress.put("screening_complete", true);
                    progress.put("progress", 70);
                }
                if (lambdaResults.getDetectedIssues() != null) {
                    progress.put("detection_complete", true);
                    progress.put("progress", 85);
                }
                if (lambdaResults.getSuggestionResponse() != null) {
                    progress.put("suggestions_complete", true);
                    progress.put("progress", 100);
                    progress.put("status", "suggestions_complete");
                }
                
                return progress;
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("❌ Error checking in-progress analysis {}: {}", analysisId, e.getMessage());
            return null;
        }
    }

    /**
     * Calculate progress percentage based on analysis result
     */
    private int calculateProgressPercentage(AnalysisResult analysisResult) {
        if (analysisResult == null) return 0;
        
        String status = analysisResult.getStatus();
        if (status == null) return 0;
        
        switch (status.toLowerCase()) {
            case "started":
            case "screening":
                return 25;
            case "detection":
                return 50;
            case "suggestions":
                return 75;
            case "completed":
            case "suggestions_complete":
                return 100;
            case "failed":
            case "error":
                return -1; // Indicates failure
            default:
                return 10; // Default for unknown status
        }
    }

    /**
     * Update analysis progress (helper method for Lambda functions to update status)
     */
    public void updateAnalysisProgress(String analysisId, String status, Object data) {
        try {
            log.info("📊 Updating analysis progress: {} -> {}", analysisId, status);
            
            // For in-progress updates, we can store in cache or a separate tracking mechanism
            // This depends on your specific needs
            
            if ("suggestions_complete".equals(status)) {
                // Mark as complete in cache if it exists
                LambdaResults results = lambdaResultsCache.get(analysisId);
                if (results != null) {
                    // Add completion marker
                    Map<String, Object> completionData = new HashMap<>();
                    completionData.put("completed", true);
                    completionData.put("timestamp", System.currentTimeMillis());
                    completionData.put("data", data);
                    
                    // Store completion data
                    if (results.getSuggestionResponse() == null) {
                        results.setSuggestionResponse(completionData);
                    }
                }
            }
            
            log.debug("✅ Analysis progress updated: {} -> {}", analysisId, status);
            
        } catch (Exception e) {
            log.error("❌ Failed to update analysis progress for {}: {}", analysisId, e.getMessage());
        }
    }
}