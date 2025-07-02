package com.somdiproy.smartcodereview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.somdiproy.smartcodereview.service.GitHubService.GitHubFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.TimeUnit;
import software.amazon.awssdk.core.exception.SdkClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LambdaInvokerService {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LambdaInvokerService.class);

	private final LambdaClient lambdaClient;
	private static final int DETECTION_BATCH_SIZE = 2; // Maximum 2 files per batch
	private static final int MAX_PAYLOAD_SIZE = 25000; // 25KB max for even faster processingprivate static final Duration LAMBDA_TIMEOUT = Duration.ofMinutes(15);
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Value("${aws.lambda.functions.screening}")
	private String screeningFunctionArn;

	@Value("${aws.lambda.functions.detection}")
	private String detectionFunctionArn;

	@Value("${aws.lambda.functions.suggestions}")
	private String suggestionsFunctionArn;

	@Autowired
	public LambdaInvokerService(LambdaClient lambdaClient) {
		// Use the injected LambdaClient from AWSConfig
		this.lambdaClient = lambdaClient;
	}

	public List<Map<String, Object>> invokeScreening(String sessionId, String analysisId, String repository,
	        String branch, List<GitHubFile> files, int scanNumber) {
	    try {
	        // Convert GitHubFile to the structure expected by Lambda
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

	        // Check if batch processing is needed
	        String testPayloadJson = objectMapper.writeValueAsString(Map.of("files", fileInputs));
	        boolean needsBatching = testPayloadJson.length() > 200000; // 200KB threshold
	        
	        if (!needsBatching) {
	            // Process all files in a single request
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

	            InvokeRequest request = InvokeRequest.builder()
	                .functionName(screeningFunctionArn)
	                .invocationType(InvocationType.REQUEST_RESPONSE)
	                .payload(SdkBytes.fromUtf8String(payloadJson))
	                .build();

	            InvokeResponse response = lambdaClient.invoke(request);
	            String responseJson = response.payload().asUtf8String();

	            log.info("📥 Screening Lambda response received, size: {} bytes", responseJson.length());

	            // Parse the response
	            Map<String, Object> responseMap = objectMapper.readValue(responseJson, Map.class);
	            String status = (String) responseMap.get("status");

	            if ("error".equals(status)) {
	                log.error("❌ Lambda returned error: {}", responseMap.get("errors"));
	                return new ArrayList<>();
	            }

	            // Return the complete screened files data
	            List<Map<String, Object>> screenedFiles = (List<Map<String, Object>>) responseMap.get("files");
	            return screenedFiles != null ? screenedFiles : new ArrayList<>();
	            
	        } else {
	            // Batch processing for large file sets
	            log.info("📦 Large payload detected ({} files, {} bytes). Using batch processing...", 
	                files.size(), testPayloadJson.length());
	            
	            List<List<Map<String, Object>>> batches = createBatches(fileInputs, 10); // 10 files per batch
	            List<Map<String, Object>> allScreenedFiles = new ArrayList<>();
	            
	            for (int i = 0; i < batches.size(); i++) {
	                try {
	                    Map<String, Object> batchPayload = new HashMap<>();
	                    batchPayload.put("sessionId", sessionId);
	                    batchPayload.put("analysisId", analysisId);
	                    batchPayload.put("repository", repository);
	                    batchPayload.put("branch", branch);
	                    batchPayload.put("files", batches.get(i));
	                    batchPayload.put("stage", "screening");
	                    batchPayload.put("scanNumber", scanNumber);
	                    batchPayload.put("batchNumber", i + 1);
	                    batchPayload.put("totalBatches", batches.size());
	                    batchPayload.put("timestamp", System.currentTimeMillis());
	                    
	                    String payloadJson = objectMapper.writeValueAsString(batchPayload);
	                    log.info("📤 Invoking screening Lambda batch {}/{} with {} files, payload size: {} bytes", 
	                        i + 1, batches.size(), batches.get(i).size(), payloadJson.length());

	                    InvokeRequest request = InvokeRequest.builder()
	                        .functionName(screeningFunctionArn)
	                        .invocationType(InvocationType.REQUEST_RESPONSE)
	                        .payload(SdkBytes.fromUtf8String(payloadJson))
	                        .build();

	                    InvokeResponse response = lambdaClient.invoke(request);
	                    String responseJson = response.payload().asUtf8String();
	                    
	                    Map<String, Object> responseMap = objectMapper.readValue(responseJson, Map.class);
	                    String status = (String) responseMap.get("status");
	                    
	                    if ("success".equals(status) || status == null) { // Handle both cases
	                        List<Map<String, Object>> batchFiles = (List<Map<String, Object>>) responseMap.get("files");
	                        if (batchFiles != null) {
	                            allScreenedFiles.addAll(batchFiles);
	                            log.info("✅ Batch {}/{} processed successfully: {} files screened", 
	                                i + 1, batches.size(), batchFiles.size());
	                        }
	                    } else if ("error".equals(status)) {
	                        log.warn("⚠️ Batch {}/{} returned error: {}", 
	                            i + 1, batches.size(), responseMap.get("errors"));
	                    }
	                    
	                    // Add small delay between batches to avoid rate limiting
	                    if (i < batches.size() - 1) {
	                        Thread.sleep(2000); // Reduced from 5000ms to 2000ms
	                    }
	                    
	                } catch (Exception batchError) {
	                    log.error("❌ Failed to process batch {}/{}: {}", 
	                        i + 1, batches.size(), batchError.getMessage());
	                    // Continue with other batches even if one fails
	                }
	            }
	            
	            log.info("📊 Batch processing complete: {} files screened out of {} total files", 
	                allScreenedFiles.size(), files.size());
	            return allScreenedFiles;
	        }
	        
	    } catch (Exception e) {
	        log.error("Failed to invoke screening Lambda", e);
	        return new ArrayList<>();
	    }
	}

	public List<Map<String, Object>> invokeDetection(String sessionId, String analysisId, String repository, String branch,
	        List<Map<String, Object>> screenedFiles, int scanNumber) {
	    try {
	        // Check if batch processing is needed based on payload size or file count
	        String testPayload = objectMapper.writeValueAsString(screenedFiles);
	        if (testPayload.length() > MAX_PAYLOAD_SIZE || screenedFiles.size() > DETECTION_BATCH_SIZE) {
	            log.info("📦 Large payload detected ({} files, {} bytes). Using batch processing...", 
	                screenedFiles.size(), testPayload.length());
	            return invokeDetectionInBatches(sessionId, analysisId, repository, branch, screenedFiles, scanNumber);
	        }
	        
	        // Single invocation for small payloads
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

	        InvokeRequest request = InvokeRequest.builder()
	                .functionName(detectionFunctionArn)
	                .invocationType(InvocationType.REQUEST_RESPONSE)
	                .payload(SdkBytes.fromUtf8String(payloadJson))  // Fixed: was batchPayloadJson
	                .build();

	        // Add retry logic
	        InvokeResponse response = null;
	        int maxRetries = 3;
	        int retryCount = 0;
	        
	        while (retryCount < maxRetries) {
	            try {
	                response = lambdaClient.invoke(request);
	                break; // Success, exit retry loop
	            } catch (SdkClientException e) {
	                retryCount++;
	                if (retryCount >= maxRetries) {
	                    throw e; // Max retries reached, throw exception
	                }
	                log.warn("⚠️ Lambda invocation failed, retrying ({}/{}): {}", 
	                        retryCount, maxRetries, e.getMessage());
	                
	                // Wait before retry with exponential backoff
	                try {
	                    TimeUnit.SECONDS.sleep(retryCount * 2);
	                } catch (InterruptedException ie) {
	                    Thread.currentThread().interrupt();
	                    throw new RuntimeException("Retry interrupted", ie);
	                }
	            }
	        }

	        String responseJson = response.payload().asUtf8String();

	        // Check for Lambda function errors
	        if (response.functionError() != null) {
	            log.error("❌ Lambda function error: {}", response.functionError());
	            return new ArrayList<>();
	        }

	        // Check response status code
	        if (response.statusCode() != 200) {
	            log.error("❌ Lambda invocation failed with status code: {}", response.statusCode());
	            return new ArrayList<>();
	        }

	        log.debug("Detection Lambda response received, size: {} bytes", responseJson.length());

	        // Parse detection response
	        Map<String, Object> responseMap = objectMapper.readValue(responseJson, Map.class);
	        String status = (String) responseMap.get("status");

	        if ("error".equals(status)) {
	            log.error("❌ Detection Lambda returned error: {}", responseMap.get("errors"));
	            return new ArrayList<>();
	        }

	        // Return the detected issues
	        List<Map<String, Object>> issues = (List<Map<String, Object>>) responseMap.get("issues");
	        return issues != null ? issues : new ArrayList<>();

	    } catch (Exception e) {
	        log.error("Failed to invoke detection Lambda", e);
	        return new ArrayList<>();
	    }
	}
	
	private List<Map<String, Object>> invokeDetectionInBatches(String sessionId, String analysisId,
	        String repository, String branch, List<Map<String, Object>> screenedFiles, int scanNumber) {
	    
	    List<Map<String, Object>> allIssues = new ArrayList<>();
	    List<List<Map<String, Object>>> batches = createBatches(screenedFiles, DETECTION_BATCH_SIZE);
	    
	    log.info("📦 Processing {} files in {} batches for detection", screenedFiles.size(), batches.size());
	    
	    int successfulBatches = 0;
	    int failedBatches = 0;
	    
	    for (int i = 0; i < batches.size(); i++) {
	        long batchStartTime = System.currentTimeMillis();
	        
	        try {
	            // Add delay between batches to avoid overwhelming Lambda
	            if (i > 0) {
	                try {
	                    Thread.sleep(1000); // 1 second delay between batches
	                } catch (InterruptedException e) {
	                    Thread.currentThread().interrupt();
	                }
	            }
	            
	            Map<String, Object> batchPayload = new HashMap<>();
	            batchPayload.put("sessionId", sessionId);
	            batchPayload.put("analysisId", analysisId);
	            batchPayload.put("repository", repository);
	            batchPayload.put("branch", branch);
	            batchPayload.put("files", batches.get(i));
	            batchPayload.put("stage", "detection");
	            batchPayload.put("scanNumber", scanNumber);
	            batchPayload.put("batchInfo", Map.of(
	                "batchNumber", i + 1,
	                "totalBatches", batches.size(),
	                "batchSize", batches.get(i).size()
	            ));
	            batchPayload.put("timestamp", System.currentTimeMillis());
	            
	            String batchPayloadJson = objectMapper.writeValueAsString(batchPayload);
	            log.info("🔍 Invoking detection batch {}/{}, payload size: {} bytes", 
	                    i + 1, batches.size(), batchPayloadJson.length());
	            
	            InvokeRequest request = InvokeRequest.builder()
	                    .functionName(detectionFunctionArn)
	                    .invocationType(InvocationType.REQUEST_RESPONSE)
	                    .payload(SdkBytes.fromUtf8String(batchPayloadJson))
	                    .build();
	            
	            // Add retry logic for batch processing
	            InvokeResponse response = null;
	            int maxRetries = 3;
	            int retryCount = 0;
	            
	            while (retryCount < maxRetries) {
	                try {
	                    response = lambdaClient.invoke(request);
	                    break; // Success, exit retry loop
	                } catch (SdkClientException e) {
	                    retryCount++;
	                    if (retryCount >= maxRetries) {
	                        throw e; // Max retries reached, throw exception
	                    }
	                    
	                    // Calculate wait time: 5, 10, 15 seconds
	                    int waitSeconds = retryCount * 5;
	                    
	                    log.warn("⚠️ Batch {}/{} Lambda invocation failed, retrying ({}/{}) after {} seconds: {}", 
	                            i + 1, batches.size(), retryCount, maxRetries, waitSeconds, e.getMessage());
	                    
	                    // Wait before retry with increased intervals
	                    try {
	                        TimeUnit.SECONDS.sleep(waitSeconds);
	                    } catch (InterruptedException ie) {
	                        Thread.currentThread().interrupt();
	                        throw new RuntimeException("Retry interrupted", ie);
	                    }
	                }
	            }
	            
	            String responseJson = response.payload().asUtf8String();
	            
	            // Check for Lambda errors
	            if (response.functionError() != null) {
	                log.error("❌ Lambda function error in batch {}/{}: {}", 
	                        i + 1, batches.size(), response.functionError());
	                failedBatches++;
	                continue;
	            }
	            
	            // Check response status code
	            if (response.statusCode() != 200) {
	                log.error("❌ Batch {}/{} invocation failed with status code: {}", 
	                        i + 1, batches.size(), response.statusCode());
	                failedBatches++;
	                continue;
	            }
	            
	            Map<String, Object> responseMap = objectMapper.readValue(responseJson, Map.class);
	            String status = (String) responseMap.get("status");
	            
	            if ("success".equals(status) || status == null) {
	                List<Map<String, Object>> batchIssues = (List<Map<String, Object>>) responseMap.get("issues");
	                if (batchIssues != null) {
	                    allIssues.addAll(batchIssues);
	                    long batchDuration = System.currentTimeMillis() - batchStartTime;
	                    log.info("✅ Batch {}/{} completed in {} seconds: {} issues found", 
	                            i + 1, batches.size(), batchDuration / 1000, batchIssues.size());
	                    successfulBatches++;
	                }
	            } else {
	                log.warn("⚠️ Batch {}/{} returned error: {}", 
	                        i + 1, batches.size(), responseMap.get("errors"));
	                failedBatches++;
	            }
	            
	        } catch (Exception e) {
	            long batchDuration = System.currentTimeMillis() - batchStartTime;
	            log.error("❌ Failed to process detection batch {}/{} after {} seconds: {}", 
	                    i + 1, batches.size(), batchDuration / 1000, e.getMessage());
	            failedBatches++;
	        }
	    }
	    
	    log.info("📊 Detection batch processing complete: {} successful, {} failed, {} total issues found", 
	            successfulBatches, failedBatches, allIssues.size());
	    
	    return allIssues;
	}
	
	private List<List<Map<String, Object>>> createBatches(List<Map<String, Object>> items, int batchSize) {
	    List<List<Map<String, Object>>> batches = new ArrayList<>();
	    for (int i = 0; i < items.size(); i += batchSize) {
	        int end = Math.min(i + batchSize, items.size());
	        batches.add(items.subList(i, end));
	    }
	    return batches;
	}
	
	public String invokeSuggestions(String sessionId, String analysisId, String repository, String branch,
	        List<Map<String, Object>> issues, int scanNumber) {
	    try {
	        // Remove artificial delay
	        Map<String, Object> payload = new HashMap<>();
	        payload.put("sessionId", sessionId);
	        payload.put("analysisId", analysisId);
	        payload.put("repository", repository);
	        payload.put("branch", branch);
	        payload.put("issues", issues);
	        payload.put("stage", "suggestions");
	        payload.put("scanNumber", scanNumber);
	        payload.put("timestamp", System.currentTimeMillis());
	        String payloadJson = objectMapper.writeValueAsString(payload);

	        InvokeRequest request = InvokeRequest.builder()
	                .functionName(suggestionsFunctionArn)
	                .invocationType(InvocationType.REQUEST_RESPONSE)
	                .payload(SdkBytes.fromUtf8String(payloadJson))
	                .build();

	        InvokeResponse response = lambdaClient.invoke(request);
	        String responseJson = response.payload().asUtf8String();

	        // Check for Lambda function errors
	        if (response.functionError() != null) {
	            log.error("❌ Suggestions Lambda function error: {}", response.functionError());
	            return null;  // Changed from 'return;' to 'return null;'
	        }

	        log.debug("Suggestions Lambda response received, size: {} bytes", responseJson.length());
	        return responseJson;

	    } catch (Exception e) {
	        log.error("Failed to invoke suggestions Lambda", e);
	        return null;
	    }
	}
}