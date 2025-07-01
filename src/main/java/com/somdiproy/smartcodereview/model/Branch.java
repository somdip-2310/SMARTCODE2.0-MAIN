package com.somdiproy.smartcodereview.model;



import java.util.Date;


public class Branch {
    private String name;
    private Boolean isDefault;
    private Boolean isProtected;
    private String sha;
    private Date lastCommitDate;
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public Boolean getIsDefault() {
		return isDefault;
	}
	public void setIsDefault(Boolean isDefault) {
		this.isDefault = isDefault;
	}
	public Boolean getIsProtected() {
		return isProtected;
	}
	public void setIsProtected(Boolean isProtected) {
		this.isProtected = isProtected;
	}
	public String getSha() {
		return sha;
	}
	public void setSha(String sha) {
		this.sha = sha;
	}
	public Date getLastCommitDate() {
		return lastCommitDate;
	}
	public void setLastCommitDate(Date lastCommitDate) {
		this.lastCommitDate = lastCommitDate;
	}
    
    
}