/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.wallet.verifier.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.wallet.verifier.dto.CredentialDto;
import com.google.wallet.verifier.service.RequestGeneratorService;
import com.google.wallet.verifier.service.VerificationService;
import com.nimbusds.jose.jwk.ECKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST Controller for credential request and verification endpoints.
 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class CredentialController {

  private final RequestGeneratorService requestGeneratorService;
  private final VerificationService verificationService;
  private final ObjectMapper objectMapper;

  private static final Set<String> SUPPORTED_PROTOCOLS = new HashSet<>(
    Arrays.asList("openid4vp-v1-unsigned", "openid4vp-v1-signed")
  );

  /**
   * Endpoint to create a credential request.
   *
   * POST /request
   * Body: {
   *   "protocol": "openid4vp-v1-unsigned" | "openid4vp-v1-signed",
   *   "doctypes": ["org.iso.18013.5.1.mDL", ...],
   *   "requested_fields": [{"namespace": "...", "name": "..."}, ...],
   *   "is_zkp_request": true/false
   * }
   */
  @PostMapping("/request")
  public ResponseEntity<?> createRequest(
    @RequestBody CredentialDto.CreateRequestDto requestDto) {

    try {
      log.info("================== REQUEST START ==================");
      log.info("Endpoint: POST /request");
      log.info("Received credential request");
      log.info("Request details:");
      log.info("  Protocol: {}", requestDto.getProtocol());
      log.info("  Doctypes: {}", requestDto.getDoctypes());
      log.info("  Requested fields count: {}",
        requestDto.getRequestedFields() != null ? requestDto.getRequestedFields().size() : 0);

      if (requestDto.getRequestedFields() != null) {
        for (int i = 0; i < requestDto.getRequestedFields().size(); i++) {
          CredentialDto.RequestedField field = requestDto.getRequestedFields().get(i);
          log.info("    Field {}: namespace='{}', name='{}'", i + 1, field.getNamespace(), field.getName());
        }
      }

      log.info("  Is ZKP request: {}", requestDto.isZkpRequest());

      // Validate protocol
      if (requestDto.getProtocol() == null || !SUPPORTED_PROTOCOLS.contains(requestDto.getProtocol())) {
        log.error("Invalid protocol: {}", requestDto.getProtocol());
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", "Unsupported protocol: " + requestDto.getProtocol());
        return ResponseEntity.badRequest().body(errorResponse);
      }

      // Validate required fields
      if (requestDto.getDoctypes() == null || requestDto.getDoctypes().isEmpty()) {
        log.error("Missing doctypes");
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", "Missing required field: doctypes");
        return ResponseEntity.badRequest().body(errorResponse);
      }

      boolean isSignedRequest = "openid4vp-v1-signed".equals(requestDto.getProtocol());

      // Generate request state
      Map<String, Object> state = requestGeneratorService.generateRequestState();

      // Generate JWE key pair
      Map<String, ECKey> keyPair = requestGeneratorService.generateJweKeyPair();
      ECKey privateKey = keyPair.get("private");
      ECKey publicKey = keyPair.get("public");

      // Store private key in state for later decryption
      state.put("jwe_private_key_jwk", privateKey.toJSONString());

      // Construct the request
      Map<String, Object> request = requestGeneratorService.constructOpenid4vpRequest(
        requestDto.getDoctypes(),
        requestDto.getRequestedFields() != null ? requestDto.getRequestedFields() : Collections.emptyList(),
        state.get("nonce_base64").toString(),
        publicKey,
        requestDto.isZkpRequest(),
        isSignedRequest
      );

      log.info("Successfully created credential request");

      // Build response with request as object and protocol
      Map<String, Object> responseBody = new HashMap<>();
      responseBody.put("success", true);
      responseBody.put("protocol", requestDto.getProtocol());
      responseBody.put("request", request);  // Return as JSON object, not base64 string
      responseBody.put("state", state);

      // Debug logging
      log.debug("Generated request structure:");
      log.debug("  - response_type: {}", request.get("response_type"));
      log.debug("  - response_mode: {}", request.get("response_mode"));
      log.debug("  - nonce length: {}", request.get("nonce").toString().length());

      if (request.containsKey("client_metadata")) {
        Map<String, Object> clientMeta = (Map<String, Object>) request.get("client_metadata");
        if (clientMeta.containsKey("jwks")) {
          Map<String, Object> jwks = (Map<String, Object>) clientMeta.get("jwks");
          if (jwks.containsKey("keys")) {
            List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");
            if (!keys.isEmpty()) {
              Map<String, Object> key = keys.get(0);
              log.debug("  - JWK kid: {}", key.get("kid"));
              log.debug("  - JWK use: {}", key.get("use"));
              log.debug("  - JWK alg: {}", key.get("alg"));
              log.debug("  - JWK kty: {}", key.get("kty"));
              log.debug("  - JWK crv: {}", key.get("crv"));
            }
          }
        }
      }

      log.info("Response:");
      log.info("  Success: true");
      log.info("  Protocol: {}", requestDto.getProtocol());
      log.info("  State contains: nonce_base64, jwe_private_key_jwk");
      log.info("================== REQUEST END ==================");

      return ResponseEntity.ok()
        .body(responseBody);

    } catch (Exception e) {
      log.error("Error creating credential request", e);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("success", false);
      errorResponse.put("error", "An unexpected error occurred while creating the request");
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
  }

  /**
   * Endpoint to verify a credential response.
   *
   * POST /verify
   * Body: {
   *   "protocol": "openid4vp-v1-unsigned" | "openid4vp-v1-signed",
   *   "data": { "response": "jwe_string" },
   *   "state": { ... state from /request ... },
   *   "origin": "https://..." | "android:apk..."
   * }
   */
  @PostMapping("/verify")
  public ResponseEntity<CredentialDto.VerifyResponse> verifyCredential(
    @RequestBody Map<String, Object> requestBody) {

    try {
      log.info("================== VERIFY START ==================");
      log.info("Endpoint: POST /verify");
      log.info("Received verification request");
      log.info("Request body keys: {}", requestBody.keySet());

      // Extract required fields
      String protocol = (String) requestBody.get("protocol");
      Map<String, Object> state = (Map<String, Object>) requestBody.get("state");
      String origin = (String) requestBody.get("origin");
      Map<String, Object> data = (Map<String, Object>) requestBody.get("data");

      log.info("Request details:");
      log.info("  Protocol: {}", protocol);
      log.info("  Origin: {}", origin);
      log.info("  State keys: {}", state != null ? state.keySet() : "null");
      log.info("  Data keys: {}", data != null ? data.keySet() : "null");

      // Input validation
      if (protocol == null || state == null) {
        return ResponseEntity.badRequest().body(
          CredentialDto.VerifyResponse.builder()
            .success(false)
            .error("Missing required fields: protocol, state")
            .build()
        );
      }

      if (!SUPPORTED_PROTOCOLS.contains(protocol)) {
        return ResponseEntity.badRequest().body(
          CredentialDto.VerifyResponse.builder()
            .success(false)
            .error("Unsupported protocol: " + protocol)
            .build()
        );
      }

      if (origin == null || data == null || !data.containsKey("response")) {
        return ResponseEntity.badRequest().body(
          CredentialDto.VerifyResponse.builder()
            .success(false)
            .error("Missing required fields: origin and data.response")
            .build()
        );
      }

      boolean isSignedRequest = "openid4vp-v1-signed".equals(protocol);
      String responseData = (String) data.get("response");

      log.info("Processing {} response", protocol);
      log.debug("Response data (first 100 chars): {}",
        responseData.substring(0, Math.min(100, responseData.length())));

      // Process the response
      List<CredentialDto.CredentialAttribute> extractedData =
        verificationService.processOpenid4vpResponse(
          responseData,
          state,
          origin,
          isSignedRequest
        );

      if (extractedData != null) {
        log.info("Verification successful");
        log.info("Response:");
        log.info("  Success: true");
        log.info("  Attributes extracted: {}", extractedData.size());

        for (int i = 0; i < extractedData.size(); i++) {
          CredentialDto.CredentialAttribute attr = extractedData.get(i);
          String valueStr = attr.getValue() != null ? attr.getValue().toString() : "null";
          // Truncate long values (like base64 images)
          if (valueStr.length() > 100) {
            valueStr = valueStr.substring(0, 100) + "... (truncated)";
          }
          log.info("    Attribute {}: {} = {}", i + 1, attr.getName(), valueStr);
        }

        log.info("================== VERIFY END ==================");

        return ResponseEntity.ok(
          CredentialDto.VerifyResponse.builder()
            .success(true)
            .credentialData(extractedData)
            .build()
        );
      } else {
        log.warn("Verification failed - extracted data is null");
        log.info("================== VERIFY END (FAILED) ==================");

        return ResponseEntity.badRequest().body(
          CredentialDto.VerifyResponse.builder()
            .success(false)
            .error("Token processing or verification failed")
            .build()
        );
      }

    } catch (Exception e) {
      log.error("Error during verification", e);
      log.error("Stack trace:", e);
      log.info("================== VERIFY END (ERROR) ==================");

      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
        CredentialDto.VerifyResponse.builder()
          .success(false)
          .error("An unexpected error occurred during verification")
          .build()
      );
    }
  }

  /**
   * Endpoint to verify a ZK credential response.
   *
   * POST /zkverify
   */
  @PostMapping("/zkverify")
  public ResponseEntity<Map<String, Object>> verifyZkCredential(
    @RequestBody Map<String, Object> requestBody) {

    try {
      log.info("Received ZK verification request");

      String protocol = (String) requestBody.get("protocol");
      Map<String, Object> state = (Map<String, Object>) requestBody.get("state");
      String origin = (String) requestBody.get("origin");
      Map<String, Object> data = (Map<String, Object>) requestBody.get("data");

      // Validation
      if (protocol == null || state == null) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", "Missing required fields: protocol, state");
        return ResponseEntity.badRequest().body(errorResponse);
      }

      if (!SUPPORTED_PROTOCOLS.contains(protocol)) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", "Unsupported protocol: " + protocol);
        return ResponseEntity.badRequest().body(errorResponse);
      }

      boolean isSignedRequest = "openid4vp-v1-signed".equals(protocol);
      String responseData = (String) data.get("response");

      Map<String, Object> result = verificationService.processOpenid4vpZkResponse(
        responseData,
        state,
        origin,
        isSignedRequest
      );

      if (result != null && (Boolean) result.get("status")) {
        Map<String, Object> successResponse = new HashMap<>();
        successResponse.put("success", true);
        successResponse.put("credential_data", result.get("verified_claims"));
        return ResponseEntity.ok(successResponse);
      } else {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", result != null ? result.get("message") : "Verification failed");
        return ResponseEntity.badRequest().body(errorResponse);
      }

    } catch (Exception e) {
      log.error("Error during ZK verification", e);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("success", false);
      errorResponse.put("error", "An unexpected error occurred during verification");
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
  }

  /**
   * Handle GET requests to POST-only endpoints.
   */
  @GetMapping({"/request", "/verify"})
  public ResponseEntity<Map<String, String>> handleGetRequests() {
    Map<String, String> error = new HashMap<>();
    error.put("error", "Method Not Allowed. Please use POST.");
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(error);
  }
}