package com.somdiproy.smartcodereview.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for GitHub integration services
 */
@Configuration
@EnableCaching
@EnableAsync
public class GitHubServiceConfig {
    
    @Value("${github.analysis.parallel-downloads:5}")
    private int parallelDownloads;
    
    @Value("${github.analysis.timeout-per-file:10000}")
    private int timeoutPerFile;
    
    /**
     * Cache manager for GitHub API responses
     */
    @Bean
    public CacheManager gitHubCacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        cacheManager.setCacheNames(java.util.Arrays.asList(
            "repositories", 
            "branches", 
            "file-stats",
            "repository-access"
        ));
        return cacheManager;
    }
    
    /**
     * Thread pool executor for parallel GitHub file downloads
     */
    @Bean("githubExecutor")
    public Executor githubTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(parallelDownloads);
        executor.setMaxPoolSize(parallelDownloads * 2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("github-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
    
    /**
     * Configuration properties for GitHub service
     */
    @Bean
    public GitHubServiceProperties gitHubServiceProperties() {
        return new GitHubServiceProperties();
    }
    
    /**
     * Properties class for GitHub service configuration
     */
    public static class GitHubServiceProperties {
        
        @Value("${github.api.token:}")
        private String defaultToken;
        
        @Value("${github.api.base-url:https://api.github.com}")
        private String baseUrl;
        
        @Value("${github.rate-limit:5000}")
        private int rateLimit;
        
        @Value("${github.timeout:30000}")
        private int timeout;
        
        @Value("${github.analysis.retry-attempts:3}")
        private int retryAttempts;
        
        @Value("${analysis.max-file-size:20971520}")
        private long maxFileSize;
        
        @Value("${analysis.max-files-per-scan:50}")
        private int maxFilesPerScan;
        
        // Getters
        public String getDefaultToken() { return defaultToken; }
        public String getBaseUrl() { return baseUrl; }
        public int getRateLimit() { return rateLimit; }
        public int getTimeout() { return timeout; }
        public int getRetryAttempts() { return retryAttempts; }
        public long getMaxFileSize() { return maxFileSize; }
        public int getMaxFilesPerScan() { return maxFilesPerScan; }
        
        // Validation
        public boolean isConfigured() {
            return defaultToken != null && !defaultToken.trim().isEmpty();
        }
        
        public boolean isValidConfiguration() {
            return baseUrl != null && 
                   rateLimit > 0 && 
                   timeout > 0 && 
                   retryAttempts > 0 && 
                   maxFileSize > 0 && 
                   maxFilesPerScan > 0;
        }
    }
}