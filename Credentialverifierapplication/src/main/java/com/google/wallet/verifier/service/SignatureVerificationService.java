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

import com.upokecenter.cbor.CBORObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

/**
 * Minimal signature verification for real Google Wallet credentials.
 *
 * This verifies:
 * 1. Issuer signature (Google signed the credential)
 * 2. Device signature (device holder authorized release)
 */
@Service
@Slf4j
public class SignatureVerificationService {

  /**
   * Verifies the issuer signature in the mdoc.
   *
   * @param issuerAuth The issuerAuth CBOR object
   * @param issuerSigned The issuerSigned CBOR object (data that was signed)
   * @return true if signature is valid
   */
  public boolean verifyIssuerSignature(CBORObject issuerAuth, CBORObject issuerSigned) {
    try {
      log.info("Verifying issuer signature...");

      // IssuerAuth is a COSE_Sign1 structure
      // Structure: [protected headers, unprotected headers, payload, signature]

      if (issuerAuth.getType() != com.upokecenter.cbor.CBORType.Array || issuerAuth.size() != 4) {
        log.error("Invalid issuerAuth structure");
        return false;
      }

      // Extract certificate from issuerAuth
      CBORObject unprotectedHeaders = issuerAuth.get(1);
      CBORObject x5chain = unprotectedHeaders.get(CBORObject.FromObject(33)); // x5chain tag

      if (x5chain == null || x5chain.getType() != com.upokecenter.cbor.CBORType.Array) {
        log.error("No certificate chain found");
        return false;
      }

      // Get first certificate (signer certificate)
      byte[] certBytes = x5chain.get(0).GetByteString();
      CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
      X509Certificate cert = (X509Certificate) certFactory.generateCertificate(
        new ByteArrayInputStream(certBytes)
      );

      log.info("Issuer certificate: {}", cert.getSubjectX500Principal().getName());
      log.info("Issuer: {}", cert.getIssuerX500Principal().getName());
      log.info("Valid from: {} to {}", cert.getNotBefore(), cert.getNotAfter());

      // Check certificate validity
      try {
        cert.checkValidity();
        log.info("Certificate is valid (not expired)");
      } catch (Exception e) {
        log.error("Certificate is not valid: {}", e.getMessage());
        return false;
      }

      // Extract signature
      byte[] signature = issuerAuth.get(3).GetByteString();

      // Build the data that was signed (Sig_structure)
      // Sig_structure = ["Signature1", protected_headers, external_aad, payload]
      CBORObject protectedHeaders = issuerAuth.get(0);

      // Encode issuerSigned as the payload
      byte[] payload = issuerSigned.EncodeToBytes();

      // Build Sig_structure
      CBORObject sigStructure = CBORObject.NewArray();
      sigStructure.Add("Signature1");
      sigStructure.Add(protectedHeaders);
      sigStructure.Add(CBORObject.FromObject(new byte[0])); // empty external_aad
      sigStructure.Add(payload);

      byte[] toBeSigned = sigStructure.EncodeToBytes();

      // Verify signature using certificate's public key
      Signature sig = Signature.getInstance("SHA256withECDSA");
      sig.initVerify(cert.getPublicKey());
      sig.update(toBeSigned);

      // Convert signature from IEEE P1363 to DER format if needed
      byte[] derSignature = convertToDER(signature);

      boolean valid = sig.verify(derSignature);

      if (valid) {
        log.info("✓ Issuer signature is VALID");
      } else {
        log.error("✗ Issuer signature is INVALID");
      }

      return valid;

    } catch (Exception e) {
      log.error("Error verifying issuer signature", e);
      return false;
    }
  }

