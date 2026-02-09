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

package com.google.wallet.verifier.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for JWE (JSON Web Encryption).
 */
@Configuration
@ConfigurationProperties(prefix = "jwe")
@Data
public class JweConfig {

  /**
   * Key Agreement Algorithm (e.g., ECDH-ES).
   */
  private String algorithm = "ECDH-ES";

  /**
   * Content Encryption Algorithm (e.g., A128GCM).
   */
  private String encryption = "A128GCM";
}