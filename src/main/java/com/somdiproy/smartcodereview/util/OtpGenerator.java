package com.somdiproy.smartcodereview.util;

import java.security.SecureRandom;

/**
 * Utility class for generating OTP codes
 */
public class OtpGenerator {
    
    private static final SecureRandom random = new SecureRandom();
    private static final String DIGITS = "0123456789";
    
    /**
     * Generate a numeric OTP of specified length
     */
    public static String generate(int length) {
        StringBuilder otp = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            otp.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        }
        return otp.toString();
    }
    
    /**
     * Generate a 6-digit OTP (default)
     */
    public static String generate() {
        return generate(6);
    }
}