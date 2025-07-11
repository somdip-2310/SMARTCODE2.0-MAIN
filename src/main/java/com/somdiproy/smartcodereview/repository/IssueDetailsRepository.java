package com.somdiproy.smartcodereview.repository;

import com.somdiproy.smartcodereview.model.Issue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Repository for Issue Details using DynamoDB
 */
@Repository
public class IssueDetailsRepository {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(IssueDetailsRepository.class);
    
    private final DynamoDbTable<Issue> issueTable;
    
    @Autowired
    public IssueDetailsRepository(DynamoDbEnhancedClient dynamoDbClient,
                                 @Value("${aws.dynamodb.tables.issue-details}") String tableName) {
        this.issueTable = dynamoDbClient.table(tableName, TableSchema.fromBean(Issue.class));
    }
    
    /**
     * Save issue
     */
    public Issue save(Issue issue) {
        issueTable.putItem(issue);
        log.debug("Saved issue: {} for analysis: {}", issue.getIssueId(), issue.getAnalysisId());
        return issue;
    }
    
    /**
     * Save multiple issues (batch)
     */
    public List<Issue> saveAll(List<Issue> issues) {
        // DynamoDB Enhanced Client doesn't have built-in batch write
        // For production, implement batch write for better performance
        issues.forEach(this::save);
        return issues;
    }
    
    /**
     * Find issue by composite key
     */
    public Optional<Issue> findByAnalysisIdAndIssueId(String analysisId, String issueId) {
        Key key = Key.builder()
                .partitionValue(analysisId)
                .sortValue(issueId)
                .build();
        
        Issue issue = issueTable.getItem(key);
        return Optional.ofNullable(issue);
    }
    
    /**
     * Find all issues for an analysis
     */
    public List<Issue> findByAnalysisId(String analysisId) {
        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder()
                        .partitionValue(analysisId)
                        .build()))
                .build();
        
        return issueTable.query(queryRequest)
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }
    
    /**
     * Find issues by severity for an analysis
     */
    public List<Issue> findByAnalysisIdAndSeverity(String analysisId, String severity) {
        return findByAnalysisId(analysisId).stream()
                .filter(issue -> severity.equals(issue.getSeverity()))
                .collect(Collectors.toList());
    }
    
    /**
     * Find issues by type for an analysis
     */
    public List<Issue> findByAnalysisIdAndType(String analysisId, String type) {
        return findByAnalysisId(analysisId).stream()
                .filter(issue -> type.equals(issue.getType()))
                .collect(Collectors.toList());
    }
    
    /**
     * Update issue
     */
    public Issue update(Issue issue) {
        return issueTable.updateItem(issue);
    }
    
    /**
     * Delete issue
     */
    public void delete(String analysisId, String issueId) {
        Key key = Key.builder()
                .partitionValue(analysisId)
                .sortValue(issueId)
                .build();
        
        issueTable.deleteItem(key);
        log.debug("Deleted issue: {} for analysis: {}", issueId, analysisId);
    }
    
    /**
     * Delete all issues for an analysis
     */
    public void deleteByAnalysisId(String analysisId) {
        List<Issue> issues = findByAnalysisId(analysisId);
        issues.forEach(issue -> delete(analysisId, issue.getIssueId()));
        log.debug("Deleted {} issues for analysis: {}", issues.size(), analysisId);
    }
    
    /**
     * Count issues by severity for an analysis
     */
    public long countBySeverity(String analysisId, String severity) {
        return findByAnalysisIdAndSeverity(analysisId, severity).size();
    }
    
    /**
     * Get issue statistics for an analysis
     */
    public IssueStatistics getStatistics(String analysisId) {
        List<Issue> issues = findByAnalysisId(analysisId);
        
        return IssueStatistics.builder()
                .total(issues.size())
                .critical(issues.stream().filter(i -> "CRITICAL".equals(i.getSeverity())).count())
                .high(issues.stream().filter(i -> "HIGH".equals(i.getSeverity())).count())
                .medium(issues.stream().filter(i -> "MEDIUM".equals(i.getSeverity())).count())
                .low(issues.stream().filter(i -> "LOW".equals(i.getSeverity())).count())
                .security(issues.stream().filter(i -> "SECURITY".equals(i.getCategory())).count())
                .performance(issues.stream().filter(i -> "PERFORMANCE".equals(i.getCategory())).count())
                .quality(issues.stream().filter(i -> "QUALITY".equals(i.getCategory())).count())
                .build();
    }
    
    /**
     * Statistics class
     */
    public static class IssueStatistics {
        private long total;
        private long critical;
        private long high;
        private long medium;
        private long low;
        private long security;
        private long performance;
        private long quality;
        
        // Constructors
        public IssueStatistics() {}
        
        // Getters and Setters
        public long getTotal() {
            return total;
        }
        
        public void setTotal(long total) {
            this.total = total;
        }
        
        public long getCritical() {
            return critical;
        }
        
        public void setCritical(long critical) {
            this.critical = critical;
        }
        
        public long getHigh() {
            return high;
        }
        
        public void setHigh(long high) {
            this.high = high;
        }
        
        public long getMedium() {
            return medium;
        }
        
        public void setMedium(long medium) {
            this.medium = medium;
        }
        
        public long getLow() {
            return low;
        }
        
        public void setLow(long low) {
            this.low = low;
        }
        
        public long getSecurity() {
            return security;
        }
        
        public void setSecurity(long security) {
            this.security = security;
        }
        
        public long getPerformance() {
            return performance;
        }
        
        public void setPerformance(long performance) {
            this.performance = performance;
        }
        
        public long getQuality() {
            return quality;
        }
        
        public void setQuality(long quality) {
            this.quality = quality;
        }
        
        // Builder pattern
        public static IssueStatisticsBuilder builder() {
            return new IssueStatisticsBuilder();
        }
        
        public static class IssueStatisticsBuilder {
            private IssueStatistics stats = new IssueStatistics();
            
            public IssueStatisticsBuilder total(long total) {
                stats.setTotal(total);
                return this;
            }
            
            public IssueStatisticsBuilder critical(long critical) {
                stats.setCritical(critical);
                return this;
            }
            
            public IssueStatisticsBuilder high(long high) {
                stats.setHigh(high);
                return this;
            }
            
            public IssueStatisticsBuilder medium(long medium) {
                stats.setMedium(medium);
                return this;
            }
            
            public IssueStatisticsBuilder low(long low) {
                stats.setLow(low);
                return this;
            }
            
            public IssueStatisticsBuilder security(long security) {
                stats.setSecurity(security);
                return this;
            }
            
            public IssueStatisticsBuilder performance(long performance) {
                stats.setPerformance(performance);
                return this;
            }
            
            public IssueStatisticsBuilder quality(long quality) {
                stats.setQuality(quality);
                return this;
            }
            
            public IssueStatistics build() {
                return stats;
            }
        }
    }
}