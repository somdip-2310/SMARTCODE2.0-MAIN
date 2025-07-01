package com.somdiproy.smartcodereview.repository;

import com.somdiproy.smartcodereview.model.Analysis;
import com.somdiproy.smartcodereview.model.AnalysisResult;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Repository for Analysis Results using DynamoDB
 */
@Repository
public class AnalysisRepository {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AnalysisRepository.class);
    
    private final DynamoDbTable<AnalysisResult> analysisTable;
    
    @Autowired
    public AnalysisRepository(DynamoDbEnhancedClient dynamoDbClient,
                             @Value("${aws.dynamodb.tables.analysis-results}") String tableName) {
        this.analysisTable = dynamoDbClient.table(tableName, TableSchema.fromBean(AnalysisResult.class));
    }
    
    /**
     * Save analysis result
     */
    public AnalysisResult save(AnalysisResult analysis) {
        // Set TTL for 7 days
        long ttl = Instant.now().getEpochSecond() + (7 * 24 * 60 * 60);
        analysis.setTtl(ttl);
        analysis.setExpiresAt(ttl);
        
        analysisTable.putItem(analysis);
        log.debug("Saved analysis: {}", analysis.getAnalysisId());
        return analysis;
    }
    
    /**
     * Find analysis by ID
     */
    public Optional<AnalysisResult> findById(String analysisId) {
        Key key = Key.builder()
                .partitionValue(analysisId)
                .build();
        
        AnalysisResult analysis = analysisTable.getItem(key);
        return Optional.ofNullable(analysis);
    }
    
    /**
     * Find all analyses for a session
     */
    public List<AnalysisResult> findBySessionId(String sessionId) {
        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder()
                        .partitionValue(sessionId)
                        .build()))
                .build();
        
        return analysisTable.index("SessionIndex")
                .query(queryRequest)
                .items()
                .stream()
                .collect(Collectors.toList());
    }
    
    /**
     * Update analysis result
     */
    public AnalysisResult update(AnalysisResult analysis) {
        return analysisTable.updateItem(analysis);
    }
    
    /**
     * Delete analysis result
     */
    public void delete(String analysisId) {
        Key key = Key.builder()
                .partitionValue(analysisId)
                .build();
        
        analysisTable.deleteItem(key);
        log.debug("Deleted analysis: {}", analysisId);
    }
    
    /**
     * Find analyses by repository and time range
     */
    public List<AnalysisResult> findByRepositoryAndTimeRange(String repository, 
                                                            long startTime, 
                                                            long endTime) {
        // This would require a GSI on repository and timestamp
        // For now, we'll scan and filter (not optimal for production)
        return analysisTable.scan()
                .items()
                .stream()
                .filter(analysis -> analysis.getRepository().equals(repository))
                .filter(analysis -> analysis.getStartedAt() >= startTime && 
                                  analysis.getStartedAt() <= endTime)
                .collect(Collectors.toList());
    }
}