package com.somdiproy.smartcodereview.util;

/**
 * Utility class for masking email addresses
 */
public class EmailMasker {
    
    /**
     * Mask email address for privacy
     * Example: john.doe@example.com -> j***@example.com
     */
    public static String mask(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        
        String[] parts = email.split("@");
        String username = parts[0];
        String domain = parts[1];
        
        if (username.length() <= 1) {
            return username + "***@" + domain;
        }
        
        return username.charAt(0) + "***@" + domain;
    }
    
    /**
     * Partially mask email showing more characters
     * Example: john.doe@example.com -> joh***@example.com
     */
    public static String partialMask(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        
        String[] parts = email.split("@");
        String username = parts[0];
        String domain = parts[1];
        
        if (username.length() <= 3) {
            return username.charAt(0) + "***@" + domain;
        }
        
        int showChars = Math.min(3, username.length() / 2);
        return username.substring(0, showChars) + "***@" + domain;
    }
}