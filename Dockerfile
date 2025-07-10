# Dockerfile for Smart Code Review Service
# Multi-stage build for optimized production image

# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17 AS build

# Set working directory
WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:17-jre-alpine

# Install curl for health checks
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -g 1000 spring && adduser -u 1000 -G spring -s /bin/sh -D spring

# Set working directory
WORKDIR /app

# Create necessary directories
RUN mkdir -p /app/logs /app/uploads && chown -R spring:spring /app

# Copy JAR file from build stage
COPY --from=build /app/target/*.jar app.jar

# Change ownership to spring user
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# Expose port 8083
EXPOSE 8083

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8083/actuator/health || exit 1

# Environment Variables
ENV SPRING_PROFILES_ACTIVE=production
ENV SERVER_PORT=8083
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# AWS Configuration (will be overridden by ECS Task Role)
ENV AWS_REGION=us-east-1
ENV AWS_DEFAULT_REGION=us-east-1

# Smart Code Review specific environment variables
ENV AWS_BEDROCK_REGION=us-east-1
ENV AWS_BEDROCK_MODEL_ID=anthropic.claude-3-sonnet-20240229-v1:0
ENV AWS_S3_BUCKET=smartcode-uploads
ENV CORS_ALLOWED_ORIGINS=https://smartcode.somdip.dev,https://somdip.dev,http://localhost:3000

# Demo session configuration
ENV DEMO_SESSION_DURATION=7
ENV ANALYSIS_CODE_MAX_SIZE=100000
ENV ANALYSIS_FILE_MAX_SIZE=52428800

# Integration URLs
ENV PORTFOLIO_SERVICE_URL=https://somdip.dev
ENV HR_DEMO_SERVICE_URL=https://demos.somdip.dev

# Google Analytics
ENV GOOGLE_ANALYTICS_ID=G-TJMD3KM77H
ENV GOOGLE_ANALYTICS_ENABLED=true

# Security hardening
ENV SPRING_SECURITY_REQUIRE_SSL=false
ENV SERVER_USE_FORWARD_HEADERS=true
ENV SERVER_FORWARD_HEADERS_STRATEGY=native

# Performance tuning for container environment
ENV SPRING_JMX_ENABLED=false
ENV SPRING_TASK_EXECUTION_POOL_CORE_SIZE=5
ENV SPRING_TASK_EXECUTION_POOL_MAX_SIZE=20

# File upload configuration
ENV SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=50MB
ENV SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE=50MB

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]

# Labels for metadata
LABEL maintainer="Somdip Roy <somdiproy.roy@gmail.com>"
LABEL description="Smart Code Review Service - AI-Powered Code Analysis Platform"
LABEL version="1.0.0"
LABEL org.opencontainers.image.title="Smart Code Review Service"
LABEL org.opencontainers.image.description="AI-powered code review and analysis platform using Amazon Bedrock"
LABEL org.opencontainers.image.vendor="Somdip Roy"
LABEL org.opencontainers.image.version="1.0.0"
LABEL org.opencontainers.image.created="2025-01-20"
LABEL org.opencontainers.image.source="https://github.com/somdiproy/smart-code-review"
LABEL org.opencontainers.image.url="https://smartcode.somdip.dev"
LABEL org.opencontainers.image.documentation="https://smartcode.somdip.dev/docs"
