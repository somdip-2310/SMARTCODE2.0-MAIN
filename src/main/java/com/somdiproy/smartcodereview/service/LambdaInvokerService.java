package com.somdiproy.smartcodereview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.somdiproy.smartcodereview.service.GitHubService.GitHubFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LambdaInvokerService {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LambdaInvokerService.class);

	private final LambdaClient lambdaClient;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Value("${aws.lambda.functions.screening}")
	private String screeningFunctionArn;

	@Value("${aws.lambda.functions.detection}")
	private String detectionFunctionArn;

	@Value("${aws.lambda.functions.suggestions}")
	private String suggestionsFunctionArn;

	@Autowired
	public LambdaInvokerService(LambdaClient lambdaClient) {
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
	                .invocationType("RequestResponse")
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
	                        .invocationType("RequestResponse")
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
	                        Thread.sleep(5000); // 5000ms delay between batches
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
			Thread.sleep(5000);
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
					.payload(SdkBytes.fromUtf8String(payloadJson)).build();

			InvokeResponse response = lambdaClient.invoke(request);
			String responseJson = response.payload().asUtf8String();

			log.info("Detection Lambda response: {}", responseJson);

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
	private List<List<Map<String, Object>>> createBatches(List<Map<String, Object>> items, int batchSize) {
	    List<List<Map<String, Object>>> batches = new ArrayList<>();
	    for (int i = 0; i < items.size(); i += batchSize) {
	        int end = Math.min(i + batchSize, items.size());
	        batches.add(items.subList(i, end));
	    }
	    return batches;
	}
	public void invokeSuggestions(String sessionId, String analysisId, String repository, String branch,
			List<Map<String, Object>> issues, int scanNumber) {
		try {
			Thread.sleep(5000);
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

			InvokeRequest request = InvokeRequest.builder().functionName(suggestionsFunctionArn)
					.payload(SdkBytes.fromUtf8String(payloadJson)).build();

			InvokeResponse response = lambdaClient.invoke(request);
			String responseJson = response.payload().asUtf8String();

			log.info("Suggestions Lambda response: {}", responseJson);

		} catch (Exception e) {
			log.error("Failed to invoke suggestions Lambda", e);
		}
	}
}