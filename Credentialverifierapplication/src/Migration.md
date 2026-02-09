# Python Flask to Java Spring Boot Migration Guide

## Overview

This document details the conversion of the Google Wallet Credential Verifier from Python Flask to Java Spring Boot.

## Architecture Comparison

### Python Flask Architecture
```
main-v1.py (monolithic)
├── Route handlers (@app.route)
├── Helper functions
├── Configuration constants
└── Template rendering
```

### Java Spring Boot Architecture
```
Layered Architecture
├── Controller Layer (REST endpoints)
├── Service Layer (business logic)
├── DTO Layer (data transfer objects)
├── Config Layer (configuration)
└── Util Layer (utilities)
```

## Component Mapping

| Python Component | Java Component | Notes |
|-----------------|----------------|-------|
| `app = Flask(__name__)` | `@SpringBootApplication` | Application initialization |
| `@app.route('/path')` | `@GetMapping/PostMapping` | Route definition |
| `request.get_json()` | `@RequestBody` annotation | Request parsing |
| `jsonify({...})` | `ResponseEntity<T>` | Response building |
| `render_template()` | `return "template_name"` | Template rendering |
| Global constants | `@ConfigurationProperties` | Configuration |
| Helper functions | Service classes | Business logic |

## Key Libraries Mapping

### Cryptography
- **Python**: `jwcrypto` → **Java**: Nimbus JOSE + JWT
- **Python**: `hashlib` → **Java**: `MessageDigest`
- **Python**: `os.urandom()` → **Java**: `SecureRandom`
- **Python**: `base64` → **Java**: Apache Commons Codec

### Data Handling
- **Python**: `json` → **Java**: Jackson ObjectMapper
- **Python**: `cbor2` → **Java**: CBOR for Java
- **Python**: `requests` → **Java**: `RestTemplate`

### Web Framework
- **Python**: Flask routes → **Java**: Spring MVC Controllers
- **Python**: Jinja2 templates → **Java**: Thymeleaf
- **Python**: `flask_cors` → **Java**: `CorsConfig`

## Code Examples

### 1. Route Definition

**Python:**
```python
@app.route('/request', methods=['POST'])
def handle_request():
    data = request.get_json()
    # ... processing
    return jsonify({'success': True, 'data': result})
```

**Java:**
```java
@PostMapping("/request")
public ResponseEntity<CreateRequestResponse> createRequest(
        @RequestBody CreateRequestDto requestDto) {
    // ... processing
    return ResponseEntity.ok(
        CreateRequestResponse.builder()
            .success(true)
            .data(result)
            .build()
    );
}
```

### 2. Cryptographic Operations

**Python:**
```python
def generate_secure_nonce(length_bytes=32):
    raw_nonce = os.urandom(length_bytes)
    base64_nonce = base64.urlsafe_b64encode(raw_nonce).decode("utf-8").rstrip("=")
    return raw_nonce, base64_nonce
```

**Java:**
```java
public static NonceResult generateSecureNonce(int lengthBytes) {
    byte[] rawNonce = new byte[lengthBytes];
    SECURE_RANDOM.nextBytes(rawNonce);
    String base64Nonce = encodeKeyBase64(rawNonce);
    return new NonceResult(rawNonce, base64Nonce);
}
```

### 3. JWE Key Generation

**Python:**
```python
from jwcrypto import jwk
key = jwk.JWK.generate(kty='EC', crv='P-256')
```

**Java:**
```java
ECKey ecKey = new ECKeyGenerator(Curve.P_256)
    .keyID(UUID.randomUUID().toString())
    .generate();
```

### 4. HTTP Requests

**Python:**
```python
import requests
response = requests.post(url, json=data)
result = response.json()
```

**Java:**
```java
RestTemplate restTemplate = new RestTemplate();
HttpEntity<Map> request = new HttpEntity<>(data, headers);
ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
Map result = response.getBody();
```

### 5. Configuration

**Python:**
```python
APP_PACKAGE_NAME = "com.google.wallet.gup.identityref.demo"
ZK_VERIFIER_URL = "http://127.0.0.1:5001/zkverify"
```

