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
import com.google.wallet.verifier.config.JweConfig;
import com.google.wallet.verifier.dto.CredentialDto;
import com.google.wallet.verifier.util.CryptoUtil;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for generating credential requests.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RequestGeneratorService {

  private final JweConfig jweConfig;
  private final ObjectMapper objectMapper;

  /**
   * Generates the necessary state for initiating a credential request.
   *
   * @return A map containing the generated state
   */
  public Map<String, Object> generateRequestState() {
    // Generate Nonce
    CryptoUtil.NonceResult nonceResult = CryptoUtil.generateSecureNonce(32);
    log.debug("Generated Nonce (Base64, unpadded): {}", nonceResult.getBase64NonceUnpadded());

    Map<String, Object> state = new HashMap<>();
    state.put("nonce_base64", nonceResult.getBase64NonceUnpadded());

    return state;
  }

  /**
   * Generates an EC key pair for JWE encryption.
   *
   * @return A map containing public and private JWK
   * @throws Exception if key generation fails
   */
  public Map<String, ECKey> generateJweKeyPair() throws Exception {
    // Generate EC key pair (P-256 curve) with encryption parameters
    // Must match Python: kty='EC', crv='P-256', use='enc', kid='1', alg='ECDH-ES'
    ECKey ecKey = new ECKeyGenerator(Curve.P_256)
      .keyUse(com.nimbusds.jose.jwk.KeyUse.ENCRYPTION)  // use='enc'
      .keyID("1")  // kid='1'
      .algorithm(new com.nimbusds.jose.JWEAlgorithm(jweConfig.getAlgorithm()))  // alg='ECDH-ES'
      .generate();

    Map<String, ECKey> keyPair = new HashMap<>();
    keyPair.put("private", ecKey);
    keyPair.put("public", ecKey.toPublicJWK());

    log.debug("Generated JWE key pair with ID: 1, use: enc, alg: {}", jweConfig.getAlgorithm());

    return keyPair;
  }

  /**
   * Constructs the request dictionary for the OpenID4VP protocol.
   *
   * @param doctypes List of document types being requested
   * @param requestedFields List of requested fields
   * @param nonceBase64 The URL-safe base64 encoded nonce
   * @param jwePublicKey The reader's public JWK for response encryption
   * @param isZkpRequest Boolean for getting a ZKP
   * @param isSignedRequest Flag to indicate if the request is signed
   * @return A map representing the OpenID4VP request structure
   */
  public Map<String, Object> constructOpenid4vpRequest(
    List<String> doctypes,
    List<CredentialDto.RequestedField> requestedFields,
    String nonceBase64,
    ECKey jwePublicKey,
    boolean isZkpRequest,
    boolean isSignedRequest) {

    List<Map<String, Object>> credentialsList = new ArrayList<>();
    List<List<String>> credentialSetOptions = new ArrayList<>();

    // Build claims list
    List<Map<String, Object>> claimsList = new ArrayList<>();
    for (CredentialDto.RequestedField field : requestedFields) {
      Map<String, Object> claim = new HashMap<>();
      claim.put("path", Arrays.asList(field.getNamespace(), field.getName()));
      claim.put("intent_to_retain", false); // Set to true if saving the value
      claimsList.add(claim);
    }

    // Create a credential request for each doctype
    for (String doctype : doctypes) {
      // Generate a unique ID for each credential request
      String requestId = doctype.substring(doctype.lastIndexOf('.') + 1).toLowerCase() + "-request";

      Map<String, Object> meta = new HashMap<>();
      meta.put("doctype_value", doctype);

      String formatType = "mso_mdoc";

      if (isZkpRequest) {
        // TODO: Fetch ZK specs from external service
        // For now, using placeholder
        meta.put("zk_system_type", "zk_system_placeholder");
        meta.put("verifier_message", "challenge");
        formatType = "mso_mdoc_zk";
      }

      Map<String, Object> credentialRequest = new HashMap<>();
      credentialRequest.put("id", requestId);
      credentialRequest.put("format", formatType);
      credentialRequest.put("meta", meta);

      // Add claims if any were requested
      if (!claimsList.isEmpty()) {
        credentialRequest.put("claims", claimsList);
      }

      credentialsList.add(credentialRequest);

      // Each option is a list containing one request ID
      credentialSetOptions.add(Collections.singletonList(requestId));
    }

    // Define the credential query using DCQL (Digital Credential Query Language)
    Map<String, Object> dcqlQuery = new HashMap<>();
    dcqlQuery.put("credentials", credentialsList);

    List<Map<String, Object>> credentialSets = new ArrayList<>();
    Map<String, Object> credentialSet = new HashMap<>();
    credentialSet.put("options", credentialSetOptions);
    credentialSets.add(credentialSet);

    dcqlQuery.put("credential_sets", credentialSets);

    // Client metadata describing how the response should be encrypted
    Map<String, Object> clientMetadata = new HashMap<>();

    try {
      // Export public key as a map
      Map<String, Object> publicKeyMap = objectMapper.readValue(
        jwePublicKey.toPublicJWK().toJSONString(),
        Map.class
      );

      Map<String, Object> jwks = new HashMap<>();
      jwks.put("keys", Collections.singletonList(publicKeyMap));
      clientMetadata.put("jwks", jwks);
    } catch (Exception e) {
      log.error("Error creating JWKS", e);
    }

    // mdoc_crypto_capabilities specifies the cryptographic algorithms
    // supported by the verifier for the mdoc format
    // -7 corresponds to ES256 (ECDSA with P-256 and SHA-256)
    Map<String, Object> msoMdoc = new HashMap<>();
    msoMdoc.put("isserauth_alg_values", Collections.singletonList(-7));
    msoMdoc.put("deviceauth_alg_values", Collections.singletonList(-7));

    Map<String, Object> mdocCryptoCapabilities = new HashMap<>();
    mdocCryptoCapabilities.put("mso_mdoc", msoMdoc);

    clientMetadata.put("vp_formats_supported", mdocCryptoCapabilities);

    // Construct the main OpenID4VP request payload
    Map<String, Object> requestPayload = new HashMap<>();
    requestPayload.put("response_type", "vp_token");
    requestPayload.put("response_mode", "dc_api.jwt");  // Important: dc_api.jwt not direct_post.jwt
    requestPayload.put("nonce", nonceBase64);
    requestPayload.put("dcql_query", dcqlQuery);
    requestPayload.put("client_metadata", clientMetadata);

    log.debug("Constructed OpenID4VP request with {} credentials", credentialsList.size());

    return requestPayload;
  }
}