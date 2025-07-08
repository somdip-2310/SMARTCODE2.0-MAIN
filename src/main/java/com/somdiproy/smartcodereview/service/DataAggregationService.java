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
import com.somdiproy.smartcodereview.util.SeverityComparator;

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
	public DataAggregationService(AnalysisRepository analysisRepository, IssueDetailsRepository issueDetailsRepository,
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
	 * Get detection results from cache
	 */
	public List<Map<String, Object>> getDetectionResults(String analysisId) {
		LambdaResults results = lambdaResultsCache.get(analysisId);
		if (results != null && results.getDetectedIssues() != null) {
			return results.getDetectedIssues();
		}
		return new ArrayList<>();
	}

	/**
	 * Store suggestion results from Lambda
	 */
	public void storeSuggestionResults(String analysisId, String suggestionResponseJson) {
		try {
			// Log raw response for debugging
			log.debug("Raw suggestion response for analysis {}: {}", analysisId,
					suggestionResponseJson != null
							? suggestionResponseJson.substring(0, Math.min(200, suggestionResponseJson.length()))
							: "null");

			// Validate and clean response before parsing
			String cleanedResponse = validateAndCleanResponse(suggestionResponseJson);

			if (cleanedResponse == null || cleanedResponse.trim().isEmpty()) {
				log.warn("Empty or invalid suggestion response for analysis {}, creating fallback", analysisId);
				createFallbackSuggestionResponse(analysisId);
				return;
			}

			// Additional validation - ensure it's valid JSON
			if (!isValidJson(cleanedResponse)) {
				log.warn("Response is not valid JSON for analysis {}, creating synthetic response", analysisId);
				createSyntheticJsonResponse(analysisId, suggestionResponseJson);
				return;
			}

			Map<String, Object> response = objectMapper.readValue(cleanedResponse, Map.class);
			getLambdaResults(analysisId).setSuggestionResponse(response);
			log.info("Stored suggestion results for analysis {}", analysisId);
		} catch (Exception e) {
			log.error("Failed to parse suggestion response for analysis {}: {}", analysisId, e.getMessage());
			log.debug("Raw response content: {}", suggestionResponseJson);
			createSyntheticJsonResponse(analysisId, suggestionResponseJson);
		}
	}

	/**
	 * Validate and clean Lambda response to ensure it's valid JSON
	 */
	private String validateAndCleanResponse(String response) {
		if (response == null || response.trim().isEmpty()) {
			return null;
		}

		String trimmedResponse = response.trim();

		// Check for plain text responses that start with SUCCESS, FAILURE, etc.
		if (trimmedResponse.startsWith("SUCCESS") || trimmedResponse.startsWith("FAILURE")
				|| trimmedResponse.startsWith("ERROR") || trimmedResponse.startsWith("COMPLETED")
				|| trimmedResponse.startsWith("PARTIAL") || trimmedResponse.startsWith("TIMEOUT")) {
			log.warn("Received plain text response instead of JSON: {}",
					trimmedResponse.substring(0, Math.min(100, trimmedResponse.length())));
			return null;
		}

		// Ensure response starts and ends with JSON delimiters
		if (!trimmedResponse.startsWith("{") && !trimmedResponse.startsWith("[")) {
			// Try to extract JSON from within the response
			int jsonStart = trimmedResponse.indexOf("{");
			int jsonEnd = trimmedResponse.lastIndexOf("}");

			if (jsonStart != -1 && jsonEnd != -1 && jsonStart < jsonEnd) {
				trimmedResponse = trimmedResponse.substring(jsonStart, jsonEnd + 1);
				log.debug("Extracted JSON from response: {}",
						trimmedResponse.substring(0, Math.min(100, trimmedResponse.length())));
			} else {
				log.warn("No valid JSON found in response");
				return null;
			}
		}

		return trimmedResponse;
	}

	/**
	 * Validate if a string is valid JSON
	 */
	private boolean isValidJson(String jsonString) {
		try {
			objectMapper.readTree(jsonString);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Create synthetic JSON response from plain text Lambda response
	 */
	private void createSyntheticJsonResponse(String analysisId, String originalResponse) {
		try {
			Map<String, Object> syntheticResponse = new HashMap<>();

			// Determine status from plain text response
			String status = "partial_success";
			String message = "Suggestions completed with text response";

			if (originalResponse != null) {
				String response = originalResponse.trim().toUpperCase();
				if (response.startsWith("SUCCESS")) {
					status = "success";
					message = "Suggestions completed successfully";
				} else if (response.startsWith("FAILURE") || response.startsWith("ERROR")) {
					status = "error";
					message = "Suggestions generation encountered errors";
				} else if (response.startsWith("COMPLETED")) {
					status = "success";
					message = "Suggestions generation completed";
				}
			}

			syntheticResponse.put("status", status);
			syntheticResponse.put("analysisId", analysisId);
			syntheticResponse.put("suggestions", new ArrayList<>());
			syntheticResponse.put("summary",
					Map.of("totalSuggestions", 0, "tokensUsed", 0, "totalCost", 0.0, "message", message,
							"originalResponse",
							originalResponse != null
									? originalResponse.substring(0, Math.min(100, originalResponse.length()))
									: "null"));
			syntheticResponse.put("metadata", Map.of("responseType", "synthetic", "timestamp",
					System.currentTimeMillis(), "convertedFromPlainText", true));

			getLambdaResults(analysisId).setSuggestionResponse(syntheticResponse);
			log.info("Created synthetic JSON response for analysis {} from plain text response", analysisId);

		} catch (Exception e) {
			log.error("Failed to create synthetic response for analysis {}: {}", analysisId, e.getMessage());
			createFallbackSuggestionResponse(analysisId);
		}
	}

	/**
	 * Create fallback suggestion response when parsing fails
	 */
	private void createFallbackSuggestionResponse(String analysisId) {
		Map<String, Object> fallbackResponse = new HashMap<>();
		fallbackResponse.put("status", "partial_success");
		fallbackResponse.put("analysisId", analysisId);
		fallbackResponse.put("suggestions", new ArrayList<>());
		fallbackResponse.put("summary", Map.of("totalSuggestions", 0, "tokensUsed", 0, "totalCost", 0.0, "message",
				"Suggestions generation completed but response parsing failed"));

		getLambdaResults(analysisId).setSuggestionResponse(fallbackResponse);
		log.info("Created fallback suggestion response for analysis {}", analysisId);
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
			// Create and save individual issues with suggestions
			List<Issue> issues = createIssuesWithSuggestions(analysisId, results);

			// Sort issues before saving to ensure consistent ordering
			issues.sort(SeverityComparator.BY_SEVERITY_DESC);
			log.info("📊 Sorted {} issues by severity before saving", issues.size());

			issueDetailsRepository.saveAll(issues);

			log.info("Successfully aggregated and saved {} issues for analysis {}", issues.size(), analysisId);

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
				log.debug("No suggestion response for analysis {}, using default token/cost data",
						analysis.getAnalysisId());
			}
		} catch (Exception e) {
			log.error("❌ Failed to extract token usage/costs for analysis {}: {}", analysis.getAnalysisId(),
					e.getMessage());
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
			throw new IllegalStateException(
					"AnalysisResult validation failed for analysis: " + analysis.getAnalysisId());
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

		// FALLBACK: If no suggestions found in response, try to fetch from DynamoDB
		// directly
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
						if (issue.getType().equals(data.get("issueType")) || issue.getFile().equals(data.get("file"))) {
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
					log.warn("⚠️ No suggestion found for issue: {} (type: {}, file: {})", issueId, issue.getType(),
							issue.getFile());
				}

				issues.add(issue);
			}
		}

		// Sort all issues by severity before returning
		issues.sort(SeverityComparator.BY_SEVERITY_DESC);
		log.info("✅ Sorted {} total issues by severity (CRITICAL: {}, HIGH: {}, MEDIUM: {}, LOW: {})", issues.size(),
				issues.stream().filter(i -> "CRITICAL".equals(i.getSeverity())).count(),
				issues.stream().filter(i -> "HIGH".equals(i.getSeverity())).count(),
				issues.stream().filter(i -> "MEDIUM".equals(i.getSeverity())).count(),
				issues.stream().filter(i -> "LOW".equals(i.getSeverity())).count());

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
	private Map<String, Map<String, Object>> fetchSuggestionsFromDynamoDB(String analysisId,
			List<Map<String, Object>> detectedIssues) {
		Map<String, Map<String, Object>> suggestionsByIssueId = new HashMap<>();

		try {
			// Try to query issue-details table for existing suggestions
			for (Map<String, Object> issue : detectedIssues) {
				String issueId = (String) issue.get("id");
				if (issueId != null) {
					// Check if this issue has a suggestion stored
					Optional<Issue> existingIssue = issueDetailsRepository.findByAnalysisIdAndIssueId(analysisId,
							issueId);
					if (existingIssue.isPresent() && existingIssue.get().getSuggestion() != null) {
						// Convert existing suggestion to the format expected by
						// createIssuesWithSuggestions
						Map<String, Object> suggestionData = convertSuggestionToMap(
								existingIssue.get().getSuggestion());
						suggestionData.put("issueId", issueId);
						suggestionsByIssueId.put(issueId, suggestionData);
						log.debug("Found existing suggestion for issue: {}", issueId);
					}
				}
			}

			log.info("Retrieved {} existing suggestions from DynamoDB for analysis {}", suggestionsByIssueId.size(),
					analysisId);

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
		// Set title with enhanced fallback logic
		String title = getStringValue(issueData, "title");
		if (title == null || title.isEmpty()) {
			title = generateHumanReadableTitle(issueData);
		}
		issue.setTitle(title);

		// First try to get enhanced description from suggestion
		// Priority 1: Try to get enhanced description from suggestion
		String description = null;
		Map<String, Object> suggestion = (Map<String, Object>) issueData.get("suggestion");
		if (suggestion != null) {
			// Check multiple possible fields where description might be
			description = getStringValue(suggestion, "issueDescription");
			if (description == null || description.isEmpty()) {
				description = getStringValue(suggestion, "description");
			}
		}

		// Priority 2: Get description from detection results (this should always exist)
		if (description == null || description.isEmpty()) {
			description = getStringValue(issueData, "description");
			if (description != null && !description.isEmpty()) {
				log.debug("✓ Using description from detection results for issue {}", getStringValue(issueData, "id"));
			}
		}

		// Priority 3: Check if description is nested in other fields
		if (description == null || description.isEmpty()) {
			// Sometimes the description might be in a nested structure
			Object descObj = issueData.get("description");
			if (descObj instanceof Map) {
				Map<String, Object> descMap = (Map<String, Object>) descObj;
				description = getStringValue(descMap, "text");
				if (description == null || description.isEmpty()) {
					description = getStringValue(descMap, "value");
				}
			}
		}

		// Final fallback: This should rarely happen if Lambda is working correctly
		if (description == null || description.isEmpty()) {
			String type = getStringValue(issueData, "type", "Unknown");
			String severity = getStringValue(issueData, "severity", "MEDIUM");
			String category = getStringValue(issueData, "category", "general");

			log.error(
					"❌ No description found from Lambda for {} issue. This indicates a problem with the Lambda response.",
					type);

			// Create a meaningful fallback that indicates the issue
			description = generateDetailedDescription(type, severity, category);
		}

		issue.setDescription(description);
		issue.setSeverity(getStringValue(issueData, "severity", "MEDIUM").toUpperCase());
		issue.setCategory(getStringValue(issueData, "category", "GENERAL"));
		// Enhanced file path extraction with multiple fallback strategies
		String filePath = getStringValue(issueData, "file");

		// Debug logging to trace the issue
		log.debug("Extracting file path for issue {} - initial value: {}", getStringValue(issueData, "id"), filePath);

		if (filePath == null || filePath.trim().isEmpty() || "unknown".equalsIgnoreCase(filePath)) {
			// Try multiple possible field names
			String[] possibleFields = { "path", "filePath", "filename", "fileName", "location", "source", "fileInput" };
			for (String field : possibleFields) {
				String value = getStringValue(issueData, field);
				if (value != null && !value.trim().isEmpty() && !"unknown".equalsIgnoreCase(value)) {
					filePath = value;
					log.debug("Found file path in field '{}': {}", field, filePath);
					break;
				}
			}

			// If still not found, check if it's nested in metadata
			if (filePath == null || filePath.trim().isEmpty()) {
				Map<String, Object> metadata = (Map<String, Object>) issueData.get("metadata");
				if (metadata != null) {
					filePath = getStringValue(metadata, "file");
					if (filePath == null || filePath.trim().isEmpty()) {
						filePath = getStringValue(metadata, "path");
					}
					if (filePath == null || filePath.trim().isEmpty()) {
						filePath = getStringValue(metadata, "filename");
					}
				}
			}

			// Check if file info is in a nested structure
			if (filePath == null || filePath.trim().isEmpty()) {
				Object fileObj = issueData.get("fileInput");
				if (fileObj instanceof Map) {
					Map<String, Object> fileMap = (Map<String, Object>) fileObj;
					filePath = getStringValue(fileMap, "path");
					if (filePath == null || filePath.trim().isEmpty()) {
						filePath = getStringValue(fileMap, "name");
					}
				}
			}

			// Try to extract from code snippet or description
			if (filePath == null || filePath.trim().isEmpty() || "unknown".equalsIgnoreCase(filePath)) {
				String code = getStringValue(issueData, "code");
				String codeSnippet = getStringValue(issueData, "codeSnippet");
				if (code != null && code.contains("// File:")) {
					int start = code.indexOf("// File:") + 8;
					int end = code.indexOf("\n", start);
					if (end > start) {
						filePath = code.substring(start, end).trim();
					}
				} else if (codeSnippet != null && codeSnippet.contains("// File:")) {
					int start = codeSnippet.indexOf("// File:") + 8;
					int end = codeSnippet.indexOf("\n", start);
					if (end > start) {
						filePath = codeSnippet.substring(start, end).trim();
					}
				}
			}
		}

		// Final validation and default
		if (filePath == null || filePath.trim().isEmpty() || "unknown".equalsIgnoreCase(filePath)) {
			// Generate a meaningful default based on issue type
			String type = getStringValue(issueData, "type", "issue");
			filePath = String.format("%s.java", type.toLowerCase().replace("_", "-"));
			log.warn("⚠️ File path not found for issue {}, type: {}, generated default: {}",
					getStringValue(issueData, "id"), type, filePath);
		} else {
			log.info("✅ Successfully extracted file path: {} for issue: {}", filePath, getStringValue(issueData, "id"));
		}

		issue.setFile(filePath);

		// Enhanced line number extraction
		// Enhanced line number extraction
		Integer lineNumber = extractLineNumber(issueData);
		if (lineNumber == null || lineNumber <= 0) {
			// If we couldn't extract line number, log it
			String type = getStringValue(issueData, "type");
			String file = issue.getFile();
			log.warn("⚠️ No line number found for {} issue in file {}, defaulting to 1", type, file);
			lineNumber = 1;
		}
		issue.setLine(lineNumber);
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
	 * Resolve file path with multiple fallback strategies
	 */
	private String resolveFilePath(Map<String, Object> issueData, List<?> screenedFiles) {
		// First try direct file path
		String filePath = getStringValue(issueData, "file");
		if (filePath != null && !filePath.trim().isEmpty() && !"unknown".equalsIgnoreCase(filePath)) {
			return filePath;
		}

		// Try alternative fields
		String[] pathFields = { "path", "filePath", "filename", "fileName", "location" };
		for (String field : pathFields) {
			String path = getStringValue(issueData, field);
			if (path != null && !path.trim().isEmpty()) {
				return path;
			}
		}

		// Try to match from screened files if available
		if (screenedFiles != null && !screenedFiles.isEmpty()) {
			String issueId = getStringValue(issueData, "id");
			for (Object fileObj : screenedFiles) {
				if (fileObj instanceof Map) {
					Map<String, Object> file = (Map<String, Object>) fileObj;
					String screenedPath = getStringValue(file, "path");
					if (screenedPath != null) {
						// This is a simple fallback - in production, you'd want better matching logic
						return screenedPath;
					}
				}
			}
		}

		log.error("❌ Could not resolve file path for issue: {}", issueData);
		return "Unknown File";
	}

	/**
	 * Generate detailed description based on issue type
	 */
	private String generateDetailedDescription(String type, String severity, String category) {
		String cleanType = type.replace("_", " ").toLowerCase();

		// Security issue descriptions
		if ("security".equalsIgnoreCase(category)) {
			switch (type.toUpperCase()) {
			case "SQL_INJECTION":
				return "SQL injection vulnerability detected where user input is directly concatenated into database queries. This allows attackers to manipulate queries and access unauthorized data. The vulnerability can lead to data breaches, unauthorized data modification, or complete database compromise.";
			case "XSS":
			case "CROSS_SITE_SCRIPTING":
				return "Cross-site scripting (XSS) vulnerability found where user input is rendered without proper sanitization. Attackers can inject malicious scripts that execute in other users' browsers. This can lead to session hijacking, data theft, or defacement.";
			case "INSECURE_DESERIALIZATION":
				return "Insecure deserialization vulnerability detected where untrusted data is deserialized without validation. This can allow attackers to execute arbitrary code on the server. The impact includes remote code execution and complete system compromise.";
			case "HARDCODED_CREDENTIALS":
				return "Hardcoded credentials found in the source code, exposing sensitive authentication information. Anyone with access to the code can use these credentials to gain unauthorized access. This violates security best practices and compliance requirements.";
			case "WEAK_CRYPTOGRAPHY":
			case "CRYPTOGRAPHIC_WEAKNESS":
				return "Weak cryptographic algorithm or implementation detected that can be easily broken by attackers. This compromises the confidentiality and integrity of encrypted data. Modern, secure algorithms should be used instead.";
			case "PATH_TRAVERSAL":
				return "Path traversal vulnerability detected where user input is used to access files without validation. Attackers can access sensitive files outside the intended directory. This can lead to information disclosure or system compromise.";
			case "COMMAND_INJECTION":
				return "Command injection vulnerability found where user input is passed to system commands. Attackers can execute arbitrary commands on the server. This can lead to complete system takeover.";
			default:
				return String.format(
						"Security vulnerability of type '%s' detected with %s severity. This issue can compromise application security and should be addressed immediately. Review the code to apply appropriate security controls.",
						cleanType, severity.toLowerCase());
			}
		}

		// Performance issue descriptions
		if ("performance".equalsIgnoreCase(category)) {
			switch (type.toUpperCase()) {
			case "INEFFICIENT_LOOP":
			case "NESTED_LOOPS":
				return "Inefficient loop structure detected that can cause performance degradation. The current implementation has high time complexity and may cause slowdowns with large datasets. Consider optimizing the algorithm or using more efficient data structures.";
			case "MEMORY_LEAK":
				return "Potential memory leak detected where resources are not properly released. This can lead to increased memory consumption over time and eventual application crashes. Ensure all resources are properly closed or disposed.";
			case "DATABASE_N_PLUS_ONE":
			case "N_PLUS_ONE_QUERY":
				return "N+1 query problem detected where multiple database queries are executed in a loop. This causes significant performance issues as data volume grows. Use eager loading or batch queries to reduce database round trips.";
			case "BLOCKING_IO":
				return "Blocking I/O operation detected in a performance-critical path. This can cause thread starvation and reduced throughput. Consider using asynchronous operations or caching to improve performance.";
			default:
				return String.format(
						"Performance issue of type '%s' detected that can impact application responsiveness. This %s severity issue affects system efficiency. Optimize the implementation to improve performance.",
						cleanType, severity.toLowerCase());
			}
		}

		// Code quality descriptions
		return String.format(
				"Code quality issue of type '%s' detected with %s severity. This affects code maintainability and should be refactored. Following best practices will improve code readability and reduce technical debt.",
				cleanType, severity.toLowerCase());
	}

	/**
	 * Extract line number from various possible formats - Enhanced version
	 */
	private Integer extractLineNumber(Map<String, Object> issueData) {
		// First priority: Try direct line fields
		String[] lineKeys = { "line", "lineNumber", "startLine", "line_number", "lineNum" };

		for (String key : lineKeys) {
			Object lineValue = issueData.get(key);
			if (lineValue != null) {
				try {
					String lineStr = lineValue.toString().trim();
					// Handle various formats
					if (lineStr.matches("\\d+")) {
						// Pure number
						int line = Integer.parseInt(lineStr);
						if (line > 0) {
							log.debug("✓ Found line number {} from field '{}'", line, key);
							return line;
						}
					} else if (lineStr.contains("-")) {
						// Range format: "10-15"
						String firstPart = lineStr.split("-")[0].trim();
						if (firstPart.matches("\\d+")) {
							int line = Integer.parseInt(firstPart);
							if (line > 0) {
								log.debug("✓ Found line number {} from range in field '{}'", line, key);
								return line;
							}
						}
					}
				} catch (Exception e) {
					// Continue to next key
				}
			}
		}

		// Second priority: Extract from code snippet that contains line number comments
		String codeSnippet = getStringValue(issueData, "code");
		if (codeSnippet == null || codeSnippet.isEmpty()) {
			codeSnippet = getStringValue(issueData, "codeSnippet");
		}

		if (codeSnippet != null && !codeSnippet.isEmpty()) {
			// Look for line number comments added by screening Lambda
			// Patterns: "// Line 123", "# Line 123", "// L123"
			java.util.regex.Pattern[] patterns = {
					java.util.regex.Pattern.compile("//\\s*Line\\s*(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE),
					java.util.regex.Pattern.compile("#\\s*Line\\s*(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE),
					java.util.regex.Pattern.compile("//\\s*L(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE) };

			for (java.util.regex.Pattern pattern : patterns) {
				java.util.regex.Matcher matcher = pattern.matcher(codeSnippet);
				if (matcher.find()) {
					try {
						int line = Integer.parseInt(matcher.group(1));
						if (line > 0) {
							log.debug("✓ Extracted line number {} from code snippet comment", line);
							return line;
						}
					} catch (NumberFormatException e) {
						// Continue to next pattern
					}
				}
			}
		}

		// Third priority: Extract from description if it mentions line number
		String description = getStringValue(issueData, "description");
		if (description != null && !description.isEmpty()) {
			// Look for patterns like "at line 45", "on line 123", "line: 123"
			java.util.regex.Pattern descPattern = java.util.regex.Pattern
					.compile("(?:at|on|in)?\\s*line[\\s:]+([0-9]+)", java.util.regex.Pattern.CASE_INSENSITIVE);
			java.util.regex.Matcher matcher = descPattern.matcher(description);
			if (matcher.find()) {
				try {
					int line = Integer.parseInt(matcher.group(1));
					if (line > 0) {
						log.debug("✓ Extracted line number {} from description", line);
						return line;
					}
				} catch (NumberFormatException e) {
					// Continue
				}
			}
		}

		// Fourth priority: Try location field
		String location = getStringValue(issueData, "location");
		if (location != null && location.contains(":")) {
			try {
				String lineStr = location.substring(location.lastIndexOf(":") + 1).trim();
				if (lineStr.matches("\\d+")) {
					int line = Integer.parseInt(lineStr);
					if (line > 0) {
						log.debug("✓ Extracted line number {} from location field", line);
						return line;
					}
				}
			} catch (Exception e) {
				// Continue
			}
		}

		// Log detailed information about what we tried
		log.warn(
				"⚠️ Could not extract line number from issue data. Tried fields: {}, Code snippet length: {}, Description length: {}",
				issueData.keySet(), codeSnippet != null ? codeSnippet.length() : 0,
				description != null ? description.length() : 0);

		// Log sample of code snippet to debug
		if (codeSnippet != null && codeSnippet.length() > 0) {
			String sample = codeSnippet.substring(0, Math.min(200, codeSnippet.length()));
			log.debug("Code snippet sample: {}", sample);
		}

		// Return 1 as default
		return 1;
	}

	/**
	 * Create Suggestion from suggestion data
	 */
	private Suggestion createSuggestion(Map<String, Object> suggestionData) {
		Suggestion suggestion = new Suggestion();

		// Set issue description
		suggestion.setIssueDescription(getStringValue(suggestionData, "issueDescription"));

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

			if ("SECURITY".equalsIgnoreCase(category) || "security".equalsIgnoreCase(category))
				securityIssues++;
			else if ("PERFORMANCE".equalsIgnoreCase(category) || "performance".equalsIgnoreCase(category))
				performanceIssues++;
			else if ("QUALITY".equalsIgnoreCase(category) || "quality".equalsIgnoreCase(category)
					|| "GENERAL".equalsIgnoreCase(category))
				qualityIssues++;

			if ("CRITICAL".equalsIgnoreCase(severity))
				criticalCount++;
			else if ("HIGH".equalsIgnoreCase(severity))
				highCount++;
		}

		// Debug logging
		log.info("Score calculation - Security: {}, Performance: {}, Quality: {} issues", securityIssues,
				performanceIssues, qualityIssues);

		// Calculate scores (10 = perfect, 0 = worst)
		// Use logarithmic scale to handle high issue counts better
		double securityScore = 10.0 - Math.min(10.0, Math.log10(securityIssues + 1) * 2.5 + (criticalCount * 0.5));
		double performanceScore = 10.0 - Math.min(10.0, Math.log10(performanceIssues + 1) * 2.0);
		double qualityScore = qualityIssues == 0 ? 10.0
				: (10.0 - Math.min(10.0, Math.log10(qualityIssues + 1) * 1.5 + (highCount * 0.3)));

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
					progress.put("totalIssues",
							analysisResult.getSummary() != null ? analysisResult.getSummary().getTotalIssues() : 0);
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
	 * Check if analysis is still in progress (you may need to adjust this based on
	 * your tracking)
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
		if (analysisResult == null)
			return 0;

		String status = analysisResult.getStatus();
		if (status == null)
			return 0;

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
	 * Update analysis progress (helper method for Lambda functions to update
	 * status)
	 */
	public void updateAnalysisProgress(String analysisId, String status, Object data) {
		try {
			log.info("📊 Updating analysis progress: {} -> {}", analysisId, status);

			// For in-progress updates, we can store in cache or a separate tracking
			// mechanism
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