package com.somdiproy.smartcodereview.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * AWS SDK Configuration for Smart Code Review Platform
 * Configured for direct AWS service access (no LocalStack)
 */
@Slf4j
@Configuration
public class AWSConfig {

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${aws.profile:default}")
    private String awsProfile;

    /**
     * Configure AWS credentials provider
     * Uses AWS CLI configuration or environment variables
     */
    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        // First try to use profile credentials (from ~/.aws/credentials)
        try {
            return ProfileCredentialsProvider.create(awsProfile);
        } catch (Exception e) {
            //log.info("Profile credentials not found, falling back to default chain");
            // Fall back to default credentials provider chain
            // This will check environment variables, system properties, etc.
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