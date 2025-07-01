package com.somdiproy.smartcodereview.service;

import com.somdiproy.smartcodereview.model.Branch;
import com.somdiproy.smartcodereview.model.Repository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for interacting with GitHub API
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
    
    @Value("${analysis.max-file-size:5242880}") // 5MB default
    private long maxFileSize;
    
    
    /**
     * Get repository information
     */
    @Cacheable(value = "repositories", key = "#repoUrl")
    public Repository getRepository(String repoUrl) {
        try {
            GitHub github = createGitHubClient(null);
            GHRepository ghRepo = getGHRepository(github, repoUrl);
            
            return Repository.builder()
                    .url(repoUrl)
                    .name(ghRepo.getName())
                    .fullName(ghRepo.getFullName())
                    .description(ghRepo.getDescription())
                    .owner(ghRepo.getOwnerName())
                    .defaultBranch(ghRepo.getDefaultBranch())
                    .isPrivate(ghRepo.isPrivate())
                    .language(ghRepo.getLanguage())
                    .size(ghRepo.getSize())
                    .starsCount(ghRepo.getStargazersCount())
                    .forksCount(ghRepo.getForksCount())
                    .build();
                    
        } catch (IOException e) {
            log.error("Failed to fetch repository info: {}", repoUrl, e);
            throw new RuntimeException("Failed to access repository: " + e.getMessage());
        }
    }
    
    /**
     * Fetch all branches for a repository
     */
    @Cacheable(value = "branches", key = "#repoUrl")
    public List<Branch> fetchBranches(String repoUrl, String accessToken) {
        try {
            GitHub github = createGitHubClient(accessToken);
            GHRepository ghRepo = getGHRepository(github, repoUrl);
            
            Map<String, GHBranch> branches = ghRepo.getBranches();
            String defaultBranch = ghRepo.getDefaultBranch();
            
            return branches.entrySet().stream()
                    .map(entry -> {
                        GHBranch ghBranch = entry.getValue();
                        return Branch.builder()
                                .name(entry.getKey())
                                .isDefault(entry.getKey().equals(defaultBranch))
                                .isProtected(ghBranch.isProtected())
                                .sha(ghBranch.getSHA1())
                                .lastCommitDate(getLastCommitDate(ghBranch))
                                .build();
                    })
                    .sorted((a, b) -> {
                        // Sort: default first, then alphabetically
                        if (a.getIsDefault()) return -1;
                        if (b.getIsDefault()) return 1;
                        return a.getName().compareTo(b.getName());
                    })
                    .collect(Collectors.toList());
                    
        } catch (IOException e) {
            log.error("Failed to fetch branches for: {}", repoUrl, e);
            throw new RuntimeException("Failed to fetch branches: " + e.getMessage());
        }
    }
    
    /**
     * Fetch code files from a specific branch
     */
    public List<GitHubFile> fetchBranchCode(String repoUrl, String branch, String accessToken) {
        try {
            GitHub github = createGitHubClient(accessToken);
            GHRepository ghRepo = getGHRepository(github, repoUrl);
            
            List<GitHubFile> files = new ArrayList<>();
            GHTree tree = ghRepo.getTreeRecursive(branch, 1);
            
            // Process files in parallel for better performance
            List<CompletableFuture<GitHubFile>> futures = tree.getTree().stream()
                    .filter(entry -> "blob".equals(entry.getType()))
                    .filter(entry -> shouldProcessFile(entry.getPath()))
                    .map(entry -> CompletableFuture.supplyAsync(() -> 
                        fetchFileContent(ghRepo, entry, branch)))
                    .collect(Collectors.toList());
            
            // Wait for all files to be fetched
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
            // Collect results
            for (CompletableFuture<GitHubFile> future : futures) {
                GitHubFile file = future.get();
                if (file != null) {
                    files.add(file);
                }
            }
            
            log.info("Fetched {} files from {} branch {}", files.size(), repoUrl, branch);
            return files;
            
        } catch (Exception e) {
            log.error("Failed to fetch code from {} branch {}", repoUrl, branch, e);
            throw new RuntimeException("Failed to fetch code: " + e.getMessage());
        }
    }
    
    /**
     * Fetch content of a single file
     */
    private GitHubFile fetchFileContent(GHRepository repo, GHTreeEntry entry, String branch) {
        try {
            // Skip large files
            if (entry.getSize() > maxFileSize) {
                log.debug("Skipping large file: {} ({})", entry.getPath(), entry.getSize());
                return null;
            }
            
            GHContent content = repo.getFileContent(entry.getPath(), branch);
            
            return GitHubFile.builder()
                    .path(entry.getPath())
                    .name(getFileName(entry.getPath()))
                    .content(content.getContent())
                    .size(entry.getSize())
                    .sha(entry.getSha())
                    .language(detectLanguage(entry.getPath()))
                    .build();
                    
        } catch (IOException e) {
            log.warn("Failed to fetch file content: {}", entry.getPath(), e);
            return null;
        }
    }
    
    /**
     * Check if file should be processed based on extension and patterns
     */
    private boolean shouldProcessFile(String path) {
        // Check excluded patterns
        for (String pattern : excludedPatterns) {
            if (path.matches(pattern.replace("**", ".*").replace("*", "[^/]*"))) {
                return false;
            }
        }
        
        // Check supported extensions
        String extension = getFileExtension(path);
        return supportedExtensions.contains(extension);
    }
    
    /**
     * Create GitHub client with optional access token
     */
    private GitHub createGitHubClient(String accessToken) throws IOException {
        if (accessToken != null && !accessToken.isEmpty()) {
            return new GitHubBuilder().withOAuthToken(accessToken).build();
        } else if (defaultGithubToken != null && !defaultGithubToken.isEmpty()) {
            return new GitHubBuilder().withOAuthToken(defaultGithubToken).build();
        } else {
            // Anonymous access (limited rate)
            return GitHub.connectAnonymously();
        }
    }
    
    /**
     * Get GHRepository from URL
     */
    private GHRepository getGHRepository(GitHub github, String repoUrl) throws IOException {
        // Extract owner/repo from URL
        String path = repoUrl.replaceFirst("https?://github\\.com/", "")
                            .replaceFirst("\\.git$", "");
        
        String[] parts = path.split("/");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid repository URL: " + repoUrl);
        }
        
        return github.getRepository(parts[0] + "/" + parts[1]);
    }
    
    /**
     * Get last commit date for a branch
     */
    private Date getLastCommitDate(GHBranch branch) {
        try {
            // This is a workaround as GHBranch doesn't directly expose commit date
            return new Date(); // For now, return current date
        } catch (Exception e) {
            log.warn("Failed to get last commit date", e);
            return new Date();
        }
    }
    
    /**
     * Extract file extension
     */
    private String getFileExtension(String path) {
        int lastDot = path.lastIndexOf('.');
        if (lastDot > 0 && lastDot < path.length() - 1) {
            return path.substring(lastDot);
        }
        return "";
    }
    
    /**
     * Extract file name from path
     */
    private String getFileName(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return path.substring(lastSlash + 1);
        }
        return path;
    }
    
    /**
     * Detect programming language from file extension
     */
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
            ".php", "php"
        );
        
        return languageMap.getOrDefault(extension, "unknown");
    }
    
    /**
     * Data class for GitHub file
     */
    /**
     * Data class for GitHub file
     */
    public static class GitHubFile {
        private String path;
        private String name;
        private String content;
        private long size;
        private String sha;
        private String language;
        
        public GitHubFile() {}
        
        private GitHubFile(Builder builder) {
            this.path = builder.path;
            this.name = builder.name;
            this.content = builder.content;
            this.size = builder.size;
            this.sha = builder.sha;
            this.language = builder.language;
        }
        
        // Getters
        public String getPath() { return path; }
        public String getName() { return name; }
        public String getContent() { return content; }
        public long getSize() { return size; }
        public String getSha() { return sha; }
        public String getLanguage() { return language; }
        
        // Setters
        public void setPath(String path) { this.path = path; }
        public void setName(String name) { this.name = name; }
        public void setContent(String content) { this.content = content; }
        public void setSize(long size) { this.size = size; }
        public void setSha(String sha) { this.sha = sha; }
        public void setLanguage(String language) { this.language = language; }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String path;
            private String name;
            private String content;
            private long size;
            private String sha;
            private String language;
            
            public Builder path(String path) {
                this.path = path;
                return this;
            }
            
            public Builder name(String name) {
                this.name = name;
                return this;
            }
            
            public Builder content(String content) {
                this.content = content;
                return this;
            }
            
            public Builder size(long size) {
                this.size = size;
                return this;
            }
            
            public Builder sha(String sha) {
                this.sha = sha;
                return this;
            }
            
            public Builder language(String language) {
                this.language = language;
                return this;
            }
            
            public GitHubFile build() {
                return new GitHubFile(this);
            }
        }
    }
}