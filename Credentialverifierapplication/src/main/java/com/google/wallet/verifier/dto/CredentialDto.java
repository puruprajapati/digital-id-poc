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

package com.google.wallet.verifier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Data Transfer Objects for API requests and responses.
 */
public class CredentialDto {

  /**
   * Request DTO for creating a credential request.
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CreateRequestDto {
    private String protocol;

    // Frontend sends "doctype" (singular)
    @JsonProperty("doctype")
    private List<String> doctypes;

    // Frontend sends "attributes" not "requested_fields"
    @JsonProperty("attributes")
    private List<RequestedField> requestedFields;

    // Frontend sends "requestZkp" not "is_zkp_request"
    @JsonProperty("requestZkp")
    private boolean isZkpRequest;
  }

  /**
   * Represents a requested field in the credential.
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RequestedField {
    private String namespace;
    private String name;
  }

  /**
   * Response DTO for credential request creation.
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CreateRequestResponse {
    private boolean success;
    private String request;
    private Map<String, Object> state;
    private String error;
  }

  /**
   * Request DTO for verifying credentials.
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class VerifyRequestDto {
    private String protocol;
    private Map<String, Object> data;
    private Map<String, Object> state;
    private String origin;
  }

  /**
   * Response DTO for credential verification.
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class VerifyResponse {
    private boolean success;

    @JsonProperty("credential_data")
    private List<CredentialAttribute> credentialData;

    private String error;
  }

  /**
   * Represents a credential attribute.
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CredentialAttribute {
    private String name;
    private Object value;
  }

  /**
   * Request state object.
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RequestState {
    @JsonProperty("nonce_base64")
    private String nonceBase64;

    @JsonProperty("jwe_private_key_jwk")
    private String jwePrivateKeyJwk;
  }
}