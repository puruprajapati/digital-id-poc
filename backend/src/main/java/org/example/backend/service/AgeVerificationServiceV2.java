package org.example.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import org.example.backend.dto.VerificationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

// instead of deeplink or standard URL use
// navigator.credentials.get() in front-end to trigger DC API flow
@Service
public class AgeVerificationServiceV2 {
  private final VerificationSessionStore sessionStore;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired(required = false)
  private CborParserService cborParserService;

  @Autowired
  public AgeVerificationServiceV2(VerificationSessionStore sessionStore) {
    this.sessionStore = sessionStore;
  }

  public VerificationResponse verifyAge(String sessionId, String credentialJson) {
    VerificationSession session = sessionStore.get(sessionId);

    if (session == null) {
      return VerificationResponse.builder()
        .success(false)
        .message("Invalid session")
        .build();
    }

    try {
      // Parse the credential response from Digital Credentials API
      JsonNode responseNode = objectMapper.readTree(credentialJson);

      System.out.println("Received credential JSON: " + credentialJson);

      // The response structure from openid4vp contains a 'response' field (JWE encrypted)
      // or 'vp_token' if using dc_api mode (unencrypted)

      // The vp_token structure from Google Wallet is:
      // { "vp_token": { "credential-id": ["base64-cbor-data"] } }
      String vpTokenData  = null;

      if (responseNode.has("vp_token")) {
        JsonNode vpToken = responseNode.get("vp_token");
        System.out.println("vp_token type: " + vpToken.getNodeType());

        if (vpToken.isObject()) {
          // Get the first credential (there should only be one)
          JsonNode firstCredential = vpToken.elements().next();
          if (firstCredential.isArray() && firstCredential.size() > 0) {
            vpTokenData = firstCredential.get(0).asText();
            System.out.println("Extracted VP token data (first 100 chars): " +
              vpTokenData.substring(0, Math.min(100, vpTokenData.length())));
          }
        } else if (vpToken.isTextual()) {
          vpTokenData = vpToken.asText();
        }
      }

      if (vpTokenData  == null || vpTokenData .isEmpty()) {
        return VerificationResponse.builder()
          .success(false)
          .message("No vp_token found in response. Response: " + credentialJson)
          .build();
      }

      // The vpTokenData is base64-encoded CBOR (ISO mdoc format)
      // Use URL-safe decoder because Google Wallet often uses URL-safe base64 ("-" and "_" characters)
      byte[] cborData = Base64.getUrlDecoder().decode(vpTokenData);
      System.out.println("Decoded CBOR data length: " + cborData.length + " bytes");


      // Parse the VP token (it's typically a base64-encoded CBOR structure for mdoc)
      // For ISO mdoc format, we need to decode CBOR
      // Since this is complex, we'll parse what we can from the structure

      // Try to parse with CBOR parser if available
      String birthDate = null;
      String givenName = null;
      String familyName = null;
      Boolean ageOver18 = null;
      Boolean ageOver21 = null;

      if (cborParserService != null) {
        try {
          CborParserService.MdocData mdocData = cborParserService.parseMdocCbor(vpTokenData);
          birthDate = mdocData.getBirthDate();
          givenName = mdocData.getGivenName();
          familyName = mdocData.getFamilyName();
          ageOver18 = mdocData.getAgeOver18();
          ageOver21 = mdocData.getAgeOver21();

          System.out.println("Parsed mdoc data - birthDate: " + birthDate +
            ", ageOver18: " + ageOver18 +
            ", ageOver21: " + ageOver21);
        } catch (Exception e) {
          System.err.println("CBOR parsing failed: " + e.getMessage());
          e.printStackTrace();
        }
      }



      // Determine verification result
      boolean isVerified = false;
      Integer age = null;

      // If we have age_over_XX claims, use those
      if (session.getMinAge() >= 21 && ageOver21 != null) {
        isVerified = ageOver21;
        age = isVerified ? 21 : null; // We know they're at least 21 or not
      } else if (ageOver18 != null) {
        isVerified = ageOver18;
        age = isVerified ? 18 : null; // We know they're at least 18 or not
      }

      // If we have birth_date, calculate actual age
      if (birthDate != null && !birthDate.isEmpty()) {
        try {
          LocalDate birth = LocalDate.parse(birthDate, DateTimeFormatter.ISO_DATE);
          LocalDate currentDate = LocalDate.now();
          int calculatedAge = Period.between(birth, currentDate).getYears();
          age = calculatedAge;
          isVerified = calculatedAge >= session.getMinAge();
          System.out.println("Calculated age from birth_date: " + calculatedAge);
        } catch (Exception e) {
          System.err.println("Failed to parse birth_date: " + e.getMessage());
          // If birth_date parsing fails but age_over_X claims exist, use those
          if (ageOver18 == null && ageOver21 == null) {
            return VerificationResponse.builder()
              .success(false)
              .message("Could not parse birth_date and no age claims found. Birth date: " + birthDate)
              .build();
          }
        }
      } else {
        // No birth_date available - check if we have age claims
        if (ageOver18 == null && ageOver21 == null) {
          // No age information at all
          return VerificationResponse.builder()
            .success(false)
            .message("Could not extract birth_date or age claims from credential. Raw response: " + credentialJson)
            .build();
        }
        // We have age claims but no birth_date - this is OK, use the age claims
        System.out.println("No birth_date available, using age_over_X claims: ageOver18=" + ageOver18 + ", ageOver21=" + ageOver21);
      }

      // If we still don't have verification result, something went wrong
      if (age == null && !isVerified) {
        System.out.println("No age information available for verification");
        return VerificationResponse.builder()
          .success(false)
          .message("Could not determine age from credential")
          .build();
      }

      session.setVerified(isVerified);
      session.setAge(age);

      String message = isVerified
        ? "Age verification successful - User is " + (age != null ? age : "18") + "+ years old"
        : "Age verification failed - User does not meet minimum age requirement";

      return VerificationResponse.builder()
        .success(true)
        .verified(isVerified)
        .age(age)
        .minAge(session.getMinAge())
        .givenName(givenName)
        .familyName(familyName)
        .message(message)
        .build();

    } catch (Exception e) {
      e.printStackTrace();
      return VerificationResponse.builder()
        .success(false)
        .message("Error processing credential: " + e.getMessage() + ". Raw data: " + credentialJson)
        .build();
    }
  }

