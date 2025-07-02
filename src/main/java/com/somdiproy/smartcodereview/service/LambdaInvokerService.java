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
					.payload(SdkBytes.fromUtf8String(payloadJson)).build();

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
			return screenedFiles;
		} catch (Exception e) {
			log.error("Failed to invoke screening Lambda", e);
			return new ArrayList<>();
		}
	}

	public List<Map<String, Object>> invokeDetection(String sessionId, String analysisId, String repository, String branch,
			List<Map<String, Object>> screenedFiles, int scanNumber) {
		try {
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

	public void invokeSuggestions(String sessionId, String analysisId, String repository, String branch,
			List<Map<String, Object>> issues, int scanNumber) {
		try {
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