package com.somdiproy.smartcodereview.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Session entity for managing user sessions with DynamoDB
 * Supports 1-hour sessions with 3 scan limit
 */
@DynamoDbBean
public class Session {
    
    private String sessionId;
    private String email;
    private String emailMasked;
    private String organization;
    private String otpHash;
    private Integer otpAttempts = 0;
    private Long otpExpiresAt;
    private String verificationStatus = "PENDING"; // PENDING, VERIFIED, EXPIRED
    private Long createdAt;
    private Long expiresAt;
    private Long ttl;
    private Integer scanCount = 0;
    private Integer scanLimit = 3;
    private Integer remainingScans = 3;
    private List<Scan> scans = new ArrayList<>();
    private String ipAddress;
    private String userAgent;
    private String githubToken;
    
    // Constructors
    public Session() {
        this.otpAttempts = 0;
        this.verificationStatus = "PENDING";
        this.scanCount = 0;
        this.scanLimit = 3;
        this.remainingScans = 3;
        this.scans = new ArrayList<>();
    }
    
    // DynamoDB annotations
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
        return this.scanCount < this.scanLimit && isVerified();
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
    
    // Getters and Setters
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

    @DynamoDbAttribute("verificationStatus")
    public String getVerificationStatus() {
        return verificationStatus != null ? verificationStatus : "PENDING";
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus != null ? verificationStatus : "PENDING";
    }

    // Business logic methods for backward compatibility
    public boolean isVerified() {
        return "VERIFIED".equals(this.verificationStatus);
    }

    public void setVerified(Boolean verified) {
        this.verificationStatus = Boolean.TRUE.equals(verified) ? "VERIFIED" : "PENDING";
    }

    public Boolean getVerified() {
        return "VERIFIED".equals(this.verificationStatus);
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
    
    // Builder pattern
    public static SessionBuilder builder() {
        return new SessionBuilder();
    }
    
    public static class SessionBuilder {
        private Session session = new Session();
        
        public SessionBuilder sessionId(String sessionId) {
            session.setSessionId(sessionId);
            return this;
        }
        
        public SessionBuilder email(String email) {
            session.setEmail(email);
            return this;
        }
        
        public SessionBuilder emailMasked(String emailMasked) {
            session.setEmailMasked(emailMasked);
            return this;
        }
        
        public SessionBuilder organization(String organization) {
            session.setOrganization(organization);
            return this;
        }
        
        public SessionBuilder otpHash(String otpHash) {
            session.setOtpHash(otpHash);
            return this;
        }
        
        public SessionBuilder otpAttempts(Integer otpAttempts) {
            session.setOtpAttempts(otpAttempts);
            return this;
        }
        
        public SessionBuilder otpExpiresAt(Long otpExpiresAt) {
            session.setOtpExpiresAt(otpExpiresAt);
            return this;
        }
        
        public SessionBuilder verified(Boolean verified) {
            session.setVerificationStatus(Boolean.TRUE.equals(verified) ? "VERIFIED" : "PENDING");
            return this;
        }
        
        public SessionBuilder createdAt(Long createdAt) {
            session.setCreatedAt(createdAt);
            return this;
        }
        
        public SessionBuilder expiresAt(Long expiresAt) {
            session.setExpiresAt(expiresAt);
            return this;
        }
        
        public SessionBuilder ttl(Long ttl) {
            session.setTtl(ttl);
            return this;
        }
        
        public SessionBuilder scanCount(Integer scanCount) {
            session.setScanCount(scanCount);
            return this;
        }
        
        public SessionBuilder scanLimit(Integer scanLimit) {
            session.setScanLimit(scanLimit);
            return this;
        }
        
        public SessionBuilder remainingScans(Integer remainingScans) {
            session.setRemainingScans(remainingScans);
            return this;
        }
        
        public SessionBuilder scans(List<Scan> scans) {
            session.setScans(scans);
            return this;
        }
        
        public SessionBuilder ipAddress(String ipAddress) {
            session.setIpAddress(ipAddress);
            return this;
        }
        
        public SessionBuilder userAgent(String userAgent) {
            session.setUserAgent(userAgent);
            return this;
        }
        
        public SessionBuilder githubToken(String githubToken) {
            return null;
        }
        
        public Session build() {
            return session;
        }
    }

    /**
     * Nested class for scan details
     */
    @DynamoDbBean
    public static class Scan {
        private Integer scanNumber;
        private String analysisId;
        private String repository;
        private String branch;
        private Long startedAt;
        private Long completedAt;
        
        // Constructors
        public Scan() {}
        
        // Getters and Setters
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