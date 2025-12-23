package org.example.backend.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationRequest {
  private String sessionId;
  private Integer minAge;
  private Object credential;  // Accept as Object, will be converted to JSON string in controller
  private String mode;

  public String getCredentialAsJson() {
    if (credential == null) {
      return null;
    }
    // If it's already a string, return it
    if (credential instanceof String) {
      return (String) credential;
    }
    // Otherwise convert to JSON string
    try {
      ObjectMapper mapper = new ObjectMapper();
      return mapper.writeValueAsString(credential);
    } catch (Exception e) {
      return credential.toString();
    }
  }
}
