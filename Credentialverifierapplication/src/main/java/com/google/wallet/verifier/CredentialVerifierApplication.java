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

package com.google.wallet.verifier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot application class for the Wallet Credential Verifier.
 *
 * This application processes and verifies identity tokens from Google Wallet
 * using the OpenID4VP protocols.
 */
@SpringBootApplication
public class CredentialVerifierApplication {

  public static void main(String[] args) {
    System.out.println("Starting Credential Verifier Application...");
    System.out.println("WARNING: Development mode - do not use in production");
    SpringApplication.run(CredentialVerifierApplication.class, args);
  }
}