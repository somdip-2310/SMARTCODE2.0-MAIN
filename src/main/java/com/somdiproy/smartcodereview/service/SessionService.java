package com.somdiproy.smartcodereview.service;

import com.somdiproy.smartcodereview.exception.InvalidOtpException;
import com.somdiproy.smartcodereview.exception.SessionNotFoundException;
import com.somdiproy.smartcodereview.model.AnalysisResult;
import com.somdiproy.smartcodereview.model.Session;
import com.somdiproy.smartcodereview.repository.SessionRepository;
import com.somdiproy.smartcodereview.util.EmailMasker;
import com.somdiproy.smartcodereview.util.OtpGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing user sessions with OTP verification
 */
@Slf4j
@Service
public class SessionService {
    
    private final SessionRepository sessionRepository;
    private final EmailService emailService;
    private final SecureTokenService secureTokenService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SessionService.class);
    
    @Value("${session.duration:3600}")
    private int sessionDurationSeconds;
    
    @Value("${session.otp-expiry:300}")
    private int otpExpirySeconds;
    
    @Value("${session.otp-length:6}")
    private int otpLength;
    
    @Autowired
    public SessionService(SessionRepository sessionRepository, 
                         EmailService emailService,
                         SecureTokenService secureTokenService) {
        this.sessionRepository = sessionRepository;
        this.emailService = emailService;
        this.secureTokenService = secureTokenService;
    }
    
    /**
     * Create a new session with GitHub token validation and send OTP
     */
    public Session createSession(String email, String githubToken, HttpServletRequest request) {
        // Validate GitHub token first
        if (!secureTokenService.isValidGitHubToken(githubToken)) {
            throw new IllegalArgumentException("Invalid GitHub token format. Token must start with 'ghp_'");
        }
        
        // Create the session
        Session session = createSession(email, request);
        
        // Store the GitHub token in secure memory-only storage
        secureTokenService.storeSessionToken(session.getSessionId(), githubToken);
        
        return session;
    }
    
    /**
     * Create a new session and send OTP (existing method for backward compatibility)
     */
    public Session createSession(String email, HttpServletRequest request) {
        // Check if session already exists for this email
        Optional<Session> existingSession = sessionRepository.findByEmail(email);
        if (existingSession.isPresent() && !existingSession.get().isExpired()) {
            // In local testing, delete existing session to see fresh OTP generation
            if ("local".equals(System.getProperty("spring.profiles.active", "local"))) {
                log.info("🔄 LOCAL TESTING: Deleting existing session to generate fresh OTP");
                deleteSession(existingSession.get().getSessionId());
                // Continue to create new session
            } else {
                log.info("Session already exists for email: {}", EmailMasker.mask(email));
                return existingSession.get();
            }
        }
        
        // Generate OTP
        String otp = OtpGenerator.generate(otpLength);
        String otpHash = passwordEncoder.encode(otp);
        
        // Extract organization from email
        String organization = extractOrganization(email);
        
        // Create session
        long now = Instant.now().getEpochSecond();
        Session session = Session.builder()
                .sessionId(UUID.randomUUID().toString())
                .email(email)
                .emailMasked(EmailMasker.mask(email))
                .organization(organization)
                .otpHash(otpHash)
                .otpAttempts(0)
                .otpExpiresAt(now + otpExpirySeconds)
                .verified(Boolean.FALSE)
                .createdAt(now)
                .expiresAt(now + sessionDurationSeconds)
                .ttl(now + sessionDurationSeconds)
                .scanCount(0)
                .scanLimit(3)
                .remainingScans(3)
                .ipAddress(getClientIp(request))
                .userAgent(request.getHeader("User-Agent"))
                .build();
        
        // Save session
        sessionRepository.save(session);
        
        // Send OTP email (will be logged in local environment)
        emailService.sendOtpEmail(email, otp);

        log.info("✅ Created new session for email: {} with sessionId: {}", 
                 EmailMasker.mask(email), session.getSessionId());
        log.info("🔐 Session Details - Email: {} | SessionId: {} | OTP Length: {} | Expires: {} minutes", 
                 EmailMasker.mask(email), session.getSessionId(), otpLength, otpExpirySeconds / 60);
        
        return session;
    }
    
    /**
     * Verify OTP and activate session
     */
    public Session verifyOtp(String sessionId, String otp) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("Session not found"));
        
        // Check if session is expired
        if (session.isExpired()) {
            throw new SessionNotFoundException("Session has expired");
        }
        
        // Check if OTP is expired
        if (session.isOtpExpired()) {
            throw new InvalidOtpException("OTP has expired");
        }
        
        // Check OTP attempts
        if (session.getOtpAttempts() >= 3) {
            throw new InvalidOtpException("Maximum OTP attempts exceeded");
        }
        
        // Verify OTP
        if (!passwordEncoder.matches(otp, session.getOtpHash())) {
            session.setOtpAttempts(session.getOtpAttempts() + 1);
            sessionRepository.update(session);
            throw new InvalidOtpException("Invalid OTP");
        }
        
        // Mark session as verified
        session.setVerificationStatus("VERIFIED");
        session.setOtpHash(null); // Clear OTP hash for security

        // Force a fresh save to DynamoDB
        sessionRepository.save(session);

        // Retrieve fresh copy to verify persistence
        Session verifiedSession = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException("Session not found after verification"));

        log.info("✅ Successfully verified OTP for sessionId: {}", sessionId);
        log.info("🔄 Session verification status: {}", verifiedSession.getVerificationStatus());
        log.info("🔄 Session isVerified(): {}", verifiedSession.isVerified());
        log.info("🔓 Session now verified: email={}, remainingScans={}", 
                 verifiedSession.getEmailMasked(), verifiedSession.getRemainingScans());

        return verifiedSession;
    }
    
    /**
     * Get session by ID
     */
    public Session getSession(String sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("Session not found"));
        
        if (session.isExpired()) {
            throw new SessionNotFoundException("Session has expired");
        }
        
        return session;
    }
    
    /**
     * Update GitHub token for existing session
     */
    public Session updateGithubToken(String sessionId, String githubToken) {
        // Validate the new token
        if (!secureTokenService.isValidGitHubToken(githubToken)) {
            throw new IllegalArgumentException("Invalid GitHub token format. Token must start with 'ghp_'");
        }
        
        // Verify session exists and is valid
        Session session = getSession(sessionId);
        
        // Update token in secure storage
        secureTokenService.storeSessionToken(sessionId, githubToken);
        
        log.info("📝 Updated GitHub token for session: {}", sessionId);
        return session;
    }
    
    /**
     * Resend OTP for existing session
     */
    public void resendOtp(String sessionId) {
        Session session = getSession(sessionId);
        
        if (session.isVerified()) {
            throw new InvalidOtpException("Session already verified");
        }
        
        // Generate new OTP
        String otp = OtpGenerator.generate(otpLength);
        String otpHash = passwordEncoder.encode(otp);
        
        // Update session with new OTP
        long now = Instant.now().getEpochSecond();
        session.setOtpHash(otpHash);
        session.setOtpExpiresAt(now + otpExpirySeconds);
        session.setOtpAttempts(0);
        
        sessionRepository.update(session);
        
        // Send OTP email (will be logged in local environment)
        emailService.sendOtpEmail(session.getEmail(), otp);

        log.info("🔄 Resent OTP for sessionId: {} | Email: {}", sessionId, EmailMasker.mask(session.getEmail()));
    }
    
    /**
     * Delete session
     */
    public void deleteSession(String sessionId) {
        // Remove associated GitHub token
        secureTokenService.removeSessionToken(sessionId);
        
        // Delete session from repository
        sessionRepository.delete(sessionId);
        
        log.info("Deleted session: {}", sessionId);
    }
    
    /**
     * Extract organization from email domain
     */
    private String extractOrganization(String email) {
        if (email == null || !email.contains("@")) {
            return "unknown";
        }
        
        String domain = email.substring(email.indexOf("@") + 1);
        String org = domain.split("\\.")[0];
        
        // Common email providers
        if (org.equalsIgnoreCase("gmail") || org.equalsIgnoreCase("yahoo") || 
            org.equalsIgnoreCase("hotmail") || org.equalsIgnoreCase("outlook")) {
            return "personal";
        }
        
        return org.toLowerCase();
    }
    /**
     * Save or update a session
     */
    public Session saveSession(Session session) {
        // Validate session
        if (session.getSessionId() == null) {
            throw new IllegalArgumentException("Session ID cannot be null");
        }
        
        // Update TTL if needed
        if (session.getTtl() == null) {
            long currentTime = System.currentTimeMillis() / 1000;
            session.setTtl(currentTime + 3600); // 1 hour TTL
        }
        
        // Save to repository
        return sessionRepository.save(session);
    }
    /**
     * Get client IP address
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}