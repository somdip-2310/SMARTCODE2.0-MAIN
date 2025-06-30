package com.somdiproy.smartcodereview.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

/**
 * Issue entity for DynamoDB storage
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    private Suggestion suggestion;
    
    @DynamoDbPartitionKey
    public String getAnalysisId() {
        return analysisId;
    }
    
    @DynamoDbSortKey
    @DynamoDbAttribute("issueId")
    public String getIssueId() {
        return issueId;
    }
}