package com.somdiproy.smartcodereview.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import java.util.List;

/**
 * Suggestion model for issue fixes
 */
@DynamoDbBean
public class Suggestion {
    
    private String issueDescription;
    private ImmediateFix immediateFix;
    private BestPractice bestPractice;
    private Testing testing;
    private Prevention prevention;
    
    // Default constructor
    public Suggestion() {}
    
    // Getter and Setter for issueDescription
    public String getIssueDescription() {
        return issueDescription;
    }
    
    public void setIssueDescription(String issueDescription) {
        this.issueDescription = issueDescription;
    }
    
    // Getters and Setters
    public ImmediateFix getImmediateFix() {
        return immediateFix;
    }
    
    public void setImmediateFix(ImmediateFix immediateFix) {
        this.immediateFix = immediateFix;
    }
    
    public BestPractice getBestPractice() {
        return bestPractice;
    }
    
    public void setBestPractice(BestPractice bestPractice) {
        this.bestPractice = bestPractice;
    }
    
    public Testing getTesting() {
        return testing;
    }
    
    public void setTesting(Testing testing) {
        this.testing = testing;
    }
    
    public Prevention getPrevention() {
        return prevention;
    }
    
    public void setPrevention(Prevention prevention) {
        this.prevention = prevention;
    }
    
    // Nested Classes
    @DynamoDbBean
    public static class ImmediateFix {
        private String title;
        private String searchCode;
        private String replaceCode;
        private String explanation;
        
        // Default constructor
        public ImmediateFix() {}
        
        // Getters and Setters
        public String getTitle() {
            return title;
        }
        
        public void setTitle(String title) {
            this.title = title;
        }
        
        public String getSearchCode() {
            return searchCode;
        }
        
        public void setSearchCode(String searchCode) {
            this.searchCode = searchCode;
        }
        
        public String getReplaceCode() {
            return replaceCode;
        }
        
        public void setReplaceCode(String replaceCode) {
            this.replaceCode = replaceCode;
        }
        
        public String getExplanation() {
            return explanation;
        }
        
        public void setExplanation(String explanation) {
            this.explanation = explanation;
        }
    }
    
    @DynamoDbBean
    public static class BestPractice {
        private String title;
        private String code;
        private List<String> benefits;
        
        // Default constructor
        public BestPractice() {}
        
        // Getters and Setters
        public String getTitle() {
            return title;
        }
        
        public void setTitle(String title) {
            this.title = title;
        }
        
        public String getCode() {
            return code;
        }
        
        public void setCode(String code) {
            this.code = code;
        }
        
        public List<String> getBenefits() {
            return benefits;
        }
        
        public void setBenefits(List<String> benefits) {
            this.benefits = benefits;
        }
    }
    
    @DynamoDbBean
    public static class Testing {
        private String testCase;
        private List<String> validationSteps;
        
        // Default constructor
        public Testing() {}
        
        // Getters and Setters
        public String getTestCase() {
            return testCase;
        }
        
        public void setTestCase(String testCase) {
            this.testCase = testCase;
        }
        
        public List<String> getValidationSteps() {
            return validationSteps;
        }
        
        public void setValidationSteps(List<String> validationSteps) {
            this.validationSteps = validationSteps;
        }
    }
    
    @DynamoDbBean
    public static class Prevention {
        private List<String> guidelines;
        private List<Tool> tools;
        private List<String> codeReviewChecklist;
        
        // Default constructor
        public Prevention() {}
        
        // Getters and Setters
        public List<String> getGuidelines() {
            return guidelines;
        }
        
        public void setGuidelines(List<String> guidelines) {
            this.guidelines = guidelines;
        }
        
        public List<Tool> getTools() {
            return tools;
        }
        
        public void setTools(List<Tool> tools) {
            this.tools = tools;
        }
        
        public List<String> getCodeReviewChecklist() {
            return codeReviewChecklist;
        }
        
        public void setCodeReviewChecklist(List<String> codeReviewChecklist) {
            this.codeReviewChecklist = codeReviewChecklist;
        }
    }
    
    @DynamoDbBean
    public static class Tool {
        private String name;
        private String description;
        
        // Default constructor
        public Tool() {}
        
        // Getters and Setters
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
    }
}