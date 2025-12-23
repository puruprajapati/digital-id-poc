package org.example.backend.controller;

import org.example.backend.dto.VerificationRequest;
import org.example.backend.dto.VerificationResponse;
import org.example.backend.service.AgeVerificationService;
import org.example.backend.service.AgeVerificationServiceV2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/verification/v2")
public class AgeVerificationControllerV2 {
  @Autowired
  private AgeVerificationServiceV2 verificationServiceV2;

  @Autowired
  private AgeVerificationService verificationService;

  @Value("${app.base-url:http://localhost:8080}")
  private String baseUrl;

  @PostMapping("/initiate")
  public ResponseEntity<VerificationResponse> initiateVerification(@RequestBody VerificationRequest request) {
    try {
      System.out.println("Received initation request: " + request.toString());
      String sessionId = UUID.randomUUID().toString();
      VerificationResponse response = verificationService.createVerificationSession(sessionId, request.getMinAge(), baseUrl, request.getMode());
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(
        VerificationResponse.builder()
          .success(false)
          .message("Failed to initiate verification: " + e.getMessage())
          .build()
      );
    }
  }

  @PostMapping("/verify")
  public ResponseEntity<VerificationResponse> verifyAge(@RequestBody VerificationRequest request) {
    try {
      System.out.println("Received verification request: " + request.toString());
      VerificationResponse response = verificationServiceV2.verifyAge(
        request.getSessionId(),
        request.getCredentialAsJson()
      );
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(
        VerificationResponse.builder()
          .success(false)
          .message("Verification failed: " + e.getMessage())
          .build()
      );
    }
  }
}
