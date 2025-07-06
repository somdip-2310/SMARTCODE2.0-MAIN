package com.somdiproy.smartcodereview.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

/**
 * Issue entity for DynamoDB storage
 */
@DynamoDbBean
public class Issue {
    
    private String analysisId;
    private String issueId;
    private String type;
    private String title;
    private String description;
    private String severity;
    private String category;
    private String file;
    private Integer line;
    private Integer column;
    private String code;
    private String language;
    private String cwe;
    private Double cvssScore;
    private String cveId;
    private Double cveScore;
    private Suggestion suggestion;
    
    // Constructors
    public Issue() {}
    
    // Getters and Setters
    @DynamoDbPartitionKey
    public String getAnalysisId() {
        return analysisId;
    }
    
    public void setAnalysisId(String analysisId) {
        this.analysisId = analysisId;
    }
    
    @DynamoDbSortKey
    @DynamoDbAttribute("issueId")
    public String getIssueId() {
        return issueId;
    }
    
    public void setIssueId(String issueId) {
        this.issueId = issueId;
    }
    
    @DynamoDbAttribute("type")
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    @DynamoDbAttribute("title")
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    @DynamoDbAttribute("description")
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    @DynamoDbAttribute("severity")
    public String getSeverity() {
        return severity;
    }
    
    public void setSeverity(String severity) {
        this.severity = severity;
    }
    
    @DynamoDbAttribute("category")
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    @DynamoDbAttribute("file")
    public String getFile() {
        return file;
    }
    
    public void setFile(String file) {
        this.file = file;
    }
    
    @DynamoDbAttribute("line")
    public Integer getLine() {
        return line;
    }
    
    public void setLine(Integer line) {
        this.line = line;
    }
    
    @DynamoDbAttribute("column")
    public Integer getColumn() {
        return column;
    }
    
    public void setColumn(Integer column) {
        this.column = column;
    }
    
    @DynamoDbAttribute("code")
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
    
    @DynamoDbAttribute("language")
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(String language) {
        this.language = language;
    }
    
    @DynamoDbAttribute("cwe")
    public String getCwe() {
        return cwe;
    }
    
    public void setCwe(String cwe) {
        this.cwe = cwe;
    }
    
    @DynamoDbAttribute("cvssScore")
    public Double getCvssScore() {
        return cvssScore;
    }
    
   
    
    public void setCvssScore(Double cvssScore) {
		this.cvssScore = cvssScore;
	}
    
    @DynamoDbAttribute("cveId")
	public String getCveId() {
        return cveId;
    }
    
    public void setCveId(String cveId) {
        this.cveId = cveId;
    }
    
    @DynamoDbAttribute("cveScore")
    public Double getCveScore() {
        return cveScore;
    }
    
    public void setCveScore(Double cveScore) {
        this.cveScore = cveScore;
    }
    
    @DynamoDbAttribute("suggestion")
    public Suggestion getSuggestion() {
        return suggestion;
    }
    
    public void setSuggestion(Suggestion suggestion) {
        this.suggestion = suggestion;
    }
}