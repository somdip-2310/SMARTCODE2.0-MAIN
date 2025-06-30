package com.somdiproy.smartcodereview.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Session entity for managing user sessions with DynamoDB
 * Supports 1-hour sessions with 3 scan limit
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class Session {
    
    private String sessionId;
    private String email;
    private String emailMasked;
    private String organization;
    private String otpHash;
    
    @Builder.Default
    private Integer otpAttempts = 0;
    
    private Long otpExpiresAt;
    
    @Builder.Default
    private Boolean verified = false;
    
    private Long createdAt;
    private Long expiresAt;
    private Long ttl;
    
    @Builder.Default
    private Integer scanCount = 0;
    
    @Builder.Default
    private Integer scanLimit = 3;
    
    @Builder.Default
    private Integer remainingScans = 3;
    
    @Builder.Default
    private List<Scan> scans = new ArrayList<>();
    
    private String ipAddress;
    private String userAgent;
    private String githubToken;
    
    @DynamoDbPartitionKey
    public String getSessionId() {
        return sessionId;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = {"EmailIndex"})
    public String getEmail() {
        return email;
    }
    
    // Business methods
    public void incrementScanCount() {
        this.scanCount = this.scanCount + 1;
        this.remainingScans = this.scanLimit - this.scanCount;
    }
    
    public boolean canPerformScan() {
        return this.scanCount < this.scanLimit && this.verified;
    }
    
    public void addScan(String analysisId, String repository, String branch) {
        Scan scan = new Scan();
        scan.setScanNumber(this.scanCount + 1);
        scan.setAnalysisId(analysisId);
        scan.setRepository(repository);
        scan.setBranch(branch);
        scan.setStartedAt(Instant.now().getEpochSecond());
        
        if (this.scans == null) {
            this.scans = new ArrayList<>();
        }
        this.scans.add(scan);
    }
    
    public boolean isExpired() {
        return Instant.now().getEpochSecond() > this.expiresAt;
    }
    
    public boolean isOtpExpired() {
        return Instant.now().getEpochSecond() > this.otpExpiresAt;
    }
    
    
    
    public String getEmailMasked() {
		return emailMasked;
	}

	public void setEmailMasked(String emailMasked) {
		this.emailMasked = emailMasked;
	}

	public String getOrganization() {
		return organization;
	}

	public void setOrganization(String organization) {
		this.organization = organization;
	}

	public String getOtpHash() {
		return otpHash;
	}

	public void setOtpHash(String otpHash) {
		this.otpHash = otpHash;
	}

	public Integer getOtpAttempts() {
		return otpAttempts;
	}

	public void setOtpAttempts(Integer otpAttempts) {
		this.otpAttempts = otpAttempts;
	}

	public Long getOtpExpiresAt() {
		return otpExpiresAt;
	}

	public void setOtpExpiresAt(Long otpExpiresAt) {
		this.otpExpiresAt = otpExpiresAt;
	}

	public Boolean getVerified() {
		return verified;
	}

	public void setVerified(Boolean verified) {
		this.verified = verified;
	}

	public Long getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Long createdAt) {
		this.createdAt = createdAt;
	}

	public Long getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(Long expiresAt) {
		this.expiresAt = expiresAt;
	}

	public Long getTtl() {
		return ttl;
	}

	public void setTtl(Long ttl) {
		this.ttl = ttl;
	}

	public Integer getScanCount() {
		return scanCount;
	}

	public void setScanCount(Integer scanCount) {
		this.scanCount = scanCount;
	}

	public Integer getScanLimit() {
		return scanLimit;
	}

	public void setScanLimit(Integer scanLimit) {
		this.scanLimit = scanLimit;
	}

	public Integer getRemainingScans() {
		return remainingScans;
	}

	public void setRemainingScans(Integer remainingScans) {
		this.remainingScans = remainingScans;
	}

	public List<Scan> getScans() {
		return scans;
	}

	public void setScans(List<Scan> scans) {
		this.scans = scans;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
	}

	public String getGithubToken() {
		return githubToken;
	}

	public void setGithubToken(String githubToken) {
		this.githubToken = githubToken;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public void setEmail(String email) {
		this.email = email;
	}



	/**
     * Nested class for scan details
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @DynamoDbBean
    public static class Scan {
        private Integer scanNumber;
        private String analysisId;
        private String repository;
        private String branch;
        private Long startedAt;
        private Long completedAt;
		public Integer getScanNumber() {
			return scanNumber;
		}
		public void setScanNumber(Integer scanNumber) {
			this.scanNumber = scanNumber;
		}
		public String getAnalysisId() {
			return analysisId;
		}
		public void setAnalysisId(String analysisId) {
			this.analysisId = analysisId;
		}
		public String getRepository() {
			return repository;
		}
		public void setRepository(String repository) {
			this.repository = repository;
		}
		public String getBranch() {
			return branch;
		}
		public void setBranch(String branch) {
			this.branch = branch;
		}
		public Long getStartedAt() {
			return startedAt;
		}
		public void setStartedAt(Long startedAt) {
			this.startedAt = startedAt;
		}
		public Long getCompletedAt() {
			return completedAt;
		}
		public void setCompletedAt(Long completedAt) {
			this.completedAt = completedAt;
		}
    }
}