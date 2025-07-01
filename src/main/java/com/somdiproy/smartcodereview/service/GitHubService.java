package com.somdiproy.smartcodereview.service;

import com.somdiproy.smartcodereview.model.Branch;
import com.somdiproy.smartcodereview.model.Repository;
import org.kohsuke.github.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Enhanced GitHub API integration service
 * Handles repository access, branch fetching, and code analysis
 */
@Service
public class GitHubService {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GitHubService.class);
    
    @Value("${github.api.token:}")
    private String defaultGithubToken;
    
    @Value("${analysis.supported-extensions}")
    private List<String> supportedExtensions;
    
    @Value("${analysis.excluded-patterns}")
    private List<String> excludedPatterns;
    
    @Value("${analysis.max-file-size:20971520}") // 20MB default
    private long maxFileSize;
    
    @Value("${analysis.max-files-per-scan:50}")
    private int maxFilesPerScan;
    
    /**
     * Validate GitHub repository URL format
     */
    public boolean isValidRepositoryUrl(String repoUrl) {
        if (!StringUtils.hasText(repoUrl)) {
            return false;
        }
        
        // Support various GitHub URL formats
        return repoUrl.matches("^https?://github\\.com/[\\w\\-\\.]+/[\\w\\-\\.]+/?(\\.git)?$");
    }
    
    /**
     * Extract owner/repo from GitHub URL
     */
    public String[] extractOwnerAndRepo(String repoUrl) {
        if (!isValidRepositoryUrl(repoUrl)) {
            throw new IllegalArgumentException("Invalid GitHub repository URL: " + repoUrl);
        }
        
        String path = repoUrl.replaceFirst("https?://github\\.com/", "")
                            .replaceFirst("\\.git$", "")
                            .replaceFirst("/$", "");
        
        String[] parts = path.split("/");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Cannot extract owner/repo from URL: " + repoUrl);
        }
        
        return new String[]{parts[0], parts[1]};
    }
    
    /**
     * Test repository access with given token
     */
    public boolean canAccessRepository(String repoUrl, String accessToken) {
        try {
            GitHub github = createGitHubClient(accessToken);
            GHRepository repo = getGHRepository(github, repoUrl);
            
            // Try to access basic repository info
            repo.getName();
            repo.getDefaultBranch();
            
            log.info("✅ Successfully verified access to repository: {}", repoUrl);
            return true;
            
        } catch (Exception e) {
            log.warn("❌ Cannot access repository {} with provided token: {}", repoUrl, e.getMessage());
            return false;
        }
    }
    
    /**
     * Get repository information with enhanced error handling
     */
    @Cacheable(value = "repositories", key = "#repoUrl + '_' + (#accessToken != null ? #accessToken.hashCode() : 'anonymous')")
    public Repository getRepository(String repoUrl, String accessToken) {
        if (!isValidRepositoryUrl(repoUrl)) {
            throw new IllegalArgumentException("Invalid GitHub repository URL format");
        }
        
        try {
            GitHub github = createGitHubClient(accessToken);
            GHRepository ghRepo = getGHRepository(github, repoUrl);
            
            Repository repository = new Repository();
            repository.setUrl(repoUrl);
            repository.setName(ghRepo.getName());
            repository.setFullName(ghRepo.getFullName());
            repository.setDescription(ghRepo.getDescription());
            repository.setOwner(ghRepo.getOwnerName());
            repository.setDefaultBranch(ghRepo.getDefaultBranch());
            repository.setIsPrivate(ghRepo.isPrivate());
            repository.setLanguage(ghRepo.getLanguage());
            repository.setSize((long) ghRepo.getSize());
            repository.setStarsCount(ghRepo.getStargazersCount());
            repository.setForksCount(ghRepo.getForksCount());
            
            log.info("📚 Fetched repository info: {} (Private: {}, Language: {})", 
                     repository.getFullName(), repository.getIsPrivate(), repository.getLanguage());
            
            return repository;
                    
        } catch (GHFileNotFoundException e) {
            throw new RuntimeException("Repository not found: " + repoUrl + ". Please check the URL and your access permissions.");
        } catch (GHException e) {
            if (e.getMessage().contains("rate limit")) {
                throw new RuntimeException("GitHub API rate limit exceeded. Please try again later or provide a GitHub token.");
            } else if (e.getMessage().contains("Not Found")) {
                throw new RuntimeException("Repository not found or you don't have access: " + repoUrl);
            }
            throw new RuntimeException("GitHub API error: " + e.getMessage());
        } catch (IOException e) {
            log.error("Failed to fetch repository info: {}", repoUrl, e);
            throw new RuntimeException("Failed to access GitHub repository. Please check your internet connection.");
        }
    }
    
    /**
     * Overloaded method for backward compatibility
     */
    public Repository getRepository(String repoUrl) {
        return getRepository(repoUrl, null);
    }
    
    /**
     * Fetch all branches with enhanced filtering and sorting
     */
    @Cacheable(value = "branches", key = "#repoUrl + '_' + (#accessToken != null ? #accessToken.hashCode() : 'anonymous')")
    public List<Branch> fetchBranches(String repoUrl, String accessToken) {
        try {
            GitHub github = createGitHubClient(accessToken);
            GHRepository ghRepo = getGHRepository(github, repoUrl);
            
            Map<String, GHBranch> branches = ghRepo.getBranches();
            String defaultBranch = ghRepo.getDefaultBranch();
            
            log.info("🌿 Found {} branches in repository: {}", branches.size(), repoUrl);
            
            return branches.entrySet().stream()
                    .map(entry -> {
                        try {
                            GHBranch ghBranch = entry.getValue();
                            Branch branch = new Branch();
                            branch.setName(entry.getKey());
                            branch.setIsDefault(entry.getKey().equals(defaultBranch));
                            branch.setIsProtected(ghBranch.isProtected());
                            branch.setSha(ghBranch.getSHA1());
                            branch.setLastCommitDate(getLastCommitDate(ghBranch));
                            return branch;
                        } catch (Exception e) {
                            log.warn("⚠️ Error processing branch {}: {}", entry.getKey(), e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .sorted((a, b) -> {
                        // Sort: default first, then alphabetically
                        if (Boolean.TRUE.equals(a.getIsDefault())) return -1;
                        if (Boolean.TRUE.equals(b.getIsDefault())) return 1;
                        return a.getName().compareTo(b.getName());
                    })
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("Failed to fetch branches for: {}", repoUrl, e);
            throw new RuntimeException("Failed to fetch branches: " + e.getMessage());
        }
    }
    
    /**
     * Fetch code files from specific branch with intelligent filtering
     */
    public List<GitHubFile> fetchBranchCode(String repoUrl, String branch, String accessToken) {
        try {
            GitHub github = createGitHubClient(accessToken);
            GHRepository ghRepo = getGHRepository(github, repoUrl);
            
            log.info("🔍 Fetching files from {}:{}", repoUrl, branch);
            
            List<GitHubFile> files = new ArrayList<>();
            GHTree tree = ghRepo.getTreeRecursive(branch, 1);
            
            // Filter and process files
            List<GHTreeEntry> eligibleFiles = tree.getTree().stream()
                    .filter(entry -> "blob".equals(entry.getType()))
                    .filter(entry -> shouldProcessFile(entry.getPath()))
                    .filter(entry -> entry.getSize() <= maxFileSize)
                    .limit(maxFilesPerScan)
                    .collect(Collectors.toList());
            
            log.info("📁 Processing {} eligible files (filtered from {} total)", 
                     eligibleFiles.size(), tree.getTree().size());
            
            // Process files in parallel for better performance
            List<CompletableFuture<GitHubFile>> futures = eligibleFiles.stream()
                    .map(entry -> CompletableFuture.supplyAsync(() -> 
                        fetchFileContent(ghRepo, entry, branch)))
                    .collect(Collectors.toList());
            
            // Wait for all files to be fetched
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
            // Collect results
            for (CompletableFuture<GitHubFile> future : futures) {
                try {
                    GitHubFile file = future.get();
                    if (file != null) {
                        files.add(file);
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Failed to fetch file content: {}", e.getMessage());
                }
            }
            
            log.info("✅ Successfully fetched {} files from {} branch {}", 
                     files.size(), repoUrl, branch);
            return files;
            
        } catch (Exception e) {
            log.error("💥 Failed to fetch code from {} branch {}", repoUrl, branch, e);
            throw new RuntimeException("Failed to fetch code: " + e.getMessage());
        }
    }
    
    /**
     * Get file content statistics for analysis planning
     */
    public FileAnalysisStats getFileStats(String repoUrl, String branch, String accessToken) {
        try {
            GitHub github = createGitHubClient(accessToken);
            GHRepository ghRepo = getGHRepository(github, repoUrl);
            
            GHTree tree = ghRepo.getTreeRecursive(branch, 1);
            
            Map<String, Integer> languageStats = new HashMap<>();
            Map<String, Integer> sizeDistribution = new HashMap<>();
            int totalFiles = 0;
            int eligibleFiles = 0;
            long totalSize = 0;
            
            for (GHTreeEntry entry : tree.getTree()) {
                if (!"blob".equals(entry.getType())) continue;
                
                totalFiles++;
                totalSize += entry.getSize();
                
                String language = detectLanguage(entry.getPath());
                languageStats.merge(language, 1, Integer::sum);
                
                String sizeCategory = categorizeSizeSize(entry.getSize());
                sizeDistribution.merge(sizeCategory, 1, Integer::sum);
                
                if (shouldProcessFile(entry.getPath()) && entry.getSize() <= maxFileSize) {
                    eligibleFiles++;
                }
            }
            
            return FileAnalysisStats.builder()
                    .totalFiles(totalFiles)
                    .eligibleFiles(eligibleFiles)
                    .totalSizeBytes(totalSize)
                    .languageDistribution(languageStats)
                    .sizeDistribution(sizeDistribution)
                    .estimatedTokens(estimateTokenCount(eligibleFiles))
                    .estimatedCost(estimateAnalysisCost(eligibleFiles))
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to get file stats for: {}", repoUrl, e);
            throw new RuntimeException("Failed to analyze repository structure: " + e.getMessage());
        }
    }
    
    /**
     * Fetch content of a single file with retry logic
     */
    private GitHubFile fetchFileContent(GHRepository repo, GHTreeEntry entry, String branch) {
        int maxRetries = 3;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                GHContent content = repo.getFileContent(entry.getPath(), branch);
                
                return GitHubFile.builder()
                        .path(entry.getPath())
                        .name(getFileName(entry.getPath()))
                        .content(content.getContent())
                        .size(entry.getSize())
                        .sha(entry.getSha())
                        .language(detectLanguage(entry.getPath()))
                        .build();
                        
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    log.warn("⚠️ Failed to fetch file after {} attempts: {}", maxRetries, entry.getPath());
                    return null;
                }
                
                try {
                    Thread.sleep(1000 * attempt); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Enhanced file filtering logic
     */
    private boolean shouldProcessFile(String path) {
        // Check excluded patterns first (more efficient)
        for (String pattern : excludedPatterns) {
            String regex = pattern.replace("**", ".*").replace("*", "[^/]*");
            if (path.matches(regex)) {
                return false;
            }
        }
        
        // Check file extension
        String extension = getFileExtension(path);
        if (!supportedExtensions.contains(extension)) {
            return false;
        }
        
        // Additional filtering rules
        String fileName = getFileName(path).toLowerCase();
        
        // Skip common non-source files
        if (fileName.startsWith(".") || 
            fileName.contains("package-lock.json") ||
            fileName.contains("yarn.lock") ||
            fileName.endsWith(".min.js") ||
            fileName.endsWith(".min.css")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Create GitHub client with fallback options
     */
    private GitHub createGitHubClient(String accessToken) throws IOException {
        // Use provided token first
        if (StringUtils.hasText(accessToken)) {
            try {
                GitHub github = new GitHubBuilder().withOAuthToken(accessToken).build();
                // Test the token
                github.getRateLimit();
                return github;
            } catch (Exception e) {
                log.warn("⚠️ Provided GitHub token is invalid, falling back to default");
            }
        }
        
        // Use default token
        if (StringUtils.hasText(defaultGithubToken)) {
            try {
                GitHub github = new GitHubBuilder().withOAuthToken(defaultGithubToken).build();
                github.getRateLimit();
                return github;
            } catch (Exception e) {
                log.warn("⚠️ Default GitHub token is invalid, using anonymous access");
            }
        }
        
        // Anonymous access (limited rate limit)
        GitHub github = GitHub.connectAnonymously();
        GHRateLimit rateLimit = github.getRateLimit();
        
        log.info("🔓 Using anonymous GitHub access. Rate limit: {}/{}", 
                 rateLimit.getRemaining(), rateLimit.getLimit());
        
        if (rateLimit.getRemaining() < 10) {
            throw new IOException("GitHub rate limit nearly exceeded. Please provide a GitHub token for better access.");
        }
        
        return github;
    }
    
    /**
     * Get GHRepository with enhanced error handling
     */
    private GHRepository getGHRepository(GitHub github, String repoUrl) throws IOException {
        String[] ownerRepo = extractOwnerAndRepo(repoUrl);
        String repoPath = ownerRepo[0] + "/" + ownerRepo[1];
        
        try {
            return github.getRepository(repoPath);
        } catch (GHFileNotFoundException e) {
            throw new GHFileNotFoundException("Repository not found: " + repoPath + 
                    ". Please check the URL and ensure the repository is public or you have access.");
        }
    }
    
    // Utility methods
    private Date getLastCommitDate(GHBranch branch) {
        try {
            return new Date(); // Placeholder - would need commit API call
        } catch (Exception e) {
            return new Date();
        }
    }
    
    private String getFileExtension(String path) {
        int lastDot = path.lastIndexOf('.');
        return (lastDot > 0 && lastDot < path.length() - 1) ? path.substring(lastDot) : "";
    }
    
    private String getFileName(String path) {
        int lastSlash = path.lastIndexOf('/');
        return (lastSlash >= 0 && lastSlash < path.length() - 1) ? path.substring(lastSlash + 1) : path;
    }
    
    private String detectLanguage(String path) {
        String extension = getFileExtension(path).toLowerCase();
        
        Map<String, String> languageMap = Map.of(
            ".java", "java",
            ".py", "python", 
            ".js", "javascript",
            ".ts", "typescript",
            ".cs", "csharp",
            ".go", "go",
            ".rb", "ruby",
            ".php", "php",
            ".cpp", "cpp",
            ".c", "c"
        );
        
        return languageMap.getOrDefault(extension, "unknown");
    }
    
    private String categorizeSizeSize(long size) {
        if (size < 1024) return "small";
        if (size < 10240) return "medium";
        if (size < 102400) return "large";
        return "xlarge";
    }
    
    private int estimateTokenCount(int fileCount) {
        return fileCount * 1000; // Rough estimate
    }
    
    private double estimateAnalysisCost(int fileCount) {
        // Based on Nova model pricing
        int totalTokens = estimateTokenCount(fileCount);
        return (totalTokens / 1_000_000.0) * 0.80; // Nova Premier price
    }
    
    /**
     * GitHubFile data class with enhanced features
     */
    public static class GitHubFile {
        private String path;
        private String name;
        private String content;
        private long size;
        private String sha;
        private String language;
        private String mimeType;
        private boolean isBinary;
        
        public GitHubFile() {}
        
        private GitHubFile(Builder builder) {
            this.path = builder.path;
            this.name = builder.name;
            this.content = builder.content;
            this.size = builder.size;
            this.sha = builder.sha;
            this.language = builder.language;
            this.mimeType = builder.mimeType;
            this.isBinary = builder.isBinary;
        }
        
        // Getters and setters
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
        
        public String getSha() { return sha; }
        public void setSha(String sha) { this.sha = sha; }
        
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        
        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }
        
        public boolean isBinary() { return isBinary; }
        public void setBinary(boolean binary) { isBinary = binary; }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String path, name, content, sha, language, mimeType;
            private long size;
            private boolean isBinary;
            
            public Builder path(String path) { this.path = path; return this; }
            public Builder name(String name) { this.name = name; return this; }
            public Builder content(String content) { this.content = content; return this; }
            public Builder size(long size) { this.size = size; return this; }
            public Builder sha(String sha) { this.sha = sha; return this; }
            public Builder language(String language) { this.language = language; return this; }
            public Builder mimeType(String mimeType) { this.mimeType = mimeType; return this; }
            public Builder isBinary(boolean isBinary) { this.isBinary = isBinary; return this; }
            
            public GitHubFile build() {
                return new GitHubFile(this);
            }
        }
    }
    
    /**
     * File analysis statistics
     */
    public static class FileAnalysisStats {
        private int totalFiles;
        private int eligibleFiles;
        private long totalSizeBytes;
        private Map<String, Integer> languageDistribution;
        private Map<String, Integer> sizeDistribution;
        private int estimatedTokens;
        private double estimatedCost;
        
        public FileAnalysisStats() {}
        
        private FileAnalysisStats(Builder builder) {
            this.totalFiles = builder.totalFiles;
            this.eligibleFiles = builder.eligibleFiles;
            this.totalSizeBytes = builder.totalSizeBytes;
            this.languageDistribution = builder.languageDistribution;
            this.sizeDistribution = builder.sizeDistribution;
            this.estimatedTokens = builder.estimatedTokens;
            this.estimatedCost = builder.estimatedCost;
        }
        
        // Getters
        public int getTotalFiles() { return totalFiles; }
        public int getEligibleFiles() { return eligibleFiles; }
        public long getTotalSizeBytes() { return totalSizeBytes; }
        public Map<String, Integer> getLanguageDistribution() { return languageDistribution; }
        public Map<String, Integer> getSizeDistribution() { return sizeDistribution; }
        public int getEstimatedTokens() { return estimatedTokens; }
        public double getEstimatedCost() { return estimatedCost; }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private int totalFiles, eligibleFiles, estimatedTokens;
            private long totalSizeBytes;
            private Map<String, Integer> languageDistribution, sizeDistribution;
            private double estimatedCost;
            
            public Builder totalFiles(int totalFiles) { this.totalFiles = totalFiles; return this; }
            public Builder eligibleFiles(int eligibleFiles) { this.eligibleFiles = eligibleFiles; return this; }
            public Builder totalSizeBytes(long totalSizeBytes) { this.totalSizeBytes = totalSizeBytes; return this; }
            public Builder languageDistribution(Map<String, Integer> languageDistribution) { this.languageDistribution = languageDistribution; return this; }
            public Builder sizeDistribution(Map<String, Integer> sizeDistribution) { this.sizeDistribution = sizeDistribution; return this; }
            public Builder estimatedTokens(int estimatedTokens) { this.estimatedTokens = estimatedTokens; return this; }
            public Builder estimatedCost(double estimatedCost) { this.estimatedCost = estimatedCost; return this; }
            
            public FileAnalysisStats build() {
                return new FileAnalysisStats(this);
            }
        }
    }
}