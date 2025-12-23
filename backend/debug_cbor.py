#!/usr/bin/env python3
import base64
import json
import cbor2

# The base64 string from your namespace - this is the single element in the array
base64_data = "pGhkaWdlc3RJRBkWF2ZyYW5kb21QHN+DMvBOaGr5GzeSFPIIdnFlbGVtZW50SWRlbnRpZmllcmthZ2Vfb3Zlcl8xOGxlbGVtZW50VmFsdWX1"

# Decode with URL-safe decoder (handles - and _ characters)
decoded = base64.urlsafe_b64decode(base64_data + "=" * (4 - len(base64_data) % 4))
print(f"Decoded bytes length: {len(decoded)}")
print(f"Hex dump (first 100 bytes): {decoded[:100].hex()}")

# Parse CBOR
try:
    parsed = cbor2.loads(decoded)
    print(f"\nParsed CBOR structure:")
    print(json.dumps(parsed, indent=2, default=str))
except Exception as e:
    print(f"Error parsing CBOR: {e}")

    # Try to show raw structure
    print(f"\nRaw byte analysis:")
    print(f"First byte: 0x{decoded[0]:02x}")
    if decoded[0] == 0xa4:
        print("  -> This is a CBOR map with 4 keys")
    elif decoded[0] == 0xa5:
        print("  -> This is a CBOR map with 5 keys")

