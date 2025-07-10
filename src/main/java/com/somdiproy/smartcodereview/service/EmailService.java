package com.somdiproy.smartcodereview.service;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class EmailService {
    
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    
    @Value("${sendgrid.api.key:}")
    private String sendGridApiKey;
    
    @Value("${sendgrid.from-email:smartcode@somdip.dev}")
    private String fromEmail;
    
    @Value("${sendgrid.from-name:SmartCode Review}")
    private String fromName;
    
    @Value("${sendgrid.enabled:true}")
    private boolean sendGridEnabled;
    
    @Value("${logging.otp.enabled:false}")
    private boolean otpLoggingEnabled;
    
    /**
     * Send professional OTP email using SendGrid
     */
    public void sendOtpEmail(String toEmail, String otp) {
        try {
            // Check if SendGrid is disabled or OTP logging is enabled
            if (!sendGridEnabled || otpLoggingEnabled) {
                log.info("=== OTP DEBUG ===");
                log.info("Email: {} | OTP: {}", toEmail, otp);
                log.info("================");
                
                // If SendGrid is disabled, don't proceed with email sending
                if (!sendGridEnabled) {
                    log.info("SendGrid is disabled - Email not sent");
                    return;
                }
            }
            
            // Create the email
            Email from = new Email(fromEmail, fromName);
            Email to = new Email(toEmail);
            
            String subject = "Your SmartCode Review Verification Code";
            Content content = new Content("text/html", createProfessionalOtpTemplate(otp));
            
            Mail mail = new Mail(from, subject, to, content);
            
           // Validate API key before sending
            if (sendGridApiKey == null || sendGridApiKey.trim().isEmpty()) {
                log.error("SendGrid API key is not configured");
                throw new RuntimeException("Email service is not properly configured");
            }

            // Send via SendGrid
            SendGrid sg = new SendGrid(sendGridApiKey);
            Request request = new Request();
            
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            
            Response response = sg.api(request);
            
            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                log.info("✅ OTP email sent successfully to: {}", maskEmail(toEmail));
            } else {
                log.error("❌ Failed to send OTP email. Status: {}, Body: {}", 
                    response.getStatusCode(), response.getBody());
                throw new RuntimeException("Failed to send verification email");
            }
            
        } catch (IOException e) {
            log.error("Error sending OTP email to: {}", maskEmail(toEmail), e);
            throw new RuntimeException("Failed to send verification email", e);
        }
    }
    
    /**
     * Create professional HTML template for OTP email
     */
    private String createProfessionalOtpTemplate(String otp) {
        return String.format("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { 
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        line-height: 1.6;
                        color: #333;
                        background-color: #f5f5f5;
                    }
                    .wrapper {
                        width: 100%%;
                        background-color: #f5f5f5;
                        padding: 40px 20px;
                    }
                    .container {
                        max-width: 600px;
                        margin: 0 auto;
                        background-color: #ffffff;
                        border-radius: 12px;
                        box-shadow: 0 2px 8px rgba(0,0,0,0.1);
                        overflow: hidden;
                    }
                    .header {
                        background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        color: white;
                        padding: 40px 30px;
                        text-align: center;
                    }
                    .header h1 {
                        font-size: 28px;
                        font-weight: 600;
                        margin-bottom: 10px;
                    }
                    .header p {
                        font-size: 16px;
                        opacity: 0.9;
                    }
                    .content {
                        padding: 40px 30px;
                    }
                    .greeting {
                        font-size: 18px;
                        color: #333;
                        margin-bottom: 20px;
                    }
                    .message {
                        font-size: 16px;
                        color: #666;
                        margin-bottom: 30px;
                        line-height: 1.8;
                    }
                    .otp-container {
                        background: linear-gradient(135deg, #f5f7fa 0%%, #c3cfe2 100%%);
                        border-radius: 10px;
                        padding: 30px;
                        text-align: center;
                        margin: 30px 0;
                    }
                    .otp-label {
                        font-size: 14px;
                        color: #666;
                        text-transform: uppercase;
                        letter-spacing: 1px;
                        margin-bottom: 15px;
                    }
                    .otp-code {
                        font-size: 36px;
                        font-weight: 700;
                        color: #667eea;
                        letter-spacing: 8px;
                        font-family: 'Courier New', monospace;
                    }
                    .validity {
                        font-size: 14px;
                        color: #e53e3e;
                        margin-top: 15px;
                        font-weight: 500;
                    }
                    .info-box {
                        background-color: #f7fafc;
                        border-left: 4px solid #667eea;
                        padding: 20px;
                        margin: 30px 0;
                        border-radius: 4px;
                    }
                    .info-box h3 {
                        font-size: 16px;
                        color: #333;
                        margin-bottom: 10px;
                    }
                    .info-box ul {
                        list-style: none;
                        color: #666;
                        font-size: 14px;
                    }
                    .info-box li {
                        padding: 5px 0;
                        padding-left: 20px;
                        position: relative;
                    }
                    .info-box li:before {
                        content: "✓";
                        position: absolute;
                        left: 0;
                        color: #48bb78;
                        font-weight: bold;
                    }
                    .footer {
                        background-color: #f7fafc;
                        padding: 30px;
                        text-align: center;
                        border-top: 1px solid #e2e8f0;
                    }
                    .footer p {
                        font-size: 14px;
                        color: #718096;
                        margin-bottom: 10px;
                    }
                    .footer a {
                        color: #667eea;
                        text-decoration: none;
                    }
                    .social-links {
                        margin-top: 20px;
                    }
                    .social-links a {
                        display: inline-block;
                        margin: 0 10px;
                        color: #718096;
                        font-size: 14px;
                    }
                    .warning {
                        font-size: 13px;
                        color: #718096;
                        font-style: italic;
                        margin-top: 20px;
                        padding-top: 20px;
                        border-top: 1px solid #e2e8f0;
                    }
                    @media (max-width: 600px) {
                        .header { padding: 30px 20px; }
                        .content { padding: 30px 20px; }
                        .otp-code { font-size: 28px; letter-spacing: 4px; }
                    }
                </style>
            </head>
            <body>
                <div class="wrapper">
                    <div class="container">
                        <div class="header">
                            <h1>🚀 SmartCode Review</h1>
                            <p>AI-Powered Code Analysis Platform</p>
                        </div>
                        
                        <div class="content">
                            <h2 class="greeting">Welcome to SmartCode Review!</h2>
                            
                            <p class="message">
                                Thank you for choosing SmartCode Review for your code analysis needs. 
                                To ensure the security of your session, please verify your email address 
                                using the verification code below.
                            </p>
                            
                            <div class="otp-container">
                                <div class="otp-label">Your Verification Code</div>
                                <div class="otp-code">%s</div>
                                <div class="validity">⏱️ Valid for 5 minutes</div>
                            </div>
                            
                            <div class="info-box">
                                <h3>What happens next?</h3>
                                <ul>
                                    <li>Enter this code on the verification page</li>
                                    <li>Get instant access to your 7-minute demo session</li>
                                    <li>Analyze up to 3 code repositories</li>
                                    <li>Experience AI-powered code review with Amazon Bedrock</li>
                                </ul>
                            </div>
                            
                            <p class="warning">
                                <strong>Security Notice:</strong> If you didn't request this verification code, 
                                please ignore this email. Someone may have entered your email address by mistake.
                            </p>
                        </div>
                        
                        <div class="footer">
                            <p>
                                <strong>SmartCode Review</strong> - Part of the Somdip.dev Portfolio
                            </p>
                            <p>
                                <a href="https://smartcode.somdip.dev">smartcode.somdip.dev</a> | 
                                <a href="https://somdip.dev">somdip.dev</a>
                            </p>
                            <div class="social-links">
                                <a href="https://github.com/somdiproy">GitHub</a>
                                <a href="https://www.linkedin.com/in/somdip-roy-b8004b111/">LinkedIn</a>
                            </div>
                            <p style="margin-top: 20px; font-size: 12px; color: #a0aec0;">
                                © 2024 SmartCode Review. All rights reserved.
                            </p>
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """, otp);
    }
    
    /**
     * Send session expiry warning email
     */
    public void sendSessionExpiryWarning(String toEmail, int remainingMinutes) {
        // Implementation for session expiry warning
        log.info("Session expiry warning would be sent to: {} ({} minutes remaining)", 
                maskEmail(toEmail), remainingMinutes);
    }
    
    /**
     * Mask email for logging
     */
    private String maskEmail(String email) {
        if (email == null || email.length() < 3) return "***";
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) return "***";
        return email.substring(0, Math.min(3, atIndex)) + "***" + email.substring(atIndex);
    }
}