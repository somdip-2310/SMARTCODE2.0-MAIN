package com.somdiproy.smartcodereview.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Repository model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Repository {
    private String url;
    private String name;
    private String fullName;
    private String description;
    private String owner;
    private String defaultBranch;
    private Boolean isPrivate;
    private String language;
    private Long size;
    private Integer starsCount;
    private Integer forksCount;
}