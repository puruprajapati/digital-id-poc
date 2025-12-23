package org.example.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;

@Service
public class CborParserService {

  private final ObjectMapper cborMapper = new ObjectMapper(new CBORFactory());
  private final ObjectMapper jsonMapper = new ObjectMapper();

  /**
   * Parse ISO mdoc CBOR structure and extract claims
   */
  public MdocData parseMdocCbor(String base64CborData) throws IOException {
    // Use URL-safe decoder to accept '-' and '_' characters often present in web-safe base64
    byte[] cborBytes = Base64.getUrlDecoder().decode(base64CborData);

    System.out.println("Parsing CBOR data, length: " + cborBytes.length);

    MdocData result = new MdocData();

    try {
      // Parse the outer CBOR structure
      JsonNode rootNode = cborMapper.readTree(new ByteArrayInputStream(cborBytes));

      System.out.println("CBOR structure: " + rootNode.toString());

      // ISO mdoc structure typically has:
      // - version
      // - documents (array)
      //   - docType
      //   - issuerSigned
      //     - nameSpaces (map)
      //       - org.iso.18013.5.1 (array of claims OR array of base64-encoded inner CBOR)
      //   - deviceSigned

      if (rootNode.has("documents") && rootNode.get("documents").isArray()) {
        JsonNode documents = rootNode.get("documents");
        if (!documents.isEmpty()) {
          JsonNode firstDoc = documents.get(0);

          // Get docType
          if (firstDoc.has("docType")) {
            result.setDocType(firstDoc.get("docType").asText());
          }

          // Parse issuerSigned section
          if (firstDoc.has("issuerSigned")) {
            JsonNode issuerSigned = firstDoc.get("issuerSigned");
            if (issuerSigned.has("nameSpaces")) {
              JsonNode nameSpaces = issuerSigned.get("nameSpaces");

              // Look for org.iso.18013.5.1 namespace
              if (nameSpaces.has("org.iso.18013.5.1")) {
                JsonNode claimsNode = nameSpaces.get("org.iso.18013.5.1");
                System.out.println("ISO namespace node type: " + claimsNode.getNodeType() + ", content: " + claimsNode.toString().substring(0, Math.min(200, claimsNode.toString().length())));

                // If the namespace node is already an array of claim objects, parse directly
                if (claimsNode.isArray()) {
                  System.out.println("Namespace is array with " + claimsNode.size() + " elements");

                  // CASE A: elements are claim objects (elementIdentifier/elementValue)
                  boolean firstIsObject = claimsNode.size() > 0 && claimsNode.get(0).isObject();
                  System.out.println("First element is object? " + firstIsObject);

                  if (firstIsObject) {
                    System.out.println("Parsing as claim objects directly");
                    parseClaims(claimsNode, result);
                  } else {
                    // CASE B: elements are textual base64-encoded CBOR fragments (common with some mdoc variants)
                    System.out.println("Elements are textual - treating as base64 encoded CBOR fragments");
                    for (int idx = 0; idx < claimsNode.size(); idx++) {
                      JsonNode item = claimsNode.get(idx);
                      try {
                        byte[] innerBytes = null;

                        if (item.isTextual()) {
                          String innerB64 = item.asText();
                          System.out.println("Processing textual base64 fragment #" + idx + " (length: " + innerB64.length() + ")");

                          // Many implementations use URL-safe base64 for web transport
                          try {
                            innerBytes = Base64.getUrlDecoder().decode(innerB64);
                            System.out.println("Decoded with URL-safe decoder: " + innerBytes.length + " bytes");
                          } catch (IllegalArgumentException iae) {
                            // Fallback to standard decoder if url-decoder fails
                            System.out.println("URL-safe decode failed, trying standard decoder");
                            innerBytes = Base64.getDecoder().decode(innerB64);
                          }
                        } else if (item.isBinary()) {
                          // Jackson CBOR mapper converts base64 strings to BINARY nodes - these ARE already the raw CBOR bytes!
                          System.out.println("Processing BINARY node #" + idx + " - this is already decoded CBOR data");
                          innerBytes = item.binaryValue();
                          System.out.println("BINARY data length: " + innerBytes.length + " bytes");
                        }

                        if (innerBytes == null || innerBytes.length == 0) {
                          System.out.println("Skipping empty element #" + idx + " of type " + item.getNodeType());
                          continue;
                        }

                        // Try to parse the decoded inner CBOR fragment
                        try {
                          JsonNode innerNode = cborMapper.readTree(new ByteArrayInputStream(innerBytes));
                          System.out.println("Parsed inner CBOR node type: " + innerNode.getNodeType());

                          if (innerNode.isArray()) {
                            System.out.println("Inner node is array with " + innerNode.size() + " elements");
                            parseClaims(innerNode, result);
                          } else if (innerNode.isObject()) {
                            System.out.println("Inner node is object - attempting to parse as single claim");
                            System.out.println("Object has elementIdentifier? " + innerNode.has("elementIdentifier"));
                            System.out.println("Object has elementValue? " + innerNode.has("elementValue"));

                            // First try to parse as a single claim object (the most common case with Google Wallet)
                            parseSingleClaim(innerNode, result);

                            // If that didn't work, try other approaches
                            if (result.getBirthDate() == null && result.getAgeOver18() == null && result.getAgeOver21() == null) {
                              System.out.println("Single claim parse didn't extract values, trying other approaches");

                              if (innerNode.has("claims") && innerNode.get("claims").isArray()) {
                                System.out.println("Found 'claims' array in inner object");
                                parseClaims(innerNode.get("claims"), result);
                              } else {
                                // Try interpreting object fields directly
                                System.out.println("Parsing object fields directly");
                                innerNode.fields().forEachRemaining(entry -> {
                                  String key = entry.getKey();
                                  JsonNode val = entry.getValue();
                                  System.out.println("  Field: " + key + " = " + val + " (type: " + val.getNodeType() + ")");
                                });
                              }
                            }
                          }

                        } catch (Exception innerEx) {
                          System.err.println("Failed to parse inner CBOR fragment: " + innerEx.getMessage());
                          innerEx.printStackTrace();
                        }
                      } catch (Exception ex) {
                        System.err.println("Error decoding namespace element: " + ex.getMessage());
                        ex.printStackTrace();
                      }
                    }
                  }
                } else if (claimsNode.isObject()) {
                  System.out.println("Namespace is object");
                  // Sometimes the namespace directly contains an object with claims
                  if (claimsNode.has("claims") && claimsNode.get("claims").isArray()) {
                    parseClaims(claimsNode.get("claims"), result);
                  } else {
                    // Try interpreting object fields
                    System.out.println("Parsing namespace object as single claim");
                    parseSingleClaim(claimsNode, result);
                  }
                }
              }
            }
          }
        }
      }

    } catch (Exception e) {
      System.err.println("Error parsing CBOR: " + e.getMessage());
      e.printStackTrace();

      // Fallback: try to parse as raw CBOR map
      try {
        JsonNode rawNode = cborMapper.readTree(new ByteArrayInputStream(cborBytes));
        System.out.println("Raw CBOR parse result: " + rawNode.toString());
      } catch (Exception ex) {
        System.err.println("Even raw CBOR parsing failed: " + ex.getMessage());
      }
    }

    return result;
  }