  private String extractBirthDate(JsonNode responseNode) {
    // Try multiple possible paths for birth_date
    String[] possiblePaths = {
      "birth_date",
      "birthdate",
      "date_of_birth",
      "org.iso.18013.5.1/birth_date"
    };

    for (String path : possiblePaths) {
      if (responseNode.has(path)) {
        return responseNode.get(path).asText();
      }
    }

    // Check in nested structures
    if (responseNode.has("vp")) {
      JsonNode vp = responseNode.get("vp");
      if (vp.has("verifiableCredential")) {
        JsonNode cred = vp.get("verifiableCredential");
        if (cred.isArray() && cred.size() > 0) {
          JsonNode firstCred = cred.get(0);
          if (firstCred.has("credentialSubject")) {
            JsonNode subject = firstCred.get("credentialSubject");
            for (String path : possiblePaths) {
              if (subject.has(path)) {
                return subject.get(path).asText();
              }
            }
          }
        }
      }
    }

    return null;
  }

  private String extractClaim(JsonNode responseNode, String claimName) {
    if (responseNode.has(claimName)) {
      return responseNode.get(claimName).asText();
    }

    // Check in nested structures
    if (responseNode.has("vp")) {
      JsonNode vp = responseNode.get("vp");
      if (vp.has("verifiableCredential")) {
        JsonNode cred = vp.get("verifiableCredential");
        if (cred.isArray() && cred.size() > 0) {
          JsonNode firstCred = cred.get(0);
          if (firstCred.has("credentialSubject")) {
            JsonNode subject = firstCred.get("credentialSubject");
            if (subject.has(claimName)) {
              return subject.get(claimName).asText();
            }
          }
        }
      }
    }

    return null;
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

}