**Java:**
```java
@Configuration
@ConfigurationProperties(prefix = "app")
@Data
public class AppConfig {
    private String packageName;
}

// In application.properties:
// app.package-name=com.google.wallet.gup.identityref.demo
```

## Dependency Configuration

### Python (requirements.txt)
```
flask==3.0.0
flask-cors==4.0.0
jwcrypto==1.5.0
cbor2==5.4.6
requests==2.31.0
isomdoc==0.1.0
```

### Java (pom.xml)
```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>com.nimbusds</groupId>
        <artifactId>nimbus-jose-jwt</artifactId>
        <version>9.37.3</version>
    </dependency>
    <!-- ... other dependencies -->
</dependencies>
```

## Testing Strategy

### Unit Tests

**Python:**
```python
import unittest

class TestVerification(unittest.TestCase):
    def test_nonce_generation(self):
        raw, b64 = generate_secure_nonce(32)
        self.assertEqual(len(raw), 32)
```

**Java:**
```java
@Test
public void testNonceGeneration() {
    NonceResult result = CryptoUtil.generateSecureNonce(32);
    assertEquals(32, result.getRawNonce().length);
}
```

## Error Handling

### Python
```python
try:
    # ... operation
    return jsonify({'success': True})
except Exception as e:
    print(f"Error: {e}")
    return jsonify({'success': False, 'error': str(e)}), 500
```

### Java
```java
try {
    // ... operation
    return ResponseEntity.ok(response);
} catch (Exception e) {
    log.error("Error occurred", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ErrorResponse.builder()
            .success(false)
            .error("An error occurred")
            .build());
}
```

## Logging

### Python
```python
print(f"Processing request for protocol: {protocol}")
```

### Java
```java
@Slf4j
public class MyService {
    public void process(String protocol) {
        log.info("Processing request for protocol: {}", protocol);
    }
}
```

## Common Pitfalls and Solutions

### 1. Null Safety
- **Python**: Uses `None` checks
- **Java**: Use `Optional<T>` or null checks with proper annotations

### 2. Type Safety
- **Python**: Dynamic typing with duck typing
- **Java**: Static typing - define DTOs for all data structures

### 3. Dictionary/Map Handling
- **Python**: Native dict syntax `{"key": value}`
- **Java**: Use `Map<String, Object>` or create proper DTOs

### 4. JSON Serialization
- **Python**: Automatic with `jsonify()`
- **Java**: Configure Jackson properly, use annotations like `@JsonProperty`

### 5. CORS
- **Python**: `flask_cors` decorator
- **Java**: Explicit CORS configuration class

## Performance Considerations

### Python Flask
- Single-threaded by default
- Use Gunicorn/uWSGI for production
- Limited concurrent request handling

### Java Spring Boot
- Multi-threaded by default
- Built-in embedded Tomcat
- Better concurrent request handling
- Larger memory footprint

## Deployment

### Python
```bash
gunicorn -w 4 -b 0.0.0.0:5001 main:app
```

### Java
```bash
java -jar credential-verifier-1.0.0.jar
# or
mvn spring-boot:run
```

## Migration Checklist

- [x] Convert Flask routes to Spring Controllers
- [x] Map Python libraries to Java equivalents
- [x] Create configuration files
- [x] Implement service layer
- [x] Create DTOs for request/response
- [x] Set up CORS configuration
- [x] Implement cryptographic utilities
- [ ] Add comprehensive unit tests
- [ ] Add integration tests
- [ ] Implement proper mdoc verification library
- [ ] Add proper state management (Redis/DB)
- [ ] Security hardening
- [ ] Performance optimization
- [ ] Production deployment setup

## Next Steps

1. **Testing**: Add comprehensive test coverage
2. **Integration**: Integrate proper mdoc/ISO 18013-5 library
3. **State Management**: Implement Redis or database for state storage
4. **Security**: Add authentication, rate limiting, input validation
5. **Monitoring**: Add application metrics and monitoring
6. **Documentation**: API documentation (Swagger/OpenAPI)
7. **CI/CD**: Set up build and deployment pipelines

## Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Nimbus JOSE + JWT](https://connect2id.com/products/nimbus-jose-jwt)
- [ISO 18013-5 Standard](https://www.iso.org/standard/69084.html)
- [OpenID4VP Specification](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)