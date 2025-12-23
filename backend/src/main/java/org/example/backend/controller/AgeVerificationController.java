package org.example.backend.controller;

import org.example.backend.dto.VerificationRequest;
import org.example.backend.dto.VerificationResponse;
import org.example.backend.service.AgeVerificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.UUID;

@RestController
@RequestMapping("/api/verification")
public class AgeVerificationController {

  @Autowired
  private AgeVerificationService verificationService;

  @Value("${app.base-url:http://localhost:8080}")
  private String baseUrl;

  @PostMapping("/initiate")
  public ResponseEntity<VerificationResponse> initiateVerification(@RequestBody VerificationRequest request) {
    try {
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
      VerificationResponse response = verificationService.verifyAge(
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

  @GetMapping("/status/{sessionId}")
  public ResponseEntity<VerificationResponse> getVerificationStatus(@PathVariable String sessionId) {
    try {
      VerificationResponse response = verificationService.getVerificationStatus(sessionId);
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      return ResponseEntity.notFound().build();
    }
  }


  // Callback endpoint for Google Wallet redirect
  @GetMapping("/callback")
  public RedirectView handleCallback(
    @RequestParam(required = false) String session_id,
    @RequestParam(required = false) String id_token,
    @RequestParam(required = false) String error,
    @RequestParam(required = false) String error_description) {

    if (error != null) {
      return new RedirectView(baseUrl + "/verify?error=" + error + "&session=" + session_id);
    }

    if (id_token != null && session_id != null) {
      // Store the token for the session
      verificationService.storeCallbackToken(session_id, id_token);
      return new RedirectView(baseUrl + "/verify?success=true&session=" + session_id);
    }

    return new RedirectView(baseUrl + "/verify?error=invalid_callback");
  }

  // Endpoint to retrieve the token after callback
  @GetMapping("/token/{sessionId}")
  public ResponseEntity<VerificationResponse> getToken(@PathVariable String sessionId) {
    try {
      String token = verificationService.getStoredToken(sessionId);
      if (token != null) {
        return ResponseEntity.ok(VerificationResponse.builder()
          .success(true)
          .sessionId(sessionId)
          .credential(token)
          .message("Token retrieved")
          .build());
      }
      return ResponseEntity.ok(VerificationResponse.builder()
        .success(false)
        .message("Token not yet received")
        .build());
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(
        VerificationResponse.builder()
          .success(false)
          .message("Failed to retrieve token: " + e.getMessage())
          .build()
      );
    }
  }
}