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

package com.google.wallet.verifier.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.wallet.verifier.config.AppConfig;
import com.google.wallet.verifier.config.ZkConfig;
import com.google.wallet.verifier.dto.CredentialDto;
import com.google.wallet.verifier.util.CryptoUtil;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jose.jwk.ECKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Service for verifying credentials from the wallet.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VerificationService {

  private final AppConfig appConfig;
  private final ZkConfig zkConfig;
  private final ObjectMapper objectMapper;
  private final RestTemplate restTemplate = new RestTemplate();

  /**
   * Processes OpenID4VP response.
   *
   * @param responseData The encrypted response string
   * @param state The request state
   * @param origin The origin of the request
   * @param isSignedRequest Whether the request was signed
   * @return List of verified credential attributes
   */
  public List<CredentialDto.CredentialAttribute> processOpenid4vpResponse(
    String responseData,
    Map<String, Object> state,
    String origin,
    boolean isSignedRequest) {

    try {
      log.info("Processing OpenID4VP response");
      log.debug("Response data (first 100 chars): {}", responseData.substring(0, Math.min(100, responseData.length())));

      // Decrypt the JWE
      String jwePrivateKeyJwk = (String) state.get("jwe_private_key_jwk");
      if (jwePrivateKeyJwk == null) {
        log.error("Missing JWE private key in state");
        return null;
      }

      ECKey privateKey = ECKey.parse(jwePrivateKeyJwk);
      JWEObject jweObject = JWEObject.parse(responseData);

      ECDHDecrypter decrypter = new ECDHDecrypter(privateKey);
      jweObject.decrypt(decrypter);

      String decryptedPayload = jweObject.getPayload().toString();
      log.debug("Decrypted payload: {}", decryptedPayload);

      // Parse the decrypted payload
      Map<String, Object> payload = objectMapper.readValue(decryptedPayload, Map.class);

      // Construct SessionTranscript
      byte[] sessionTranscript = constructSessionTranscript(
        state.get("nonce_base64").toString(),
        origin,
        jwePrivateKeyJwk
      );

      // Verify the device response
      List<CredentialDto.CredentialAttribute> attributes = verifyDeviceResponse(
        payload,
        sessionTranscript,
        isSignedRequest
      );

      log.info("Successfully processed OpenID4VP response with {} attributes",
        attributes != null ? attributes.size() : 0);

      return attributes;

    } catch (Exception e) {
      log.error("Error processing OpenID4VP response", e);
      return null;
    }
  }

  /**
   * Processes OpenID4VP ZK (Zero-Knowledge) response.
   *
   * @param responseData The encrypted response string
   * @param state The request state
   * @param origin The origin of the request
   * @param isSignedRequest Whether the request was signed
   * @return Map containing verification status and claims
   */
  public Map<String, Object> processOpenid4vpZkResponse(
    String responseData,
    Map<String, Object> state,
    String origin,
    boolean isSignedRequest) {

    try {
      log.info("Processing OpenID4VP ZK response");

      // Decrypt the JWE
      String jwePrivateKeyJwk = (String) state.get("jwe_private_key_jwk");
      if (jwePrivateKeyJwk == null) {
        return createErrorResponse("Missing JWE private key in state");
      }

      ECKey privateKey = ECKey.parse(jwePrivateKeyJwk);
      JWEObject jweObject = JWEObject.parse(responseData);

      ECDHDecrypter decrypter = new ECDHDecrypter(privateKey);
      jweObject.decrypt(decrypter);

      String decryptedPayload = jweObject.getPayload().toString();
      log.debug("Decrypted ZK payload");

      // Parse and send to ZK verifier
      Map<String, Object> payload = objectMapper.readValue(decryptedPayload, Map.class);

      Map<String, Object> zkVerifyResult = sendToZkVerifier(payload);

      return zkVerifyResult;

    } catch (Exception e) {
      log.error("Error processing OpenID4VP ZK response", e);
      return createErrorResponse("ZK verification failed: " + e.getMessage());
    }
  }

  /**
   * Constructs the SessionTranscript for verification.
   *
   * @param nonceBase64 The base64-encoded nonce
   * @param origin The origin string
   * @param readerKeyJwk The reader's key in JWK format
   * @return The session transcript bytes
   */
  private byte[] constructSessionTranscript(String nonceBase64, String origin, String readerKeyJwk) {
    try {
      // Decode nonce
      byte[] nonce = CryptoUtil.decodeBase64Key(nonceBase64);

      // Build origin bytes based on origin type
      byte[] originBytes;
      if (origin.startsWith("https://") || origin.startsWith("http://")) {
        // Web origin
        originBytes = origin.getBytes(StandardCharsets.UTF_8);
      } else if (origin.startsWith("android:apk-key-hash:")) {
        // Android origin - use configured signature hash
        originBytes = CryptoUtil.decodeBase64Key(appConfig.getAndroidSignatureHash());
      } else {
        log.warn("Unknown origin type: {}", origin);
        originBytes = origin.getBytes(StandardCharsets.UTF_8);
      }

      // Combine nonce and origin (simplified - actual implementation would use CBOR)
      byte[] sessionTranscript = new byte[nonce.length + originBytes.length];
      System.arraycopy(nonce, 0, sessionTranscript, 0, nonce.length);
      System.arraycopy(originBytes, 0, sessionTranscript, nonce.length, originBytes.length);

      return sessionTranscript;

    } catch (Exception e) {
      log.error("Error constructing SessionTranscript", e);
      throw new RuntimeException("Failed to construct SessionTranscript", e);
    }
  }

  /**
   * Verifies the device response.
   * Note: This is a simplified version. Production code should use proper mdoc verification library.
   *
   * @param payload The decrypted payload
   * @param sessionTranscript The session transcript
   * @param isSignedRequest Whether the request was signed
   * @return List of verified attributes
   */
  private List<CredentialDto.CredentialAttribute> verifyDeviceResponse(
    Map<String, Object> payload,
    byte[] sessionTranscript,
    boolean isSignedRequest) {

    List<CredentialDto.CredentialAttribute> attributes = new ArrayList<>();

    try {
      // Extract vp_token from payload
      // vp_token is a map where keys are credential IDs (e.g., "1-request")
      Object vpTokenObj = payload.get("vp_token");
      if (vpTokenObj == null) {
        log.error("No vp_token in payload");
        return attributes;
      }

      log.info("Processing vp_token: {}", vpTokenObj.getClass().getName());

      // vp_token is a Map<String, List<String>>
      // Each key is a credential ID, each value is a list of base64-encoded mdoc data
      if (vpTokenObj instanceof Map) {
        Map<String, Object> vpTokenMap = (Map<String, Object>) vpTokenObj;

        for (Map.Entry<String, Object> entry : vpTokenMap.entrySet()) {
          String credentialId = entry.getKey();
          log.info("Processing credential: {}", credentialId);

          // The value is typically a list of base64-encoded mdoc strings
          if (entry.getValue() instanceof List) {
            List<String> mdocList = (List<String>) entry.getValue();

            for (String base64Mdoc : mdocList) {
              log.debug("Mdoc data (first 100 chars): {}",
                base64Mdoc.substring(0, Math.min(100, base64Mdoc.length())));

              // Decode and extract attributes from the mdoc
              try {
                // Use URL-safe decoder (handles - and _ characters)
                byte[] mdocBytes = java.util.Base64.getUrlDecoder().decode(base64Mdoc);
                log.info("Decoded mdoc size: {} bytes", mdocBytes.length);

                // Parse CBOR to extract attributes
                List<CredentialDto.CredentialAttribute> extractedAttrs = parseMdocAttributes(mdocBytes, credentialId);
                attributes.addAll(extractedAttrs);

              } catch (Exception e) {
                log.error("Error decoding mdoc", e);
              }
            }
          }
        }

        log.info("Successfully processed {} credentials", vpTokenMap.size());

      } else {
        log.error("vp_token is not a Map, it's: {}", vpTokenObj.getClass().getName());
      }

    } catch (Exception e) {
      log.error("Error verifying device response", e);
    }

    return attributes;
  }

  /**
   * Sends data to the ZK verifier service.
   *
   * @param payload The payload to verify
   * @return Verification result from ZK service
   */
  private Map<String, Object> sendToZkVerifier(Map<String, Object> payload) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);

      HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

      ResponseEntity<Map> response = restTemplate.postForEntity(
        zkConfig.getVerifier().getUrl(),
        request,
        Map.class
      );

      if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", true);
        result.put("verified_claims", response.getBody().get("claims"));
        return result;
      } else {
        return createErrorResponse("ZK verifier returned error status");
      }

    } catch (Exception e) {
      log.error("Error calling ZK verifier", e);
      return createErrorResponse("Failed to contact ZK verifier: " + e.getMessage());
    }
  }

  /**
   * Creates an error response map.
   *
   * @param message The error message
   * @return Error response map
   */
  private Map<String, Object> createErrorResponse(String message) {
    Map<String, Object> response = new HashMap<>();
    response.put("status", false);
    response.put("message", message);
    return response;
  }

  /**
   * Parses mdoc CBOR data to extract credential attributes.
   *
   * @param mdocBytes The CBOR-encoded mdoc bytes
   * @param credentialId The credential ID for reference
   * @return List of extracted attributes
   */
  private List<CredentialDto.CredentialAttribute> parseMdocAttributes(byte[] mdocBytes, String credentialId) {
    List<CredentialDto.CredentialAttribute> attributes = new ArrayList<>();

    try {
      // Parse CBOR
      com.upokecenter.cbor.CBORObject deviceResponse = com.upokecenter.cbor.CBORObject.DecodeFromBytes(mdocBytes);

      log.debug("DeviceResponse CBOR type: {}", deviceResponse.getType());

      // Navigate: DeviceResponse -> documents (array)
      com.upokecenter.cbor.CBORObject documents = deviceResponse.get("documents");
      if (documents == null || documents.getType() != com.upokecenter.cbor.CBORType.Array) {
        log.warn("No documents array found in DeviceResponse");
        return attributes;
      }

      log.info("Found {} document(s) in response", documents.size());

      // Process first document (typically there's only one)
      if (documents.size() > 0) {
        com.upokecenter.cbor.CBORObject document = documents.get(0);

        // Extract docType
        com.upokecenter.cbor.CBORObject docType = document.get("docType");
        if (docType != null) {
          log.info("Document type: {}", docType.AsString());
        }

        // Navigate: document -> issuerSigned -> nameSpaces
        com.upokecenter.cbor.CBORObject issuerSigned = document.get("issuerSigned");
        if (issuerSigned == null) {
          log.warn("No issuerSigned found in document");
          return attributes;
        }

        com.upokecenter.cbor.CBORObject nameSpaces = issuerSigned.get("nameSpaces");
        if (nameSpaces == null) {
          log.warn("No nameSpaces found in issuerSigned");
          return attributes;
        }

        log.debug("NameSpaces type: {}", nameSpaces.getType());

        // Iterate through all namespaces
        for (com.upokecenter.cbor.CBORObject key : nameSpaces.getKeys()) {
          String namespace = key.AsString();
          log.info("Processing namespace: {}", namespace);

          com.upokecenter.cbor.CBORObject items = nameSpaces.get(key);

          // Handle CBOR tag 24 (encoded CBOR data)
          if (items.isTagged() && items.getMostOuterTag().ToInt32Checked() == 24) {
            log.debug("Unwrapping CBOR tag 24");
            byte[] innerBytes = items.GetByteString();
            items = com.upokecenter.cbor.CBORObject.DecodeFromBytes(innerBytes);
          }

          log.debug("Items type after unwrapping: {}", items.getType());

          if (items.getType() != com.upokecenter.cbor.CBORType.Array) {
            log.warn("Namespace {} does not contain an array, type is: {}", namespace, items.getType());
            continue;
          }

          log.info("Found {} items in namespace {}", items.size(), namespace);

          // Each item in the array is an IssuerSignedItem
          for (int i = 0; i < items.size(); i++) {
            com.upokecenter.cbor.CBORObject item = items.get(i);

            // Each item might also be wrapped in CBOR tag 24
            if (item.isTagged() && item.getMostOuterTag().ToInt32Checked() == 24) {
              log.debug("Unwrapping CBOR tag 24 for item {}", i);
              byte[] innerBytes = item.GetByteString();
              item = com.upokecenter.cbor.CBORObject.DecodeFromBytes(innerBytes);
            }

            log.debug("Item {} type: {}", i, item.getType());

            // Extract elementIdentifier (field name)
            com.upokecenter.cbor.CBORObject elementIdObj = item.get("elementIdentifier");
            if (elementIdObj == null) {
              log.warn("Item {} in namespace {} has no elementIdentifier", i, namespace);
              continue;
            }

            String elementId = elementIdObj.AsString();

            // Extract elementValue (field value)
            com.upokecenter.cbor.CBORObject elementValueObj = item.get("elementValue");
            if (elementValueObj == null) {
              log.warn("Item {} ({}) in namespace {} has no elementValue", i, elementId, namespace);
              continue;
            }

            Object elementValue = convertCBORToJavaObject(elementValueObj);

            log.info("Extracted attribute: {} = {}", elementId, elementValue);

            // Add to attributes list
            attributes.add(CredentialDto.CredentialAttribute.builder()
              .name(elementId)
              .value(elementValue)
              .build());
          }
        }
      }

      log.info("Successfully extracted {} attributes from mdoc", attributes.size());

    } catch (Exception e) {
      log.error("Error parsing mdoc CBOR", e);
      // Add error indicator
      attributes.add(CredentialDto.CredentialAttribute.builder()
        .name("error")
        .value("Failed to parse mdoc: " + e.getMessage())
        .build());
    }

    return attributes;
  }

  /**
   * Converts a CBOR object to a Java object.
   *
   * @param cbor The CBOR object
   * @return Java object representation
   */
  private Object convertCBORToJavaObject(com.upokecenter.cbor.CBORObject cbor) {
    if (cbor == null) {
      return null;
    }

    switch (cbor.getType()) {
      case TextString:
        return cbor.AsString();

      case Integer:
        // Try to get as int first, fallback to long
        try {
          return cbor.AsInt32();
        } catch (Exception e) {
          return cbor.AsInt64();
        }

      case Boolean:
        return cbor.AsBoolean();

      case ByteString:
        // Return as base64 string for readability
        byte[] bytes = cbor.GetByteString();
        return java.util.Base64.getEncoder().encodeToString(bytes);

      case Array:
        // Convert array to list
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < cbor.size(); i++) {
          list.add(convertCBORToJavaObject(cbor.get(i)));
        }
        return list;

      case Map:
        // Convert map to Java map
        Map<String, Object> map = new HashMap<>();
        for (com.upokecenter.cbor.CBORObject key : cbor.getKeys()) {
          String keyStr = key.AsString();
          map.put(keyStr, convertCBORToJavaObject(cbor.get(key)));
        }
        return map;

      case FloatingPoint:
        return cbor.AsDouble();

      case SimpleValue:
        // Handle null and undefined
        if (cbor.isNull()) {
          return null;
        }
        return cbor.toString();

      default:
        // For unknown types, return string representation
        return cbor.toString();
    }
  }
}