package com.somdiproy.smartcodereview.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import com.somdiproy.smartcodereview.service.EmailService;
/**
 * AWS SDK Configuration for Smart Code Review Platform
 * Configured for direct AWS service access (no LocalStack)
 */
@Slf4j
@Configuration
public class AWSConfig {
	
	private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${aws.profile:default}")
    private String awsProfile;
    
    @Autowired
    private Environment environment;

    /**
     * Configure AWS credentials provider
     * Uses AWS CLI configuration or environment variables
     */
    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        String activeProfile = environment.getActiveProfiles().length > 0 ? 
                              environment.getActiveProfiles()[0] : "local";
        
        if ("local".equals(activeProfile)) {
            // Local development: Try profile first, then default
            try {
                log.info("Local profile detected - attempting to use AWS profile: {}", awsProfile);
                return ProfileCredentialsProvider.create(awsProfile);
            } catch (Exception e) {
                log.info("Profile credentials not found, falling back to default chain");
                return DefaultCredentialsProvider.create();
            }
        } else {
            // Production/Container: Use default chain (ECS Task Role, Environment vars, etc.)
            log.info("Production profile detected - using default credentials provider");
            return DefaultCredentialsProvider.create();
        }
    }

    /**
     * DynamoDB client configuration
     */
    @Bean
    public DynamoDbClient dynamoDbClient(AwsCredentialsProvider credentialsProvider) {
        return DynamoDbClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    /**
     * DynamoDB Enhanced Client for easier object mapping
     */
    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    /**
     * S3 client configuration
     */
    @Bean
    public S3Client s3Client(AwsCredentialsProvider credentialsProvider) {
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    
    /**
     * Lambda client configuration
     */
    @Bean
    public LambdaClient lambdaClient(AwsCredentialsProvider credentialsProvider) {
        return LambdaClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(credentialsProvider)
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                    .apiCallTimeout(Duration.ofMinutes(30))
                    .apiCallAttemptTimeout(Duration.ofMinutes(30))
                    .retryPolicy(RetryPolicy.builder()
                        .numRetries(3)
                        .build())
                    .build())
                .build();
    }

    /**
     * SQS client configuration
     */
    @Bean
    public SqsClient sqsClient(AwsCredentialsProvider credentialsProvider) {
        return SqsClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    /**
     * SES client configuration for email sending
     */
    @Bean
    public SesClient sesClient(AwsCredentialsProvider credentialsProvider) {
        return SesClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    /**
     * Bedrock Runtime client for Nova models
     */
    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient(AwsCredentialsProvider credentialsProvider) {
        // Bedrock is only available in specific regions
        String bedrockRegion = "us-east-1"; // Nova models are in us-east-1
        
        return BedrockRuntimeClient.builder()
                .region(Region.of(bedrockRegion))
                .credentialsProvider(credentialsProvider)
                .build();
    }


}