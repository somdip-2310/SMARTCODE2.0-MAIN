package com.somdiproy.smartcodereview.model;

/**
 * Repository model
 */
public class Repository {
    private String url;
    private String name;
    private String fullName;
    private String description;
    private String owner;
    private String defaultBranch;
    private Boolean isPrivate;
    private String language;
    private Long size;
    private Integer starsCount;
    private Integer forksCount;
    
    // Default constructor
    public Repository() {}
    
    // Getters and Setters
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getFullName() {
        return fullName;
    }
    
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getOwner() {
        return owner;
    }
    
    public void setOwner(String owner) {
        this.owner = owner;
    }
    
    public String getDefaultBranch() {
        return defaultBranch;
    }
    
    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }
    
    public Boolean getIsPrivate() {
        return isPrivate;
    }
    
    public void setIsPrivate(Boolean isPrivate) {
        this.isPrivate = isPrivate;
    }
    
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(String language) {
        this.language = language;
    }
    
    public Long getSize() {
        return size;
    }
    
    public void setSize(Long size) {
        this.size = size;
    }
    
    public Integer getStarsCount() {
        return starsCount;
    }
    
    public void setStarsCount(Integer starsCount) {
        this.starsCount = starsCount;
    }
    
    public Integer getForksCount() {
        return forksCount;
    }
    
    public void setForksCount(Integer forksCount) {
        this.forksCount = forksCount;
    }
}