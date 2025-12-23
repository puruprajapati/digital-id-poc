package org.example.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationResponse {
  private boolean success;
  private String sessionId;
  private Boolean verified;
  private Integer age;
  private Integer minAge;
  private String message;
  private String requestUrl;
  private String credential;
  private String givenName;
  private String familyName;
}
