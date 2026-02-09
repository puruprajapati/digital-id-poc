# Google Wallet Credential Verifier - Spring Boot

This is a Java Spring Boot conversion of the original Python Flask application for verifying digital wallet credentials using OpenID4VP protocols.

## Overview

This application processes and verifies identity tokens from Google Wallet using the OpenID4VP (OpenID for Verifiable Presentations) protocol.

## Features

- **Protocol Support**: OpenID4VP v1 (both unsigned and signed variants)
- **Credential Types**: mDL (Mobile Driving License) and Google Wallet ID
- **Zero-Knowledge Proofs**: Support for ZKP credential verification
- **JWE Encryption**: ECDH-ES with A128GCM for secure response encryption
- **CORS Support**: Cross-Origin Resource Sharing enabled
- **RESTful API**: Clean REST endpoints for request creation and verification

## Technology Stack

- **Java 17**
- **Spring Boot 3.2.1**
- **Nimbus JOSE + JWT**: For JWE/JWK handling
- **CBOR**: For binary encoding
- **Thymeleaf**: For HTML templating
- **Lombok**: For reducing boilerplate code
- **Maven**: For dependency management

## Project Structure

```
src/
├── main/
│   ├── java/com/google/wallet/verifier/
│   │   ├── CredentialVerifierApplication.java  # Main application class
│   │   ├── config/
│   │   │   ├── AppConfig.java                  # Application configuration
│   │   │   ├── CorsConfig.java                 # CORS configuration
│   │   │   ├── JweConfig.java                  # JWE configuration
│   │   │   └── ZkConfig.java                   # ZK verifier configuration
│   │   ├── controller/
│   │   │   ├── CredentialController.java       # REST API endpoints
│   │   │   └── HomeController.java             # Home page controller
│   │   ├── dto/
│   │   │   └── CredentialDto.java              # Data Transfer Objects
│   │   ├── service/
│   │   │   ├── RequestGeneratorService.java    # Request generation logic
│   │   │   └── VerificationService.java        # Verification logic
│   │   └── util/
│   │       └── CryptoUtil.java                 # Cryptographic utilities
│   └── resources/
│       ├── application.properties              # Configuration properties
│       └── templates/
│           └── RP_web.html                     # Web UI template
└── test/                                       # Test files (to be added)
```

## Configuration

Edit `src/main/resources/application.properties` to configure:

- **Server Port**: Default is 5001
- **App Package Name**: Android application package name
- **Android Signature Hash**: SHA-256 hash of your Android app's signing certificate
- **ZK Verifier URLs**: URLs for external Zero-Knowledge Verifier service

### Finding Android Signature Hash

```bash
keytool -printcert -jarfile your_app.apk | grep SHA256 | awk '{print $2}' | xxd -r -p | sha256sum | awk '{print $1}'
```

## Building and Running

### Prerequisites

- Java 17 or higher
- Maven 3.6 or higher

### Build

```bash
mvn clean package
```

### Run

```bash
mvn spring-boot:run
```

Or run the JAR directly:

```bash
java -jar target/credential-verifier-1.0.0.jar
```

The application will start on `http://localhost:5001`

## API Endpoints

### POST /request

Creates a credential request.

**Request Body:**
```json
{
  "protocol": "openid4vp-v1-unsigned",
  "doctypes": ["org.iso.18013.5.1.mDL"],
  "requested_fields": [
    {"namespace": "org.iso.18013.5.1", "name": "family_name"},
    {"namespace": "org.iso.18013.5.1", "name": "given_name"}
  ],
  "is_zkp_request": false
}
```

**Response:**
```json
{
  "success": true,
  "request": "base64_encoded_request",
  "state": {
    "nonce_base64": "...",
    "jwe_private_key_jwk": "..."
  }
}
```

### POST /verify

Verifies a credential response.

**Request Body:**
```json
{
  "protocol": "openid4vp-v1-unsigned",
  "data": {
    "response": "jwe_encrypted_string"
  },
  "state": {
    "nonce_base64": "...",
    "jwe_private_key_jwk": "..."
  },
  "origin": "https://example.com"
}
```

**Response:**
```json
{
  "success": true,
  "credential_data": [
    {"name": "family_name", "value": "Smith"},
    {"name": "given_name", "value": "John"}
  ]
}
```

### POST /zkverify

Verifies a zero-knowledge proof credential response.

### GET /

Serves the main web UI.

## Migration Notes from Python

### Key Differences

1. **Framework**: Flask → Spring Boot
    - Routes are now Controller methods with annotations
    - Template engine: Jinja2 → Thymeleaf

2. **Dependency Management**: pip → Maven
    - Dependencies are in `pom.xml` instead of `requirements.txt`

3. **Cryptography Libraries**:
    - `jwcrypto` → Nimbus JOSE + JWT
    - `cbor2` → CBOR for Java

4. **Configuration**:
    - Python environment variables → `application.properties`
    - Type-safe configuration with `@ConfigurationProperties`

5. **State Management**:
    - In-memory state storage (development only)
    - **Production**: Use Redis, database, or distributed cache

### Missing Features

The following features from the Python version need additional implementation:

1. **mdoc/mDL Verification**: The `isomdoc` library equivalent needs to be integrated
    - Current implementation has placeholder verification logic
    - Consider using a Java ISO 18013-5 library

2. **CBOR Encoding**: Full CBOR support for SessionTranscript construction
    - Currently simplified for demonstration

3. **Complete ZK Integration**: Full integration with external ZK verifier service

## Production Considerations

⚠️ **This is development code - DO NOT use in production without:**

1. **Proper State Management**:
    - Replace in-memory state with Redis, database, or distributed cache
    - Never pass private keys in requests in production

2. **Security Hardening**:
    - Enable HTTPS/TLS
    - Implement proper authentication/authorization
    - Add rate limiting
    - Validate and sanitize all inputs

3. **Error Handling**:
    - Implement comprehensive error handling
    - Add proper logging and monitoring
    - Never expose internal errors to clients

4. **Performance**:
    - Add caching where appropriate
    - Optimize database queries
    - Consider async processing for long operations

5. **Testing**:
    - Add comprehensive unit tests
    - Add integration tests
    - Perform security testing

## Development

### Adding Dependencies

Add to `pom.xml`:
```xml
<dependency>
    <groupId>...</groupId>
    <artifactId>...</artifactId>
    <version>...</version>
</dependency>
```

### Hot Reload

Spring Boot DevTools is included for automatic restart during development.

## License

Copyright 2025 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

## Support

For issues or questions, please refer to the original Google Wallet documentation.