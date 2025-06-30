package com.somdiproy.smartcodereview.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.util.List;

/**
 * Suggestion model for issue fixes
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class Suggestion {
    
    private ImmediateFix immediateFix;
    private BestPractice bestPractice;
    private Testing testing;
    private Prevention prevention;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @DynamoDbBean
    public static class ImmediateFix {
        private String title;
        private String searchCode;
        private String replaceCode;
        private String explanation;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @DynamoDbBean
    public static class BestPractice {
        private String title;
        private String code;
        private List<String> benefits;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @DynamoDbBean
    public static class Testing {
        private String testCase;
        private List<String> validationSteps;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @DynamoDbBean
    public static class Prevention {
        private List<String> guidelines;
        private List<Tool> tools;
        private List<String> codeReviewChecklist;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @DynamoDbBean
    public static class Tool {
        private String name;
        private String description;
    }
}