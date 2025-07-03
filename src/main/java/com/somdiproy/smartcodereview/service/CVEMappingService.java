package com.somdiproy.smartcodereview.service;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class CVEMappingService {
    
    private static final Map<String, String> CVE_MAPPINGS = new HashMap<>();
    
    static {
        CVE_MAPPINGS.put("SQL_INJECTION", "CVE-2023-38646");
        CVE_MAPPINGS.put("XSS", "CVE-2023-39319");
        CVE_MAPPINGS.put("INSECURE_DESERIALIZATION", "CVE-2023-33202");
        CVE_MAPPINGS.put("HARDCODED_CREDENTIALS", "CVE-2023-38501");
        CVE_MAPPINGS.put("WEAK_CRYPTOGRAPHY", "CVE-2023-39320");
    }
    
    public String getCVEId(String issueType) {
        if (issueType == null) return null;
        return CVE_MAPPINGS.getOrDefault(issueType.toUpperCase(), null);
    }
    
    public Double getCVEScore(String cveId) {
        if (cveId == null) return null;
        // Default CVSS scores for known CVEs
        switch (cveId) {
            case "CVE-2023-38646": return 9.8;
            case "CVE-2023-39319": return 8.6;
            case "CVE-2023-33202": return 9.1;
            case "CVE-2023-38501": return 7.5;
            case "CVE-2023-39320": return 6.5;
            default: return 7.0;
        }
    }
}