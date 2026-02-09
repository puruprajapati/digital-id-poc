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

package com.google.wallet.verifier.util;

import lombok.Data;
import org.apache.commons.codec.binary.Base64;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Utility class for cryptographic operations.
 */
public class CryptoUtil {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  /**
   * Generates a cryptographically secure random nonce.
   *
   * @param lengthBytes The desired length of the nonce in bytes
   * @return A NonceResult containing both raw and base64-encoded nonce
   */
  public static NonceResult generateSecureNonce(int lengthBytes) {
    byte[] rawNonce = new byte[lengthBytes];
    SECURE_RANDOM.nextBytes(rawNonce);

    // Encode and remove potential '=' padding for compatibility
    String base64NonceUnpadded = encodeKeyBase64(rawNonce);

    return new NonceResult(rawNonce, base64NonceUnpadded);
  }

  /**
   * Encodes key bytes into URL-safe base64 string without padding.
   *
   * @param keyBytes The bytes to encode
   * @return Base64 encoded string without padding
   */
  public static String encodeKeyBase64(byte[] keyBytes) {
    return Base64.encodeBase64URLSafeString(keyBytes).replace("=", "");
  }

  /**
   * Decodes a URL-safe base64 encoded key string, adding padding if necessary.
   *
   * @param keyBase64Unpadded The base64 string, potentially without padding
   * @return The decoded bytes
   */
  public static byte[] decodeBase64Key(String keyBase64Unpadded) {
    // Calculate and add the required padding for correct decoding
    int padding = 4 - (keyBase64Unpadded.length() % 4);
    String paddedKey = keyBase64Unpadded + "=".repeat(padding % 4);

    return Base64.decodeBase64(paddedKey);
  }

  /**
   * Computes SHA-256 hash of the input data.
   *
   * @param data The data to hash
   * @return The SHA-256 hash
   * @throws RuntimeException if SHA-256 algorithm is not available
   */
  public static byte[] sha256(byte[] data) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return digest.digest(data);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm not available", e);
    }
  }

  /**
   * Computes SHA-256 hash and returns it as a hex string.
   *
   * @param data The data to hash
   * @return The SHA-256 hash as hex string
   */
  public static String sha256Hex(byte[] data) {
    byte[] hash = sha256(data);
    StringBuilder hexString = new StringBuilder();
    for (byte b : hash) {
      String hex = Integer.toHexString(0xff & b);
      if (hex.length() == 1) hexString.append('0');
      hexString.append(hex);
    }
    return hexString.toString();
  }

  /**
   * Result object containing both raw and encoded nonce.
   */
  @Data
  public static class NonceResult {
    private final byte[] rawNonce;
    private final String base64NonceUnpadded;
  }
}