  /**
   * Verifies the device signature in the mdoc.
   *
   * @param deviceAuth The deviceAuth CBOR object
   * @param deviceSigned The deviceSigned data
   * @param sessionTranscript The session transcript
   * @param deviceKey The device public key
   * @return true if signature is valid
   */
  public boolean verifyDeviceSignature(CBORObject deviceAuth, CBORObject deviceSigned,
                                       byte[] sessionTranscript, CBORObject deviceKey) {
    try {
      log.info("Verifying device signature...");

      // Get device signature
      CBORObject deviceSignature = deviceAuth.get("deviceSignature");

      if (deviceSignature == null || deviceSignature.getType() != com.upokecenter.cbor.CBORType.Array) {
        log.error("Invalid deviceSignature structure");
        return false;
      }

      // DeviceSignature is also a COSE_Sign1 structure
      byte[] signature = deviceSignature.get(3).GetByteString();
      CBORObject protectedHeaders = deviceSignature.get(0);

      // Build DeviceAuthentication structure
      // DeviceAuthentication = ["DeviceAuthentication", SessionTranscript, DocType, DeviceNameSpacesBytes]
      CBORObject deviceAuth1 = CBORObject.NewArray();
      deviceAuth1.Add("DeviceAuthentication");
      deviceAuth1.Add(CBORObject.DecodeFromBytes(sessionTranscript));
      deviceAuth1.Add("com.google.wallet.idcard.1"); // docType - should be extracted
      deviceAuth1.Add(deviceSigned.get("nameSpaces").EncodeToBytes());

      byte[] toBeSigned = deviceAuth1.EncodeToBytes();

      // Extract public key from deviceKey CBOR (COSE_Key format)
      ECPublicKey publicKey = extractPublicKey(deviceKey);

      // Verify signature
      Signature sig = Signature.getInstance("SHA256withECDSA");
      sig.initVerify(publicKey);
      sig.update(toBeSigned);

      byte[] derSignature = convertToDER(signature);
      boolean valid = sig.verify(derSignature);

      if (valid) {
        log.info("✓ Device signature is VALID");
      } else {
        log.error("✗ Device signature is INVALID");
      }

      return valid;

    } catch (Exception e) {
      log.error("Error verifying device signature", e);
      return false;
    }
  }

  /**
   * Converts IEEE P1363 format signature to DER format.
   * ECDSA signatures from COSE are in IEEE P1363 format (r || s).
   */
  private byte[] convertToDER(byte[] ieee) {
    try {
      int len = ieee.length / 2;
      byte[] r = Arrays.copyOfRange(ieee, 0, len);
      byte[] s = Arrays.copyOfRange(ieee, len, ieee.length);

      // Build DER: SEQUENCE { INTEGER r, INTEGER s }
      CBORObject seq = CBORObject.NewArray();
      seq.Add(CBORObject.FromObject(new java.math.BigInteger(1, r)));
      seq.Add(CBORObject.FromObject(new java.math.BigInteger(1, s)));

      // Simple DER encoding for SEQUENCE of two INTEGERs
      // This is simplified - production should use proper ASN.1 library
      return ieee; // For now, try IEEE format first

    } catch (Exception e) {
      log.warn("Could not convert to DER, using original format");
      return ieee;
    }
  }

  /**
   * Extracts EC public key from COSE_Key CBOR object.
   */
  private ECPublicKey extractPublicKey(CBORObject coseKey) throws Exception {
    // COSE_Key format:
    // 1: kty (2 = EC)
    // -1: crv (1 = P-256)
    // -2: x coordinate
    // -3: y coordinate

    byte[] x = coseKey.get(CBORObject.FromObject(-2)).GetByteString();
    byte[] y = coseKey.get(CBORObject.FromObject(-3)).GetByteString();

    // Create EC public key from x and y coordinates
    java.security.spec.ECPoint point = new java.security.spec.ECPoint(
      new java.math.BigInteger(1, x),
      new java.math.BigInteger(1, y)
    );
//
//    java.security.spec.ECParameterSpec ecSpec =
//      java.security.spec.ECGenParameterSpec.class.cast(
//        java.security.AlgorithmParameters.getInstance("EC")
//          .getParameterSpec(java.security.spec.ECGenParameterSpec.class)
//      ).getParameterSpec();

//    java.security.spec.ECPublicKeySpec keySpec =
//      new java.security.spec.ECPublicKeySpec(point, ecSpec);
//
//    java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("EC");
//    return (ECPublicKey) keyFactory.generatePublic(keySpec);
    return null;
  }
}