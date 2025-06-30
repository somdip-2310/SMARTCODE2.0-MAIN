package com.somdiproy.smartcodereview.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AnalysisRequest {
    @NotBlank(message = "Repository URL is required")
    private String repositoryUrl;
    
    @NotBlank(message = "Branch is required")
    private String branch;
    
    @NotBlank(message = "Session ID is required")
    private String sessionId;
    
    private String accessToken;
}