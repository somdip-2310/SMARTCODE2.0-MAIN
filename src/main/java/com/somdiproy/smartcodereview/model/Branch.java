package com.somdiproy.smartcodereview.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Branch model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Branch {
    private String name;
    private Boolean isDefault;
    private Boolean isProtected;
    private String sha;
    private Date lastCommitDate;
}