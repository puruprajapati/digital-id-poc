package org.example.backend.service;

public class VerificationSession {
  private String sessionId;
  private int minAge;
  private boolean verified;
  private Integer age;
  private long createdAt;

  public VerificationSession() {}

  public String getSessionId() { return sessionId; }
  public void setSessionId(String sessionId) { this.sessionId = sessionId; }

  public int getMinAge() { return minAge; }
  public void setMinAge(int minAge) { this.minAge = minAge; }

  public boolean isVerified() { return verified; }
  public void setVerified(boolean verified) { this.verified = verified; }

  public Integer getAge() { return age; }
  public void setAge(Integer age) { this.age = age; }

  public long getCreatedAt() { return createdAt; }
  public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}

