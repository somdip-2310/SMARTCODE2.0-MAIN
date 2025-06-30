package com.somdiproy.smartcodereview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Service for sending emails including OTP emails
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    
    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    
    @Value("${spring.mail.username}")
    private String fromEmail;
    
    @Value("${spring.profiles.active:dev}")
    private String activeProfile;
    
    /**
     * Send OTP email
     */
    public void sendOtpEmail(String toEmail, String otp) {
        // In dev mode, just log the OTP
        if ("dev".equals(activeProfile)) {
            log.info("DEV MODE - OTP for {}: {}", toEmail, otp);
            return;
        }
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Smart Code Review - Verification Code");
            
            // Create email content
            Context context = new Context();
            context.setVariable("otp", otp);
            context.setVariable("email", toEmail);
            
            String htmlContent = createOtpEmailTemplate(otp);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("OTP email sent to: {}", toEmail);
            
        } catch (MessagingException e) {
            log.error("Failed to send OTP email to: {}", toEmail, e);
            // Fallback to simple email
            sendSimpleOtpEmail(toEmail, otp);
        }
    }
    
    /**
     * Send analysis complete notification
     */
    public void sendAnalysisCompleteEmail(String toEmail, String repository, String branch, String reportUrl) {
        if ("dev".equals(activeProfile)) {
            log.info("DEV MODE - Analysis complete for {}: {} - {}", toEmail, repository, branch);
            return;
        }
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Smart Code Review - Analysis Complete");
            
            String htmlContent = createAnalysisCompleteEmailTemplate(repository, branch, reportUrl);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("Analysis complete email sent to: {}", toEmail);
            
        } catch (MessagingException e) {
            log.error("Failed to send analysis complete email to: {}", toEmail, e);
        }
    }
    
    /**
     * Simple fallback OTP email
     */
    private void sendSimpleOtpEmail(String toEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Smart Code Review - Verification Code");
        message.setText(String.format(
            "Your verification code is: %s\n\n" +
            "This code will expire in 5 minutes.\n\n" +
            "If you didn't request this code, please ignore this email.\n\n" +
            "Best regards,\n" +
            "Smart Code Review Team", 
            otp
        ));
        
        try {
            mailSender.send(message);
            log.info("Simple OTP email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send simple OTP email to: {}", toEmail, e);
        }
    }
    
    /**
     * Create HTML template for OTP email
     */
    private String createOtpEmailTemplate(String otp) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 0; }
                    .container { max-width: 600px; margin: 0 auto; background-color: #ffffff; padding: 20px; }
                    .header { background-color: #007bff; color: white; padding: 20px; text-align: center; }
                    .content { padding: 30px 20px; }
                    .otp-box { background-color: #f8f9fa; border: 2px solid #007bff; border-radius: 8px; 
                               padding: 20px; margin: 20px 0; text-align: center; }
                    .otp-code { font-size: 32px; font-weight: bold; color: #007bff; letter-spacing: 8px; }
                    .footer { text-align: center; color: #666; font-size: 12px; padding: 20px; }
                    .button { background-color: #007bff; color: white; padding: 12px 30px; 
                             text-decoration: none; border-radius: 5px; display: inline-block; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Smart Code Review</h1>
                    </div>
                    <div class="content">
                        <h2>Verify Your Email</h2>
                        <p>Thank you for using Smart Code Review. Please use the verification code below to complete your sign-in:</p>
                        
                        <div class="otp-box">
                            <div class="otp-code">%s</div>
                        </div>
                        
                        <p><strong>This code will expire in 5 minutes.</strong></p>
                        
                        <p>Enter this code on the verification page to access your 1-hour session with 3 code scans.</p>
                        
                        <p>If you didn't request this code, please ignore this email.</p>
                    </div>
                    <div class="footer">
                        <p>&copy; 2024 Smart Code Review. All rights reserved.</p>
                        <p>Powered by Amazon Nova</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(otp);
    }
    
    /**
     * Create HTML template for analysis complete email
     */
    private String createAnalysisCompleteEmailTemplate(String repository, String branch, String reportUrl) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 0; }
                    .container { max-width: 600px; margin: 0 auto; background-color: #ffffff; padding: 20px; }
                    .header { background-color: #28a745; color: white; padding: 20px; text-align: center; }
                    .content { padding: 30px 20px; }
                    .repo-info { background-color: #f8f9fa; border-radius: 8px; padding: 15px; margin: 20px 0; }
                    .button { background-color: #28a745; color: white; padding: 12px 30px; 
                             text-decoration: none; border-radius: 5px; display: inline-block; }
                    .footer { text-align: center; color: #666; font-size: 12px; padding: 20px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Analysis Complete!</h1>
                    </div>
                    <div class="content">
                        <h2>Your code analysis is ready</h2>
                        
                        <div class="repo-info">
                            <p><strong>Repository:</strong> %s</p>
                            <p><strong>Branch:</strong> %s</p>
                        </div>
                        
                        <p>We've completed the analysis of your code and found several areas for improvement. 
                           Our AI-powered analysis has generated detailed suggestions for:</p>
                        
                        <ul>
                            <li>Security vulnerabilities</li>
                            <li>Performance optimizations</li>
                            <li>Code quality improvements</li>
                        </ul>
                        
                        <p style="text-align: center; margin: 30px 0;">
                            <a href="%s" class="button">View Full Report</a>
                        </p>
                        
                        <p><small>This report will be available for 7 days.</small></p>
                    </div>
                    <div class="footer">
                        <p>&copy; 2024 Smart Code Review. All rights reserved.</p>
                        <p>Powered by Amazon Nova</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(repository, branch, reportUrl);
    }
}