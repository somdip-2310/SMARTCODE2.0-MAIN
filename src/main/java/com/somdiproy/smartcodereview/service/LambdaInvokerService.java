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
import java.util.List;
import java.util.Map;

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

    public List<String> invokeScreening(List<GitHubFile> files) {
        try {
            Map<String, Object> payload = Map.of(
                "files", files,
                "action", "screen"
            );
            
            String payloadJson = objectMapper.writeValueAsString(payload);
            
            InvokeRequest request = InvokeRequest.builder()
                    .functionName(screeningFunctionArn)
                    .payload(SdkBytes.fromUtf8String(payloadJson))
                    .build();
            
            InvokeResponse response = lambdaClient.invoke(request);
            String responseJson = response.payload().asUtf8String();
            
            log.info("Screening Lambda response: {}", responseJson);
            
            // TODO: Parse response properly
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("Failed to invoke screening Lambda", e);
            return new ArrayList<>();
        }
    }
    
    public List<String> invokeDetection(List<String> files) {
        try {
            Map<String, Object> payload = Map.of(
                "files", files,
                "action", "detect"
            );
            
            String payloadJson = objectMapper.writeValueAsString(payload);
            
            InvokeRequest request = InvokeRequest.builder()
                    .functionName(detectionFunctionArn)
                    .payload(SdkBytes.fromUtf8String(payloadJson))
                    .build();
            
            InvokeResponse response = lambdaClient.invoke(request);
            String responseJson = response.payload().asUtf8String();
            
            log.info("Detection Lambda response: {}", responseJson);
            
            // TODO: Parse response properly
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("Failed to invoke detection Lambda", e);
            return new ArrayList<>();
        }
    }
    
    public void invokeSuggestions(List<String> issues) {
        try {
            Map<String, Object> payload = Map.of(
                "issues", issues,
                "action", "suggest"
            );
            
            String payloadJson = objectMapper.writeValueAsString(payload);
            
            InvokeRequest request = InvokeRequest.builder()
                    .functionName(suggestionsFunctionArn)
                    .payload(SdkBytes.fromUtf8String(payloadJson))
                    .build();
            
            InvokeResponse response = lambdaClient.invoke(request);
            String responseJson = response.payload().asUtf8String();
            
            log.info("Suggestions Lambda response: {}", responseJson);
            
        } catch (Exception e) {
            log.error("Failed to invoke suggestions Lambda", e);
        }
    }
}