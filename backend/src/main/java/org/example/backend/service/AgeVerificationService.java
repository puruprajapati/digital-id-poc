package org.example.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.backend.dto.VerificationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgeVerificationService {

  private final VerificationSessionStore sessionStore;
  private final Map<String, String> callbackTokens = new ConcurrentHashMap<>();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  public AgeVerificationService(VerificationSessionStore sessionStore) {
    this.sessionStore = sessionStore;
  }

  public VerificationResponse createVerificationSession(String sessionId, Integer minAge, String baseUrl, String mode) {
    VerificationSession session = new VerificationSession();
    session.setSessionId(sessionId);
    session.setMinAge(minAge != null ? minAge : 18);
    session.setCreatedAt(System.currentTimeMillis());
    session.setVerified(false);

    sessionStore.put(sessionId, session);

    String verificationUrl = generateGoogleWalletUrl(sessionId, baseUrl, mode);


    return VerificationResponse.builder()
      .success(true)
      .sessionId(sessionId)
      .message("Verification session created")
      .requestUrl(verificationUrl)
      .build();
  }

  public VerificationResponse verifyAge(String sessionId, String credential) {
    VerificationSession session = sessionStore.get(sessionId);

    if (session == null) {
      return VerificationResponse.builder()
        .success(false)
        .message("Invalid session")
        .build();
    }

    try {
      // Decode the credential (JWT token from Google Wallet)
      String[] parts = credential.split("\\.");
      if (parts.length != 3) {
        throw new IllegalArgumentException("Invalid JWT format");
      }

      // Decode payload
      String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
      JsonNode jsonNode = objectMapper.readTree(payload);

      // Extract date of birth - Google Wallet uses 'birthdate' claim
      String dateOfBirth = jsonNode.has("birthdate") ?
        jsonNode.get("birthdate").asText() : null;

      // Also check for 'date_of_birth' as alternate format
      if (dateOfBirth == null || dateOfBirth.isEmpty()) {
        dateOfBirth = jsonNode.has("date_of_birth") ?
          jsonNode.get("date_of_birth").asText() : null;
      }

      if (dateOfBirth == null || dateOfBirth.isEmpty()) {
        return VerificationResponse.builder()
          .success(false)
          .message("Date of birth not found in credential")
          .build();
      }

      // Calculate age
      LocalDate birthDate = LocalDate.parse(dateOfBirth, DateTimeFormatter.ISO_DATE);
      LocalDate currentDate = LocalDate.now();
      int age = Period.between(birthDate, currentDate).getYears();

      boolean isVerified = age >= session.getMinAge();
      session.setVerified(isVerified);
      session.setAge(age);

      // Extract additional information if available
      String givenName = jsonNode.has("given_name") ? jsonNode.get("given_name").asText() : null;
      String familyName = jsonNode.has("family_name") ? jsonNode.get("family_name").asText() : null;


      return VerificationResponse.builder()
        .success(true)
        .verified(isVerified)
        .age(age)
        .minAge(session.getMinAge())
        .givenName(givenName)
        .familyName(familyName)
        .message(isVerified ? "Age verification successful" : "Age verification failed - minimum age not met")
        .build();

    } catch (Exception e) {
      return VerificationResponse.builder()
        .success(false)
        .message("Error processing credential: " + e.getMessage())
        .build();
    }
  }

  public VerificationResponse getVerificationStatus(String sessionId) {
    VerificationSession session = sessionStore.get(sessionId);

    if (session == null) {
      return VerificationResponse.builder()
        .success(false)
        .message("Session not found")
        .build();
    }

    return VerificationResponse.builder()
      .success(true)
      .sessionId(sessionId)
      .verified(session.isVerified())
      .age(session.getAge())
      .minAge(session.getMinAge())
      .message("Session status retrieved")
      .build();
  }

  private String generateGoogleWalletRequest(String sessionId, int minAge) {
    // Generate the Google Wallet ID verification request URL
    // This would include parameters for the type of verification needed
    return String.format(
      "googlewallet://identity/verify?session=%s&minAge=%d&redirectUrl=http://localhost:3000/verify",
      sessionId, minAge
    );
  }

  private String generateGoogleWalletUrl(String sessionId, String baseUrl, String mode) {
    // Google Wallet Identity API URL structure
    // This creates a deep link that opens Google Wallet on mobile devices

    String redirectUri = baseUrl + "/api/verification/callback";

    // Encode query parameter values to avoid '%' sequences being interpreted by String.format
    String encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);

    if("GOOGLE_PAY_URL".equals(mode)) {
      String scope = "openid profile date_of_birth";
      String encodedScope = URLEncoder.encode(scope, StandardCharsets.UTF_8);

      // Build URL with %s placeholders only for the values we supply
      return String.format(
        "https://pay.google.com/gp/v/issuer/verify?" +
          "session_id=%s&" +
          "redirect_uri=%s&" +
          "response_type=id_token&" +
          "scope=%s",
        sessionId,
        encodedRedirect,
        encodedScope
      );
    } else {
      String scope = "openid profile date_of_birth";
      String encodedScope = URLEncoder.encode(scope, StandardCharsets.UTF_8);

      // Build URL with %s placeholders only for the values we supply
      return String.format(
        "https://pay.google.com/gp/v/issuer/verify?" +
          "session_id=%s&" +
          "redirect_uri=%s&" +
          "response_type=id_token&" +
          "scope=%s",
        sessionId,
        encodedRedirect,
        encodedScope
      );
    }

  }

  public void storeCallbackToken(String sessionId, String token) {
    callbackTokens.put(sessionId, token);
  }

  public String getStoredToken(String sessionId) {
    return callbackTokens.get(sessionId);
  }
}