  private void parseClaims(JsonNode claimsArray, MdocData result) {
    if (claimsArray == null) {
      return;
    }

    if (!claimsArray.isArray()) {
      // If it's a single object, wrap it in array logic
      if (claimsArray.isObject()) {
        parseSingleClaim(claimsArray, result);
      }
      return;
    }

    for (JsonNode claimNode : claimsArray) {
      parseSingleClaim(claimNode, result);
    }
  }

  private void parseSingleClaim(JsonNode claimNode, MdocData result) {
    try {
      // Each claim has: digestID, random, elementIdentifier, elementValue (in CBOR structure)
      if (claimNode.has("elementIdentifier") && claimNode.has("elementValue")) {
        String identifier = claimNode.get("elementIdentifier").asText();
        JsonNode value = claimNode.get("elementValue");

        System.out.println("Found claim: " + identifier + " = " + value);
        extractClaimValue(identifier, value, result);
      } else {
        // Some implementations embed identifier/value in object fields directly
        // or use variations of the key names (e.g., truncated in CBOR)
        String identifierKey = null;
        String identifierValue = null;
        JsonNode valueNode = null;

        // Look for element identifier with various key names (case-insensitive, handle truncation)
        for (Iterator<String> it = claimNode.fieldNames(); it.hasNext();) {
          String key = it.next();
          JsonNode val = claimNode.get(key);

          // Match identifier-like keys (could be "elementIdentifier" or "lementIdentifier" etc)
          if (key.toLowerCase().contains("identifier") && val.isTextual()) {
            identifierKey = key;
            identifierValue = val.asText();
            System.out.println("Found identifier key: " + key + " = " + identifierValue);
          }
          // Match value-like keys
          else if (key.toLowerCase().equals("elementvalue") || key.toLowerCase().contains("lementvalue")) {
            valueNode = val;
            System.out.println("Found value key: " + key + " = " + val);
          }
        }

        if (identifierValue != null && valueNode != null) {
          System.out.println("Extracting claim from identified fields: " + identifierValue + " = " + valueNode);
          extractClaimValue(identifierValue, valueNode, result);
        } else if (claimNode.isObject()) {
          // Fallback: scan all fields and look for known claim names
          claimNode.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode val = entry.getValue();

            System.out.println("Inspecting object field: " + key + " = " + val);

            String keyLower = key.toLowerCase();

            // Birth date variations
            if (keyLower.equals("birth_date") || keyLower.equals("birthdate") ||
                keyLower.equals("date_of_birth") || keyLower.equals("dob")) {
              if (val.isTextual()) {
                result.setBirthDate(val.asText());
                System.out.println("Found birth_date field: " + val.asText());
              }
            }
            // Given name variations
            else if (keyLower.equals("given_name") || keyLower.equals("givenname") ||
                     keyLower.equals("first_name") || keyLower.equals("firstname")) {
              if (val.isTextual()) {
                result.setGivenName(val.asText());
              }
            }
            // Family name variations
            else if (keyLower.equals("family_name") || keyLower.equals("familyname") ||
                     keyLower.equals("last_name") || keyLower.equals("lastname")) {
              if (val.isTextual()) {
                result.setFamilyName(val.asText());
              }
            }
            // Age over 18 variations
            else if (keyLower.equals("age_over_18") || keyLower.equals("ageover18") ||
                     keyLower.equals("over_18") || keyLower.equals("is_over_18")) {
              if (val.isBoolean()) {
                result.setAgeOver18(val.asBoolean());
              }
            }
            // Age over 21 variations
            else if (keyLower.equals("age_over_21") || keyLower.equals("ageover21") ||
                     keyLower.equals("over_21") || keyLower.equals("is_over_21")) {
              if (val.isBoolean()) {
                result.setAgeOver21(val.asBoolean());
              }
            }
          });
        }
      }
    } catch (Exception e) {
      System.err.println("Error parsing individual claim: " + e.getMessage());
    }
  }

  private void extractClaimValue(String identifier, JsonNode valueNode, MdocData result) {
    System.out.println("Extracting claim: identifier=" + identifier + ", value=" + valueNode);

    String identLower = identifier.toLowerCase();

    // Birth date extraction with multiple format support
    if (identLower.equals("birth_date") || identLower.equals("birthdate") ||
        identLower.equals("date_of_birth") || identLower.equals("dob")) {
      if (valueNode.isTextual()) {
        String dateStr = valueNode.asText();
        // Handle ISO date format (YYYY-MM-DD)
        if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
          result.setBirthDate(dateStr);
          System.out.println("Extracted birth_date (ISO format): " + dateStr);
        }
        // Handle other formats if needed
        else if (!dateStr.isEmpty()) {
          result.setBirthDate(dateStr);
          System.out.println("Extracted birth_date (other format): " + dateStr);
        }
      } else if (valueNode.isNumber()) {
        // Some implementations may send numeric timestamp
        System.out.println("Birth date is numeric (timestamp): " + valueNode);
        // Don't set it as we need ISO date format
      }
    }
    // Given name
    else if (identLower.equals("given_name") || identLower.equals("givenname") ||
             identLower.equals("first_name") || identLower.equals("firstname")) {
      if (valueNode.isTextual()) {
        result.setGivenName(valueNode.asText());
        System.out.println("Extracted given_name: " + valueNode.asText());
      }
    }
    // Family name
    else if (identLower.equals("family_name") || identLower.equals("familyname") ||
             identLower.equals("last_name") || identLower.equals("lastname")) {
      if (valueNode.isTextual()) {
        result.setFamilyName(valueNode.asText());
        System.out.println("Extracted family_name: " + valueNode.asText());
      }
    }
    // Age over 18
    else if (identLower.equals("age_over_18") || identLower.equals("ageover18") ||
             identLower.equals("over_18") || identLower.equals("is_over_18")) {
      if (valueNode.isBoolean()) {
        result.setAgeOver18(valueNode.asBoolean());
        System.out.println("Extracted age_over_18: " + valueNode.asBoolean());
      }
    }
    // Age over 21
    else if (identLower.equals("age_over_21") || identLower.equals("ageover21") ||
             identLower.equals("over_21") || identLower.equals("is_over_21")) {
      if (valueNode.isBoolean()) {
        result.setAgeOver21(valueNode.asBoolean());
        System.out.println("Extracted age_over_21: " + valueNode.asBoolean());
      }
    }
    else {
      System.out.println("Unknown claim identifier: " + identifier);
    }
  }

  public static class MdocData {
    private String docType;
    private String birthDate;
    private String givenName;
    private String familyName;
    private Boolean ageOver18;
    private Boolean ageOver21;

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }

    public String getBirthDate() { return birthDate; }
    public void setBirthDate(String birthDate) { this.birthDate = birthDate; }

    public String getGivenName() { return givenName; }
    public void setGivenName(String givenName) { this.givenName = givenName; }

    public String getFamilyName() { return familyName; }
    public void setFamilyName(String familyName) { this.familyName = familyName; }

    public Boolean getAgeOver18() { return ageOver18; }
    public void setAgeOver18(Boolean ageOver18) { this.ageOver18 = ageOver18; }

    public Boolean getAgeOver21() { return ageOver21; }
    public void setAgeOver21(Boolean ageOver21) { this.ageOver21 = ageOver21; }
  }
}