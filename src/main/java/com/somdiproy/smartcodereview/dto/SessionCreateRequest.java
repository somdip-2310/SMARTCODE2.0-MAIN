package com.somdiproy.smartcodereview.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import jakarta.validation.constraints.Pattern;
/**
 * Request DTO for session creation
 */
public class SessionCreateRequest {
    
	@NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    private String email;
    
    @NotBlank(message = "GitHub token is required")
    @Pattern(regexp = "ghp_[a-zA-Z0-9]{36,}", message = "Invalid GitHub token format")
    private String githubToken;
    
    public String getGithubToken() {
        return githubToken;
    }
    
    public void setGithubToken(String githubToken) {
        this.githubToken = githubToken;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
}