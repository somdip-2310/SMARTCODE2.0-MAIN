package com.somdiproy.smartcodereview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.somdiproy.smartcodereview.service.GitHubService.GitHubFile;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.springframework.scheduling.annotation.Async;
import com.somdiproy.smartcodereview.model.Analysis;
import com.somdiproy.smartcodereview.model.AnalysisResult;
import com.somdiproy.smartcodereview.model.Issue;
import com.somdiproy.smartcodereview.dto.ReportResponse;
import com.somdiproy.smartcodereview.repository.AnalysisRepository;
import com.somdiproy.smartcodereview.repository.IssueDetailsRepository;
import java.util.Date;
@Slf4j
@Service
public class LambdaInvokerService {

	private final LambdaClient lambdaClient;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LambdaInvokerService.class);

	// Configuration constants
	private static final int DETECTION_BATCH_SIZE = 1; // Reduced to 1 for rate limiting
	private static final int SUGGESTIONS_BATCH_SIZE = 1; // Process one issue at a time
	private static final int MAX_PAYLOAD_SIZE = 25000; // 25KB max
	private static final Duration LAMBDA_TIMEOUT = Duration.ofMinutes(30); // 1 hour for rate limiting scenarios

	// Rate limiting configuration
	private static final long LAMBDA_RATE_LIMIT_DELAY = 5000; // 10 seconds between calls
	private static final long SUGGESTIONS_RATE_LIMIT_DELAY = 8000; // 15 seconds for suggestions
	private static final int MAX_LAMBDA_RETRIES = 3;
	private static final long MAX_RETRY_DELAY_MS = 60000; // 5 minutes max delay
	private static final long BASE_RETRY_DELAY_MS = 5000; // 5 seconds base delay

	// Circuit breaker configuration
	private static final int CIRCUIT_BREAKER_FAILURE_THRESHOLD = 2;
	private static final long CIRCUIT_BREAKER_TIMEOUT_MS = 120000; // 5 minutes

	// Rate limiting state management
	private final ConcurrentHashMap<String, Long> lastInvocationTimes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, AtomicInteger> failureCounters = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Long> circuitBreakerOpenTimes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Long> analysisLocks = new ConcurrentHashMap<>();
	private static final long LOCK_TIMEOUT_MS = 3600000; // 1 hour

	// Monitoring
	private final AtomicLong totalInvocations = new AtomicLong(0);
	private final AtomicLong successfulInvocations = new AtomicLong(0);
	private final AtomicLong failedInvocations = new AtomicLong(0);
	private final AtomicLong rateLimitedInvocations = new AtomicLong(0);

	@Value("${aws.lambda.functions.screening}")
	private String screeningFunctionArn;

	@Value("${aws.lambda.functions.detection}")
	private String detectionFunctionArn;

	@Value("${aws.lambda.functions.suggestions}")
	private String suggestionsFunctionArn;

	// Optional rate limiting configuration from properties
	@Value("${aws.lambda.rate-limit.min-interval-between-calls:10000}")
	private long configuredRateLimit;

	@Value("${aws.lambda.rate-limit.max-concurrent-executions:1}")
	private int maxConcurrentExecutions;

	@Autowired
	private DataAggregationService dataAggregationService;

	@Autowired
	private AnalysisRepository analysisRepository;

	@Autowired
	private IssueDetailsRepository issueDetailsRepository;

	@Autowired
	public LambdaInvokerService(LambdaClient lambdaClient) {
		this.lambdaClient = lambdaClient;
		log.info("🚀 LambdaInvokerService initialized with aggressive rate limiting configuration");
		log.info("📊 Rate limits: Lambda calls={}ms, Suggestions={}ms, Max retries={}", LAMBDA_RATE_LIMIT_DELAY,
				SUGGESTIONS_RATE_LIMIT_DELAY, MAX_LAMBDA_RETRIES);
	}

	/**
	 * Enhanced screening invocation with basic rate limiting
	 */
	public List<Map<String, Object>> invokeScreening(String sessionId, String analysisId, String repository,
			String branch, List<GitHubFile> files, int scanNumber) {

		String lockKey = "screening_" + analysisId;
		if (!acquireAnalysisLock(lockKey)) {
			log.warn("⚠️ Another screening process is already running for analysis {}", analysisId);
			return new ArrayList<>();
		}

		try {
			enforceRateLimit("screening");

			List<Map<String, Object>> fileInputs = files.stream().map(file -> {
				Map<String, Object> fileMap = new HashMap<>();
				fileMap.put("path", file.getPath());
				fileMap.put("name", file.getName());
				fileMap.put("content", file.getContent());
				fileMap.put("size", file.getSize());
				fileMap.put("sha", file.getSha());
				fileMap.put("language", file.getLanguage());
				fileMap.put("mimeType", file.getMimeType());
				fileMap.put("encoding", "UTF-8");
				return fileMap;
			}).collect(Collectors.toList());

			String testPayloadJson = objectMapper.writeValueAsString(Map.of("files", fileInputs));
			boolean needsBatching = testPayloadJson.length() > 200000; // 200KB threshold

			if (!needsBatching) {
				return invokeSingleScreening(sessionId, analysisId, repository, branch, fileInputs, scanNumber);
			} else {
				return invokeBatchedScreening(sessionId, analysisId, repository, branch, fileInputs, scanNumber);
			}

		} catch (Exception e) {
			log.error("❌ Failed to invoke screening Lambda for analysis {}", analysisId, e);
			recordFailure("screening");
			return new ArrayList<>();
		} finally {
			releaseAnalysisLock(lockKey);
		}
	}

	/**
	 * Enhanced detection invocation with aggressive rate limiting
	 */
	public List<Map<String, Object>> invokeDetection(String sessionId, String analysisId, String repository,
			String branch, List<Map<String, Object>> screenedFiles, int scanNumber) {

		String lockKey = "detection_" + analysisId;
		if (!acquireAnalysisLock(lockKey)) {
			log.warn("⚠️ Another detection process is already running for analysis {}", analysisId);
			return new ArrayList<>();
		}

		try {
			if (isCircuitBreakerOpen("detection")) {
				log.warn("🔴 Circuit breaker is OPEN for detection. Skipping invocation.");
				return new ArrayList<>();
			}

			enforceRateLimit("detection");

			String testPayload = objectMapper.writeValueAsString(screenedFiles);
			if (testPayload.length() > MAX_PAYLOAD_SIZE || screenedFiles.size() > DETECTION_BATCH_SIZE) {
				log.info("📦 Large payload detected ({} files, {} bytes). Using batch processing...",
						screenedFiles.size(), testPayload.length());
				return invokeDetectionInBatches(sessionId, analysisId, repository, branch, screenedFiles, scanNumber);
			}

			return invokeSingleDetection(sessionId, analysisId, repository, branch, screenedFiles, scanNumber);

		} catch (Exception e) {
			log.error("❌ Failed to invoke detection Lambda for analysis {}", analysisId, e);
			recordFailure("detection");
			return new ArrayList<>();
		} finally {
			releaseAnalysisLock(lockKey);
		}
	}

	/**
	 * Ultra-conservative suggestions invocation with maximum rate limiting
	 */
	public String invokeSuggestions(String sessionId, String analysisId, String repository, String branch,
			List<Map<String, Object>> issues, int scanNumber) {

		String lockKey = "suggestions_" + analysisId;
		if (!acquireAnalysisLock(lockKey)) {
			log.warn("⚠️ Another suggestions process is already running for analysis {}", analysisId);
			return null;
		}

		try {
			if (isCircuitBreakerOpen("suggestions")) {
				log.warn("🔴 Circuit breaker is OPEN for suggestions. Skipping invocation.");
				return null;
			}

			// Validate inputs
			if (issues == null || issues.isEmpty()) {
				log.warn("⚠️ No issues provided for suggestions generation");
				return createEmptySuggestionsResponse(analysisId);
			}

			// Apply hybrid strategy to issues before processing
			List<Map<String, Object>> hybridProcessedIssues = applyHybridStrategy(issues);

			log.info("🎯 Hybrid Strategy: Processing {} issues out of {} total (optimized for cost)",
					hybridProcessedIssues.size(), issues.size());

			// Add fallback mechanism for failed suggestions
			String result = invokeSuggestionsWithFallback(sessionId, analysisId, repository, branch, 
					hybridProcessedIssues, scanNumber);
			
			if (result == null || "FAILED".equals(result)) {
				log.warn("⚠️ Suggestions generation failed, creating partial response");
				return createPartialSuggestionsResponse(analysisId, hybridProcessedIssues);
			}
			
			return result;

		} catch (Exception e) {
			log.error("❌ Failed to invoke suggestions Lambda for analysis {}", analysisId, e);
			recordFailure("suggestions");
			return createPartialSuggestionsResponse(analysisId, issues);
		} finally {
			releaseAnalysisLock(lockKey);
		}
	}

	private String generateBasicDescription(Map<String, Object> issue) {
		String type = (String) issue.get("type");
		String file = (String) issue.get("file");
		
		switch (type) {
			case "HARDCODED_CREDENTIALS":
				return "Remove hardcoded credentials from " + file + " and use environment variables or secure configuration.";
			case "SQL_INJECTION":
				return "Use parameterized queries to prevent SQL injection vulnerabilities in " + file + ".";
			case "XSS":
			case "CROSS_SITE_SCRIPTING (XSS)":
				return "Sanitize user inputs and encode outputs to prevent XSS attacks in " + file + ".";
			case "HIGH_CYCLOMATIC_COMPLEXITY":
				return "Refactor complex methods in " + file + " to improve maintainability.";
			case "CODE_DUPLICATION":
				return "Extract common code patterns in " + file + " into reusable methods.";
			case "MISSING_ERROR_HANDLING":
				return "Add proper error handling and exception management in " + file + ".";
			default:
				return "Review and improve code quality in " + file + " for " + type.toLowerCase().replace("_", " ") + ".";
		}
	}

	private String generateBasicFix(Map<String, Object> issue) {
		String type = (String) issue.get("type");
		
		switch (type) {
			case "HARDCODED_CREDENTIALS":
				return "1. Move credentials to environment variables\n2. Use @Value annotation for Spring properties\n3. Implement secure credential management";
			case "SQL_INJECTION":
				return "1. Replace string concatenation with PreparedStatement\n2. Use parameter placeholders (?)\n3. Validate and sanitize inputs";
			case "XSS":
			case "CROSS_SITE_SCRIPTING (XSS)":
				return "1. Use Thymeleaf's th:text instead of th:utext\n2. Implement input validation\n3. Apply output encoding";
			case "HIGH_CYCLOMATIC_COMPLEXITY":
				return "1. Break down large methods into smaller ones\n2. Use early returns to reduce nesting\n3. Apply strategy or command patterns";
			case "CODE_DUPLICATION":
				return "1. Extract common code into utility methods\n2. Create base classes for shared functionality\n3. Use composition over inheritance";
			case "MISSING_ERROR_HANDLING":
				return "1. Add try-catch blocks for risky operations\n2. Implement proper logging\n3. Return meaningful error responses";
			default:
				return "1. Review code for best practices\n2. Apply relevant design patterns\n3. Add comprehensive testing";
		}
	}

	private String generateBasicSuggestionsResponse(List<Map<String, Object>> issues) {
	    try {
	        List<Map<String, Object>> basicSuggestions = new ArrayList<>();
	        
	        for (Map<String, Object> issue : issues) {
	            Map<String, Object> suggestion = new HashMap<>();
	            suggestion.put("issueId", issue.get("id"));
	            suggestion.put("title", "Basic Fix Available");
	            suggestion.put("description", generateBasicDescription(issue));
	            suggestion.put("fix", generateBasicFix(issue));
	            suggestion.put("automated", false);
	            basicSuggestions.add(suggestion);
	        }
	        
	        Map<String, Object> response = new HashMap<>();
	        response.put("suggestions", basicSuggestions);
	        response.put("status", "PARTIAL");
	        response.put("timestamp", System.currentTimeMillis());
	        
	        return objectMapper.writeValueAsString(response);
	    } catch (Exception e) {
	        log.error("Failed to create basic suggestions response", e);
	        return "{\"status\":\"ERROR\"}";
	    }
	}

	// Fix the invokeSuggestionsWithFallback method
	private String invokeSuggestionsWithFallback(String sessionId, String analysisId, String repository, 
	        String branch, List<Map<String, Object>> issues, int scanNumber) {
	    try {
	        // Primary suggestion generation
	        return invokeSuggestionsWithTimeout(sessionId, analysisId, repository, branch, issues, scanNumber);
	    } catch (Exception e) {
	        log.warn("⚠️ Primary suggestions failed, attempting fallback: {}", e.getMessage());
	        // Fallback: Generate basic suggestions locally
	        return generateBasicSuggestionsResponse(issues);
	    }
	}

	// Fix the createPartialSuggestionsResponse method
	private String createPartialSuggestionsResponse(String analysisId, List<Map<String, Object>> issues) {
	    try {
	        List<Map<String, Object>> basicSuggestions = new ArrayList<>();
	        
	        for (Map<String, Object> issue : issues) {
	            Map<String, Object> suggestion = new HashMap<>();
	            suggestion.put("issueId", issue.get("id"));
	            suggestion.put("title", "Basic Fix Available");
	            suggestion.put("description", generateBasicDescription(issue));
	            suggestion.put("fix", generateBasicFix(issue));
	            suggestion.put("automated", false);
	            basicSuggestions.add(suggestion);
	        }
	        
	        Map<String, Object> response = new HashMap<>();
	        response.put("analysisId", analysisId);
	        response.put("suggestions", basicSuggestions);
	        response.put("status", "PARTIAL");
	        response.put("timestamp", System.currentTimeMillis());
	        
	        return objectMapper.writeValueAsString(response);
	    } catch (Exception e) {
	        log.error("Failed to create partial suggestions response", e);
	        return "{\"status\":\"ERROR\"}";
	    }
	}

	// Fix the createEmptySuggestionsResponse method
	private String createEmptySuggestionsResponse(String analysisId) {
	    Map<String, Object> response = new HashMap<>();
	    response.put("analysisId", analysisId);
	    response.put("suggestions", new ArrayList<>());
	    response.put("status", "NO_ISSUES");
	    response.put("timestamp", System.currentTimeMillis());
	    
	    try {
	        return objectMapper.writeValueAsString(response);
	    } catch (Exception e) {
	        log.error("Failed to create empty suggestions response", e);
	        return "{\"status\":\"ERROR\"}";
	    }
	}
	/**
	 * Asynchronous suggestions invocation with timeout protection
	 */
	public String invokeSuggestionsWithTimeout(String sessionId, String analysisId, String repository, 
	        String branch, List<Map<String, Object>> issues, int scanNumber) {
	    
	    try {
	        log.info("🚀 Starting async suggestions generation for analysis: {}", analysisId);
	        
	        // Start Lambda function asynchronously
	        CompletableFuture<String> asyncResult = invokeSuggestionsAsync(
	            sessionId, analysisId, repository, branch, issues, scanNumber);
	        
	        // Wait for completion with timeout (20 minutes)
	        String result = asyncResult.get(20, TimeUnit.MINUTES);

	        // Process the result to ensure consistent format
	        String processedResult = processAsyncLambdaResponse(result != null ? result : "TIMEOUT", "suggestions");

	        if ("COMPLETED".equals(processedResult)) {
	            log.info("✅ Suggestions completed successfully for analysis: {}", analysisId);
	            return createSyntheticJsonResponseForLambda("suggestions", "SUCCESS");
	        } else if ("FAILED".equals(processedResult)) {
	            log.error("❌ Suggestions failed for analysis: {}", analysisId);
	            return createSyntheticJsonResponseForLambda("suggestions", "FAILURE");
	        } else {
	            log.warn("⚠️ Suggestions completed with status: {} for analysis: {}", processedResult, analysisId);
	            return createSyntheticJsonResponseForLambda("suggestions", processedResult);
	        }
	        
	    } catch (Exception e) {
	        log.error("❌ Suggestions invocation failed for analysis {}: {}", analysisId, e.getMessage());
	        recordFailure("suggestions");
	        return null;
	    }
	}
	
	public ReportResponse createReportFromAnalysis(Analysis analysis) {
	    try {
	        String analysisId = analysis.getAnalysisId();
	        
	        // Get analysis result from DynamoDB
	        AnalysisResult analysisResult = analysisRepository.findById(analysisId).orElse(null);
	        if (analysisResult == null) {
	            return null;
	        }
	        
	        // Get all issues from DynamoDB
	        List<Issue> issues = issueDetailsRepository.findByAnalysisId(analysisId);
	        
	        // Count issues by severity
	        long criticalCount = issues.stream().filter(i -> "CRITICAL".equals(i.getSeverity())).count();
	        long highCount = issues.stream().filter(i -> "HIGH".equals(i.getSeverity())).count();
	        long mediumCount = issues.stream().filter(i -> "MEDIUM".equals(i.getSeverity())).count();
	        long lowCount = issues.stream().filter(i -> "LOW".equals(i.getSeverity())).count();
	        
	        Map<String, Double> scores = new HashMap<>();
	        scores.put("security", 85.0);
	        scores.put("performance", 85.0);
	        scores.put("quality", 85.0);
	        
	        return ReportResponse.builder()
	            .analysisId(analysisId)
	            .repository(analysisResult.getRepository())
	            .branch(analysisResult.getBranch())
	            .date(new Date())
	            .filesAnalyzed(analysisResult.getFilesAnalyzed())
	            .totalIssues(issues.size())
	            .scanNumber(analysisResult.getScanNumber())
	            .criticalCount((int) criticalCount)
	            .highCount((int) highCount)
	            .mediumCount((int) mediumCount)
	            .lowCount((int) lowCount)
	            .processingTime(analysisResult.getProcessingTimeMs())
	            .issues(issues)
	            .scores(scores)
	            .build();
	            
	    } catch (Exception e) {
	        log.error("Error creating report from analysis", e);
	        return null;
	    }
	}
	
	/**
	 * Apply hybrid strategy for cost-effective suggestions Priority: Nova Lite
	 * (90%) → Templates (9%) → Nova Premier (1%)
	 */
	private List<Map<String, Object>> applyHybridStrategy(List<Map<String, Object>> issues) {
		List<Map<String, Object>> processedIssues = new ArrayList<>();

		for (Map<String, Object> issue : issues) {
			String severity = (String) issue.getOrDefault("severity", "MEDIUM");
			String category = (String) issue.getOrDefault("category", "quality");

			// Apply hybrid priority logic
			// Apply hybrid priority logic
			String selectedModel = determineModelForIssue(severity, category, issue);
			issue.put("selectedModel", selectedModel);
			issue.put("processingStrategy", "hybrid");

			// Only process if not skipped
			if (!"SKIP".equals(selectedModel)) {
				processedIssues.add(issue);
			}
		}

		log.info("🔄 Hybrid Strategy Applied: {} issues selected for processing from {} total", processedIssues.size(),
				issues.size());

		return processedIssues;
	}
	
	/**
	 * Asynchronous Lambda invocation with polling
	 */
	@Async
	public CompletableFuture<String> invokeSuggestionsAsync(String sessionId, String analysisId, 
	        String repository, String branch, List<Map<String, Object>> issues, int scanNumber) {
	    
	    return CompletableFuture.supplyAsync(() -> {
	        try {
	            // Start Lambda function asynchronously
	            String invokeResult = invokeLambdaAsync(
	                suggestionsFunctionArn, 
	                buildSuggestionsPayload(sessionId, analysisId, repository, branch, issues, scanNumber)
	            );
	            
	            if (invokeResult != null) {
	                // Poll for completion
	                return pollForSuggestionsCompletion(analysisId, 1200000L); // 20 minutes max
	            }
	            
	            return "FAILED";
	            
	        } catch (Exception e) {
	            log.error("❌ Async suggestions generation failed for analysis {}: {}", analysisId, e.getMessage());
	            return "FAILED";
	        }
	    });
	}

	/**
	 * Invoke Lambda function asynchronously
	 */
	private String invokeLambdaAsync(String functionArn, String payload) {
	    try {
	        InvokeRequest request = InvokeRequest.builder()
	            .functionName(functionArn)
	            .invocationType(InvocationType.EVENT) // Async invocation
	            .payload(SdkBytes.fromUtf8String(payload))
	            .build();
	        
	        InvokeResponse response = lambdaClient.invoke(request);
	        
	        if (response.statusCode() == 202) { // Async success
	            log.info("✅ Lambda function started asynchronously");
	            return "ASYNC_STARTED";
	        } else {
	            log.error("❌ Lambda async invocation failed with status: {}", response.statusCode());
	            return null;
	        }
	        
	    } catch (Exception e) {
	        log.error("❌ Failed to invoke Lambda async: {}", e.getMessage());
	        return null;
	    }
	}
	/**
	 * Validate and process async Lambda response
	 */
	private String processAsyncLambdaResponse(String rawResponse, String operation) {
	    if (rawResponse == null || rawResponse.trim().isEmpty()) {
	        log.warn("Empty async response from Lambda for operation: {}", operation);
	        return "FAILED";
	    }
	    
	    String trimmedResponse = rawResponse.trim();
	    
	    // Handle plain text responses
	    if (trimmedResponse.startsWith("SUCCESS") || trimmedResponse.startsWith("COMPLETED")) {
	        log.info("Async Lambda {} completed with text response: {}", operation, 
	                trimmedResponse.substring(0, Math.min(50, trimmedResponse.length())));
	        return "COMPLETED";
	    } else if (trimmedResponse.startsWith("FAILURE") || trimmedResponse.startsWith("ERROR")) {
	        log.error("Async Lambda {} failed with text response: {}", operation, 
	                 trimmedResponse.substring(0, Math.min(50, trimmedResponse.length())));
	        return "FAILED";
	    }
	    
	    // Try to parse as JSON for structured response
	    try {
	        objectMapper.readTree(trimmedResponse);
	        // If valid JSON, extract status
	        Map<String, Object> responseMap = objectMapper.readValue(trimmedResponse, Map.class);
	        String status = (String) responseMap.get("status");
	        
	        if ("success".equals(status) || "completed".equals(status)) {
	            return "COMPLETED";
	        } else if ("error".equals(status) || "failed".equals(status)) {
	            return "FAILED";
	        } else {
	            return "IN_PROGRESS";
	        }
	    } catch (Exception e) {
	        log.warn("Could not parse async response as JSON for {}: {}", operation, e.getMessage());
	        return "FAILED";
	    }
	}
	/**
	 * Poll DynamoDB for suggestions completion
	 */
	private String pollForSuggestionsCompletion(String analysisId, long maxWaitTimeMs) {
	    long startTime = System.currentTimeMillis();
	    long pollingInterval = 2000; // Start with 2 seconds for immediate results
	    long maxPollingInterval = 15000; // Cap at 15 seconds maximum
	    int consecutiveNotFoundCount = 0;
	    final int EXPONENTIAL_THRESHOLD = 3; // Start exponential backoff after 3 attempts
	    
	    while (System.currentTimeMillis() - startTime < maxWaitTimeMs) {
	    	try {
	            // Check analysis progress in DynamoDB
	            Map<String, Object> analysisStatus = dataAggregationService.getAnalysisProgress(analysisId);
	            
	            if (analysisStatus != null) {
	                String status = (String) analysisStatus.get("status");
	                
	             // FIX: Handle all terminal states properly
	                if ("suggestions_complete".equals(status) || "completed".equals(status)) {
	                    log.debug("✅ Analysis completed for: {} with status: {} in {} attempts", 
	                        analysisId, status, consecutiveNotFoundCount + 1);
	                    return "COMPLETED";
	                } else if ("failed".equals(status) || "error".equals(status)) {
	                    log.error("❌ Analysis failed for: {} with status: {}", analysisId, status);
	                    return "FAILED";
	                } else if ("suggestions".equals(status) || "in_progress".equals(status) || 
	                          "screening".equals(status) || "detection".equals(status)) {
	                    // Valid in-progress states, continue polling
	                    log.debug("🔍 Analysis {} in progress with status: {}, continuing to poll", analysisId, status);
	                    consecutiveNotFoundCount = 0; // Reset counter for valid status updates
	                } else {
	                    // Unknown status - treat as in-progress but increment counter
	                    log.warn("⚠️ Unknown status '{}' for analysis {}, continuing to poll", status, analysisId);
	                    consecutiveNotFoundCount++;
	                }
	            } else {
	                // Increment not found count for exponential backoff
	                consecutiveNotFoundCount++;
	            }
	            
	            // Adaptive polling: fast initially, slower if not ready
	            if (consecutiveNotFoundCount <= EXPONENTIAL_THRESHOLD) {
	                pollingInterval = 2000; // Keep checking every 2 seconds for first few attempts
	            } else {
	                // Exponential backoff: 4s, 8s, 15s (capped)
	                pollingInterval = Math.min(
	                    pollingInterval * 2, 
	                    maxPollingInterval
	                );
	            }
	            
	            log.debug("🔍 Checking analysis progress for: {} (attempt {}, next check in {}ms)", 
	                analysisId, consecutiveNotFoundCount + 1, pollingInterval);
	            
	            // Wait before next poll
	            Thread.sleep(pollingInterval);
	            
	        } catch (InterruptedException e) {
	            Thread.currentThread().interrupt();
	            log.error("❌ Polling interrupted for analysis: {}", analysisId);
	            return "INTERRUPTED";
	        } catch (Exception e) {
	            log.error("❌ Error polling for analysis {}: {}", analysisId, e.getMessage());
	            // Continue polling despite errors
	        }
	    }
	    
	    log.warn("⏰ Polling timeout for analysis: {}", analysisId);
	    return "TIMEOUT";
	}

	/**
	 * Build suggestions payload with enhanced configuration
	 */
	private String buildSuggestionsPayload(String sessionId, String analysisId, String repository, 
	        String branch, List<Map<String, Object>> issues, int scanNumber) throws Exception {
	    
	    Map<String, Object> payload = new HashMap<>();
	    payload.put("sessionId", sessionId);
	    payload.put("analysisId", analysisId);
	    payload.put("repository", repository);
	    payload.put("branch", branch);
	    payload.put("issues", issues);
	    payload.put("scanNumber", scanNumber);
	    
	    // Enhanced configuration for rate limiting
	    payload.put("strategy", "hybrid");
	    payload.put("processingMode", "async");
	    payload.put("rateLimitMode", true);
	    payload.put("maxConcurrentRequests", 1);
	    payload.put("batchSize", 1);
	    payload.put("delayBetweenRequests", 8000); // 8 seconds
	    payload.put("maxRetries", 3);
	    payload.put("timeoutBuffer", 60000); // 1 minute buffer
	    payload.put("timestamp", System.currentTimeMillis());
	    
	    return objectMapper.writeValueAsString(payload);
	}
	
	/**
	 * Determine model based on issue severity and category Implements the 90/9/1
	 * strategy using deterministic selection
	 */
	private String determineModelForIssue(String severity, String category, Map<String, Object> issue) {
		// Create deterministic hash from issue characteristics
		String issueKey = String.format("%s_%s_%s_%s", issue.getOrDefault("id", "unknown"),
				issue.getOrDefault("type", "unknown"), issue.getOrDefault("file", "unknown"),
				issue.getOrDefault("line", "0"));

		int hash = Math.abs(issueKey.hashCode()) % 100;

		// 1% Nova Premier for CRITICAL security issues only
		if ("CRITICAL".equalsIgnoreCase(severity) && "security".equalsIgnoreCase(category)) {
			return hash < 1 ? "nova-premier" : "nova-lite";
		}

		// 90% Nova Lite for most issues
		if (hash < 90) {
			return "nova-lite";
		}

		// 9% Enhanced Templates for fallback (90-98)
		if (hash < 99) {
			return "template";
		}

		// Remaining 1% - use Nova Lite instead of skipping
		return "nova-lite";
	}
	/**
	 * Validate Lambda response format before processing
	 */
	private String validateLambdaResponse(String response, String operation) {
	    if (response == null || response.trim().isEmpty()) {
	        log.warn("Empty response from Lambda operation: {}", operation);
	        return null;
	    }
	    
	    String trimmedResponse = response.trim();
	    
	    // Check for known problematic response formats
	    if (trimmedResponse.startsWith("SUCCESS") || 
	        trimmedResponse.startsWith("FAILURE") || 
	        trimmedResponse.startsWith("ERROR") ||
	        trimmedResponse.startsWith("COMPLETED")) {
	        
	        log.error("Lambda {} returned plain text response instead of JSON: {}", 
	                 operation, trimmedResponse.substring(0, Math.min(100, trimmedResponse.length())));
	        
	        // Try to create a synthetic JSON response
	        return createSyntheticJsonResponse(trimmedResponse, operation);
	    }
	    
	    // Validate JSON structure
	    if (!trimmedResponse.startsWith("{") && !trimmedResponse.startsWith("[")) {
	        log.warn("Lambda {} response doesn't start with JSON delimiter: {}", 
	                operation, trimmedResponse.substring(0, Math.min(50, trimmedResponse.length())));
	        return null;
	    }
	    
	    return trimmedResponse;
	}

	/**
	 * Create synthetic JSON response from plain text Lambda response
	 */
	private String createSyntheticJsonResponse(String plainTextResponse, String operation) {
	    try {
	        Map<String, Object> syntheticResponse = new HashMap<>();
	        
	        if (plainTextResponse.startsWith("SUCCESS")) {
	            syntheticResponse.put("status", "success");
	            syntheticResponse.put("message", plainTextResponse);
	        } else if (plainTextResponse.startsWith("COMPLETED")) {
	            syntheticResponse.put("status", "completed");
	            syntheticResponse.put("message", plainTextResponse);
	        } else {
	            syntheticResponse.put("status", "error");
	            syntheticResponse.put("message", plainTextResponse);
	        }
	        
	        syntheticResponse.put("operation", operation);
	        syntheticResponse.put("timestamp", System.currentTimeMillis());
	        syntheticResponse.put("synthetic", true);
	        
	        if ("suggestions".equals(operation)) {
	            syntheticResponse.put("suggestions", new ArrayList<>());
	            syntheticResponse.put("summary", Map.of(
	                "totalSuggestions", 0,
	                "tokensUsed", 0,
	                "totalCost", 0.0
	            ));
	        }
	        
	        String jsonResponse = objectMapper.writeValueAsString(syntheticResponse);
	        log.info("Created synthetic JSON response for {} operation", operation);
	        return jsonResponse;
	        
	    } catch (Exception e) {
	        log.error("Failed to create synthetic JSON response: {}", e.getMessage());
	        return null;
	    }
	}
	/**
	 * Process and validate Lambda response before returning
	 */
	private String processLambdaResponse(String rawResponse, String operation) {
	    if (rawResponse == null || rawResponse.trim().isEmpty()) {
	        log.warn("Received null or empty response from Lambda for operation: {}", operation);
	        return createSyntheticJsonResponseForLambda(operation, "EMPTY_RESPONSE");
	    }
	    
	    String trimmedResponse = rawResponse.trim();
	    
	    // Check if response is plain text instead of JSON
	    if (trimmedResponse.startsWith("SUCCESS") || 
	        trimmedResponse.startsWith("FAILURE") || 
	        trimmedResponse.startsWith("ERROR") ||
	        trimmedResponse.startsWith("COMPLETED")) {
	        
	    	log.warn("Lambda returned plain text response for {}: {}", operation, 
	    	        trimmedResponse.substring(0, Math.min(100, trimmedResponse.length())));
	    	log.debug("Full plain text response for {}: {}", operation, trimmedResponse);
	        
	        return createSyntheticJsonResponseForLambda(operation, trimmedResponse);
	    }
	    
	    // Validate JSON format
	    try {
	        objectMapper.readTree(trimmedResponse);
	        return trimmedResponse; // Valid JSON
	    } catch (Exception e) {
	        log.error("Lambda response is not valid JSON for {}: {}", operation, e.getMessage());
	        return createSyntheticJsonResponseForLambda(operation, trimmedResponse);
	    }
	}

	/**
	 * Create synthetic JSON response when Lambda returns plain text
	 */
	private String createSyntheticJsonResponseForLambda(String operation, String plainTextResponse) {
	    try {
	        Map<String, Object> syntheticResponse = new HashMap<>();
	        
	        if (plainTextResponse.startsWith("SUCCESS")) {
	            syntheticResponse.put("status", "success");
	            syntheticResponse.put("message", plainTextResponse);
	        } else if (plainTextResponse.startsWith("FAILURE")) {
	            syntheticResponse.put("status", "error");
	            syntheticResponse.put("message", plainTextResponse);
	        } else if (plainTextResponse.startsWith("COMPLETED")) {
	            syntheticResponse.put("status", "success");
	            syntheticResponse.put("message", plainTextResponse);
	        } else {
	            syntheticResponse.put("status", "error");
	            syntheticResponse.put("message", plainTextResponse);
	        }
	        
	        syntheticResponse.put("operation", operation);
	        syntheticResponse.put("timestamp", System.currentTimeMillis());
	        syntheticResponse.put("synthetic", true);
	        
	        if ("suggestions".equals(operation)) {
	            syntheticResponse.put("suggestions", new ArrayList<>());
	            syntheticResponse.put("summary", Map.of(
	                "totalSuggestions", 0,
	                "tokensUsed", 0,
	                "totalCost", 0.0
	            ));
	        }
	        
	        String jsonResponse = objectMapper.writeValueAsString(syntheticResponse);
	        log.info("Created synthetic JSON response for {} operation", operation);
	        return jsonResponse;
	        
	    } catch (Exception e) {
	        log.error("Failed to create synthetic JSON response: {}", e.getMessage());
	        return null;
	    }
	}
	
	/**
	 * Enhanced suggestions invocation with hybrid strategy support
	 */
	public String invokeSuggestionsWithHybridStrategy(String sessionId, String analysisId, String repository,
			String branch, List<Map<String, Object>> issues, int scanNumber) throws Exception {

		log.info("🚀 Invoking suggestions Lambda with hybrid strategy for {} issues", issues.size());

		// Pre-delay to ensure we don't hit rate limits
		enforceRateLimit("suggestions", SUGGESTIONS_RATE_LIMIT_DELAY);

		Map<String, Object> payload = new HashMap<>();
		payload.put("sessionId", sessionId);
		payload.put("analysisId", analysisId);
		payload.put("repository", repository);
		payload.put("branch", branch);
		payload.put("issues", issues);
		payload.put("scanNumber", scanNumber);

		// Enable hybrid strategy in Lambda
		payload.put("strategy", "hybrid");
		payload.put("modelId", determineOverallModelStrategy(issues));
		payload.put("processingMode", "hybrid");

		// Add issue severity for routing decisions
		if (!issues.isEmpty()) {
			String maxSeverity = issues.stream().map(issue -> (String) issue.getOrDefault("severity", "MEDIUM"))
					.max(this::compareSeverity).orElse("MEDIUM");
			payload.put("issueSeverity", maxSeverity);
		}

		// Rate limiting configuration
		payload.put("rateLimitMode", true);
		payload.put("maxConcurrentRequests", 1);
		payload.put("batchSize", 1);
		payload.put("delayBetweenRequests", 8000); // 8 seconds between Nova API calls
		payload.put("maxRetries", 10);
		payload.put("exponentialBackoffMaxDelay", 120000);
		payload.put("timestamp", System.currentTimeMillis());

		String payloadJson = objectMapper.writeValueAsString(payload);

		log.info("📤 Invoking suggestions Lambda with hybrid strategy, payload size: {} bytes", payloadJson.length());

		InvokeRequest request = InvokeRequest.builder().functionName(suggestionsFunctionArn)
				.invocationType(InvocationType.REQUEST_RESPONSE).payload(SdkBytes.fromUtf8String(payloadJson)).build();

		String rawResponse = invokeWithRetryAndCircuitBreaker(request, "suggestions");
		return processLambdaResponse(rawResponse, "suggestions");
	}

	private String determineOverallModelStrategy(List<Map<String, Object>> issues) {
		boolean hasCriticalSecurity = issues.stream()
				.anyMatch(issue -> "CRITICAL".equalsIgnoreCase((String) issue.get("severity"))
						&& "security".equalsIgnoreCase((String) issue.get("category")));

		// Use first issue's characteristics for consistency
		if (!issues.isEmpty()) {
			Map<String, Object> firstIssue = issues.get(0);
			String issueKey = String.format("%s_%d", firstIssue.getOrDefault("analysisId", "unknown"), issues.size());
			int hash = Math.abs(issueKey.hashCode()) % 100;

			if (hasCriticalSecurity && hash < 1) {
				return "nova-premier";
			}

			return hash < 90 ? "nova-lite" : "template";
		}

		return "nova-lite";
	}

	/**
	 * Compare severity levels for priority ordering
	 */
	private int compareSeverity(String s1, String s2) {
		Map<String, Integer> severityOrder = Map.of("LOW", 1, "MEDIUM", 2, "HIGH", 3, "CRITICAL", 4);

		return Integer.compare(severityOrder.getOrDefault(s1, 2), severityOrder.getOrDefault(s2, 2));
	}

	/**
	 * Enhanced suggestions invocation with aggressive rate limiting
	 */
	public String invokeSuggestionsWithRateLimit(String sessionId, String analysisId, String repository, String branch,
			List<Map<String, Object>> issues, int scanNumber) throws Exception {

		log.info("🐌 Invoking suggestions with ultra-aggressive rate limiting for {} issues", issues.size());

		// Pre-delay to ensure we don't hit rate limits
		enforceRateLimit("suggestions", SUGGESTIONS_RATE_LIMIT_DELAY);

		Map<String, Object> payload = new HashMap<>();
		payload.put("sessionId", sessionId);
		payload.put("analysisId", analysisId);
		payload.put("repository", repository);
		payload.put("branch", branch);
		payload.put("issues", issues);
		payload.put("scanNumber", scanNumber);
		payload.put("rateLimitMode", true);
		payload.put("maxConcurrentRequests", 1);
		payload.put("batchSize", 1);
		payload.put("delayBetweenRequests", 15000); // 15 seconds between Nova API calls
		payload.put("maxRetries", 3);
		payload.put("exponentialBackoffMaxDelay", 120000);
		payload.put("timestamp", System.currentTimeMillis());

		String payloadJson = objectMapper.writeValueAsString(payload);

		log.info("📤 Invoking suggestions Lambda with ultra-conservative configuration, payload size: {} bytes",
				payloadJson.length());

		InvokeRequest request = InvokeRequest.builder().functionName(suggestionsFunctionArn)
				.invocationType(InvocationType.REQUEST_RESPONSE).payload(SdkBytes.fromUtf8String(payloadJson)).build();

		return invokeWithRetryAndCircuitBreaker(request, "suggestions");
	}

	/**
	 * Private helper methods
	 */
	private List<Map<String, Object>> invokeSingleScreening(String sessionId, String analysisId, String repository,
			String branch, List<Map<String, Object>> fileInputs, int scanNumber) throws Exception {

		Map<String, Object> payload = new HashMap<>();
		payload.put("sessionId", sessionId);
		payload.put("analysisId", analysisId);
		payload.put("repository", repository);
		payload.put("branch", branch);
		payload.put("files", fileInputs);
		payload.put("stage", "screening");
		payload.put("scanNumber", scanNumber);
		payload.put("timestamp", System.currentTimeMillis());

		String payloadJson = objectMapper.writeValueAsString(payload);
		log.info("📤 Invoking screening Lambda with payload size: {} bytes", payloadJson.length());

		InvokeRequest request = InvokeRequest.builder().functionName(screeningFunctionArn)
				.invocationType(InvocationType.REQUEST_RESPONSE).payload(SdkBytes.fromUtf8String(payloadJson)).build();

		String rawResponse = invokeWithRetryAndCircuitBreaker(request, "screening");
		String responseJson = processLambdaResponse(rawResponse, "screening");
		if (responseJson == null)
			return new ArrayList<>();

		Map<String, Object> responseMap = objectMapper.readValue(responseJson, Map.class);
		String status = (String) responseMap.get("status");

		if ("error".equals(status)) {
			log.error("❌ Lambda returned error: {}", responseMap.get("errors"));
			return new ArrayList<>();
		}

		List<Map<String, Object>> screenedFiles = (List<Map<String, Object>>) responseMap.get("files");
		return screenedFiles != null ? screenedFiles : new ArrayList<>();
	}

	private List<Map<String, Object>> invokeBatchedScreening(String sessionId, String analysisId, String repository,
			String branch, List<Map<String, Object>> fileInputs, int scanNumber) throws Exception {

		log.info("📦 Large payload detected ({} files). Using batch processing...", fileInputs.size());

		List<List<Map<String, Object>>> batches = createBatches(fileInputs, 10);
		List<Map<String, Object>> allScreenedFiles = new ArrayList<>();

		for (int i = 0; i < batches.size(); i++) {
			try {
				// Rate limiting between batches
				if (i > 0) {
					enforceRateLimit("screening_batch");
				}

				Map<String, Object> batchPayload = createBatchPayload(sessionId, analysisId, repository, branch,
						batches.get(i), "screening", scanNumber, i + 1, batches.size());

				String payloadJson = objectMapper.writeValueAsString(batchPayload);
				log.info("📤 Invoking screening Lambda batch {}/{} with {} files, payload size: {} bytes", i + 1,
						batches.size(), batches.get(i).size(), payloadJson.length());

				InvokeRequest request = InvokeRequest.builder().functionName(screeningFunctionArn)
						.invocationType(InvocationType.REQUEST_RESPONSE).payload(SdkBytes.fromUtf8String(payloadJson))
						.build();

				String rawResponse = invokeWithRetryAndCircuitBreaker(request, "screening_batch");
				String responseJson = processLambdaResponse(rawResponse, "screening_batch");
				if (responseJson != null) {
					Map<String, Object> responseMap = objectMapper.readValue(responseJson, Map.class);
					String status = (String) responseMap.get("status");

					if ("success".equals(status) || status == null) {
						List<Map<String, Object>> batchFiles = (List<Map<String, Object>>) responseMap.get("files");
						if (batchFiles != null) {
							allScreenedFiles.addAll(batchFiles);
							log.info("✅ Batch {}/{} processed successfully: {} files screened", i + 1, batches.size(),
									batchFiles.size());
						}
					}
				}

			} catch (Exception batchError) {
				log.error("❌ Failed to process batch {}/{}: {}", i + 1, batches.size(), batchError.getMessage());
			}
		}

		log.info("📊 Batch processing complete: {} files screened out of {} total files", allScreenedFiles.size(),
				fileInputs.size());
		return allScreenedFiles;
	}

	private List<Map<String, Object>> invokeSingleDetection(String sessionId, String analysisId, String repository,
			String branch, List<Map<String, Object>> screenedFiles, int scanNumber) throws Exception {

		Map<String, Object> payload = new HashMap<>();
		payload.put("sessionId", sessionId);
		payload.put("analysisId", analysisId);
		payload.put("repository", repository);
		payload.put("branch", branch);
		payload.put("files", screenedFiles);
		payload.put("stage", "detection");
		payload.put("scanNumber", scanNumber);
		payload.put("timestamp", System.currentTimeMillis());

		String payloadJson = objectMapper.writeValueAsString(payload);

		InvokeRequest request = InvokeRequest.builder().functionName(detectionFunctionArn)
				.invocationType(InvocationType.REQUEST_RESPONSE).payload(SdkBytes.fromUtf8String(payloadJson)).build();

		String rawResponse = invokeWithRetryAndCircuitBreaker(request, "detection");
		String responseJson = processLambdaResponse(rawResponse, "detection");
		if (responseJson == null)
			return new ArrayList<>();

		Map<String, Object> responseMap = objectMapper.readValue(responseJson, Map.class);
		String status = (String) responseMap.get("status");

		if ("error".equals(status)) {
			log.error("❌ Detection Lambda returned error: {}", responseMap.get("errors"));
			return new ArrayList<>();
		}

		List<Map<String, Object>> issues = (List<Map<String, Object>>) responseMap.get("issues");
		return issues != null ? issues : new ArrayList<>();
	}

	private List<Map<String, Object>> invokeDetectionInBatches(String sessionId, String analysisId, String repository,
			String branch, List<Map<String, Object>> screenedFiles, int scanNumber) {

		List<Map<String, Object>> allIssues = new ArrayList<>();
		List<List<Map<String, Object>>> batches = createBatches(screenedFiles, DETECTION_BATCH_SIZE);

		log.info("📦 Processing {} files in {} batches for detection", screenedFiles.size(), batches.size());

		int successfulBatches = 0;
		int failedBatches = 0;

		for (int i = 0; i < batches.size(); i++) {
			long batchStartTime = System.currentTimeMillis();

			try {
				// Aggressive rate limiting between batches
				if (i > 0) {
					enforceRateLimit("detection_batch", LAMBDA_RATE_LIMIT_DELAY);
				}

				Map<String, Object> batchPayload = createBatchPayload(sessionId, analysisId, repository, branch,
						batches.get(i), "detection", scanNumber, i + 1, batches.size());

				String batchPayloadJson = objectMapper.writeValueAsString(batchPayload);
				log.info("🔍 Invoking detection batch {}/{}, payload size: {} bytes", i + 1, batches.size(),
						batchPayloadJson.length());

				InvokeRequest request = InvokeRequest.builder().functionName(detectionFunctionArn)
						.invocationType(InvocationType.REQUEST_RESPONSE)
						.payload(SdkBytes.fromUtf8String(batchPayloadJson)).build();

				String rawResponse = invokeWithRetryAndCircuitBreaker(request, "detection_batch");
				String responseJson = processLambdaResponse(rawResponse, "detection_batch");
				if (responseJson != null) {
					Map<String, Object> responseMap = objectMapper.readValue(responseJson, Map.class);
					String status = (String) responseMap.get("status");

					if ("success".equals(status) || status == null) {
						List<Map<String, Object>> batchIssues = (List<Map<String, Object>>) responseMap.get("issues");
						if (batchIssues != null) {
							allIssues.addAll(batchIssues);
							long batchDuration = System.currentTimeMillis() - batchStartTime;
							log.info("✅ Batch {}/{} completed in {} seconds: {} issues found", i + 1, batches.size(),
									batchDuration / 1000, batchIssues.size());
							successfulBatches++;
						}
					} else {
						log.warn("⚠️ Batch {}/{} returned error: {}", i + 1, batches.size(), responseMap.get("errors"));
						failedBatches++;
					}
				} else {
					failedBatches++;
				}

			} catch (Exception e) {
				long batchDuration = System.currentTimeMillis() - batchStartTime;
				log.error("❌ Failed to process detection batch {}/{} after {} seconds: {}", i + 1, batches.size(),
						batchDuration / 1000, e.getMessage());
				failedBatches++;
			}
		}

		log.info("📊 Detection batch processing complete: {} successful, {} failed, {} total issues found",
				successfulBatches, failedBatches, allIssues.size());

		return allIssues;
	}

	/**
	 * Core invocation method with enhanced retry logic and circuit breaker
	 */
	private String invokeWithRetryAndCircuitBreaker(InvokeRequest request, String operation) {
		totalInvocations.incrementAndGet();

		if (isCircuitBreakerOpen(operation)) {
			log.warn("🔴 Circuit breaker is OPEN for operation: {}. Skipping invocation.", operation);
			return null;
		}

		Exception lastException = null;

		for (int attempt = 1; attempt <= MAX_LAMBDA_RETRIES; attempt++) {
			try {
				log.debug("🔄 Invoking Lambda for operation: {} (attempt {}/{})", operation, attempt,
						MAX_LAMBDA_RETRIES);

				long startTime = System.currentTimeMillis();
				InvokeResponse response = lambdaClient.invoke(request);
				long duration = System.currentTimeMillis() - startTime;

				if (response.functionError() != null) {
					log.error("❌ Lambda function error for operation {}: {}", operation, response.functionError());
					recordFailure(operation);
					return null;
				}

				if (response.statusCode() != 200) {
					log.error("❌ Lambda invocation failed for operation {} with status code: {}", operation,
							response.statusCode());
					recordFailure(operation);
					return null;
				}

				// Success
				recordSuccess(operation);
				log.debug("✅ Lambda invocation successful for operation {} in {}ms", operation, duration);
				String rawResponse = response.payload().asUtf8String();
				log.debug("Raw Lambda response for {}: {}", operation, 
				         rawResponse != null ? rawResponse.substring(0, Math.min(200, rawResponse.length())) : "null");
				return rawResponse;

			} catch (SdkClientException e) {
				lastException = e;
				log.warn("⚠️ Lambda invocation failed for operation {} (attempt {}/{}): {}", operation, attempt,
						MAX_LAMBDA_RETRIES, e.getMessage());

				if (attempt < MAX_LAMBDA_RETRIES) {
					long delay = calculateExponentialBackoffDelay(attempt);
					log.info("🕐 Waiting {}ms before retry attempt {}", delay, attempt + 1);

					try {
						Thread.sleep(delay);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						log.error("❌ Retry interrupted for operation: {}", operation);
						break;
					}
				} else {
					recordFailure(operation);
					openCircuitBreaker(operation);
				}
			}
		}

		failedInvocations.incrementAndGet();
		log.error("❌ All {} retry attempts failed for operation: {}", MAX_LAMBDA_RETRIES, operation);
		return null;
	}

	/**
	 * Utility methods
	 */

	/**
	 * Get hybrid strategy metrics
	 */
	public Map<String, Object> getHybridMetrics() {
		Map<String, Object> metrics = getHealthMetrics();

		// Add hybrid-specific metrics
		metrics.put("hybridStrategyEnabled", true);
		metrics.put("costOptimizationActive", true);
		metrics.put("modelDistribution", Map.of("novaLite", "90%", "templates", "9%", "novaPremier", "1%"));

		return metrics;
	}

	private void enforceRateLimit(String operation) {
		enforceRateLimit(operation, LAMBDA_RATE_LIMIT_DELAY);
	}

	private void enforceRateLimit(String operation, long delayMs) {
		Long lastCall = lastInvocationTimes.get(operation);
		if (lastCall != null) {
			long timeSinceLastCall = System.currentTimeMillis() - lastCall;
			if (timeSinceLastCall < delayMs) {
				long waitTime = delayMs - timeSinceLastCall;
				log.info("🐌 Rate limiting: waiting {}ms for operation {}", waitTime, operation);
				rateLimitedInvocations.incrementAndGet();
				try {
					Thread.sleep(waitTime);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}
		lastInvocationTimes.put(operation, System.currentTimeMillis());
	}

	private boolean acquireAnalysisLock(String lockKey) {
		long currentTime = System.currentTimeMillis();
		Long existingLock = analysisLocks.get(lockKey);

		if (existingLock != null && (currentTime - existingLock) < LOCK_TIMEOUT_MS) {
			return false;
		}

		analysisLocks.put(lockKey, currentTime);
		return true;
	}

	private void releaseAnalysisLock(String lockKey) {
		analysisLocks.remove(lockKey);
	}

	private boolean isCircuitBreakerOpen(String operation) {
		Long openTime = circuitBreakerOpenTimes.get(operation);
		if (openTime == null)
			return false;

		if (System.currentTimeMillis() - openTime > CIRCUIT_BREAKER_TIMEOUT_MS) {
			circuitBreakerOpenTimes.remove(operation);
			failureCounters.remove(operation);
			log.info("🟢 Circuit breaker CLOSED for operation: {}", operation);
			return false;
		}
		return true;
	}

	private void openCircuitBreaker(String operation) {
		circuitBreakerOpenTimes.put(operation, System.currentTimeMillis());
		log.warn("🔴 Circuit breaker OPENED for operation: {}", operation);
	}

	private void recordSuccess(String operation) {
		failureCounters.remove(operation);
		successfulInvocations.incrementAndGet();
	}

	private void recordFailure(String operation) {
		int failures = failureCounters.computeIfAbsent(operation, k -> new AtomicInteger(0)).incrementAndGet();
		if (failures >= CIRCUIT_BREAKER_FAILURE_THRESHOLD) {
			openCircuitBreaker(operation);
		}
		failedInvocations.incrementAndGet();
	}

	private long calculateExponentialBackoffDelay(int attempt) {
		long exponentialDelay = (long) Math.pow(2, attempt - 1) * BASE_RETRY_DELAY_MS;
		long delay = Math.min(exponentialDelay, MAX_RETRY_DELAY_MS);

		// Add jitter (0-25% of delay) to prevent thundering herd
		long jitter = (long) (delay * 0.25 * Math.random());
		return delay + jitter;
	}

	private List<List<Map<String, Object>>> createBatches(List<Map<String, Object>> items, int batchSize) {
		List<List<Map<String, Object>>> batches = new ArrayList<>();
		for (int i = 0; i < items.size(); i += batchSize) {
			int end = Math.min(i + batchSize, items.size());
			batches.add(items.subList(i, end));
		}
		return batches;
	}

	private Map<String, Object> createBatchPayload(String sessionId, String analysisId, String repository,
			String branch, List<Map<String, Object>> items, String stage, int scanNumber, int batchNumber,
			int totalBatches) {

		Map<String, Object> payload = new HashMap<>();
		payload.put("sessionId", sessionId);
		payload.put("analysisId", analysisId);
		payload.put("repository", repository);
		payload.put("branch", branch);
		payload.put(stage.equals("screening") ? "files" : "files", items);
		payload.put("stage", stage);
		payload.put("scanNumber", scanNumber);
		payload.put("batchInfo",
				Map.of("batchNumber", batchNumber, "totalBatches", totalBatches, "batchSize", items.size()));
		payload.put("timestamp", System.currentTimeMillis());
		return payload;
	}

	/**
	 * Monitoring and health check methods
	 */
	public Map<String, Object> getHealthMetrics() {
		Map<String, Object> metrics = new HashMap<>();
		metrics.put("totalInvocations", totalInvocations.get());
		metrics.put("successfulInvocations", successfulInvocations.get());
		metrics.put("failedInvocations", failedInvocations.get());
		metrics.put("rateLimitedInvocations", rateLimitedInvocations.get());
		metrics.put("successRate",
				totalInvocations.get() > 0 ? (double) successfulInvocations.get() / totalInvocations.get() * 100 : 0.0);
		metrics.put("activeCircuitBreakers", circuitBreakerOpenTimes.size());
		metrics.put("activeLocks", analysisLocks.size());
		return metrics;
	}

	public void logHealthMetrics() {
		Map<String, Object> metrics = getHealthMetrics();
		log.info(
				"📊 Lambda Service Health: Total={}, Success={}, Failed={}, RateLimited={}, SuccessRate={}%, CircuitBreakers={}, Locks={}",
				metrics.get("totalInvocations"), metrics.get("successfulInvocations"), metrics.get("failedInvocations"),
				metrics.get("rateLimitedInvocations"), String.format("%.1f", metrics.get("successRate")),
				metrics.get("activeCircuitBreakers"), metrics.get("activeLocks"));
	}
}