package com.somdiproy.smartcodereview.repository;

import com.somdiproy.smartcodereview.model.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.Iterator;
import java.util.Optional;

/**
 * Repository for Session management using DynamoDB
 */
@Repository
public class SessionRepository {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SessionRepository.class);
    
    private final DynamoDbTable<Session> sessionTable;
    
    @Autowired
    public SessionRepository(DynamoDbEnhancedClient dynamoDbClient,
                           @Value("${aws.dynamodb.tables.sessions}") String tableName) {
        this.sessionTable = dynamoDbClient.table(tableName, TableSchema.fromBean(Session.class));
    }
    
    public Optional<Session> findById(String sessionId) {
        Key key = Key.builder()
                .partitionValue(sessionId)
                .build();
        
        Session session = sessionTable.getItem(key);
        return Optional.ofNullable(session);
    }
    
    public Optional<Session> findByEmail(String email) {
        QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder()
                        .partitionValue(email)
                        .build()))
                .build();
        
        Iterator<Page<Session>> pages = sessionTable.index("EmailIndex")
                .query(queryRequest)
                .iterator();
        
        if (pages.hasNext()) {
            Page<Session> firstPage = pages.next();
            return firstPage.items().stream().findFirst();
        }
        
        return Optional.empty();
    }
    
    public void delete(String sessionId) {
        Key key = Key.builder()
                .partitionValue(sessionId)
                .build();
        
        sessionTable.deleteItem(key);
    }
    
    public Session update(Session session) {
        sessionTable.putItem(session);
        log.debug("Updated session in DynamoDB: sessionId={}, verificationStatus={}", 
                  session.getSessionId(), session.getVerificationStatus());
        return session;
    }

    public Session save(Session session) {
        sessionTable.putItem(session);
        log.debug("Saved session to DynamoDB: sessionId={}, verificationStatus={}", 
                  session.getSessionId(), session.getVerificationStatus());
        return session;
    }
}