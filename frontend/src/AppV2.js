import React, { useState, useEffect } from 'react';
import { Shield, CheckCircle, XCircle, Loader, Smartphone } from 'lucide-react';
import './AppV2.css';

export default function AppV2() {
  const [minAge, setMinAge] = useState(18);
  const [sessionId, setSessionId] = useState(null);
  const [verificationStatus, setVerificationStatus] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [apiSupported, setApiSupported] = useState(false);
  const [useSimpleMode, setUseSimpleMode] = useState(false);

  const API_BASE = 'https://nonsinkable-fungicidal-concepcion.ngrok-free.dev/api/verification/v2';
  // const API_BASE = 'http://localhost:8080/api/verification/v2';
  // const API_BASE = 'https://permits-execute-speakers-pack.trycloudflare.com/verification/v2';

  useEffect(() => {
    // Check if Digital Credentials API is supported
    if ('credentials' in navigator && 'get' in navigator.credentials) {
      setApiSupported(true);
    } else {
      setError('Digital Credentials API is not supported in this browser. Please use Chrome 141+ or iOS 26+ Safari.');
    }
  }, []);

  const generateEncryptionKeys = async () => {
    // Generate ECDH key pair for response encryption
    const keyPair = await crypto.subtle.generateKey(
      {
        name: 'ECDH',
        namedCurve: 'P-256'
      },
      true,
      ['deriveKey']
    );

    // Export public key in JWK format
    const publicKeyJwk = await crypto.subtle.exportKey('jwk', keyPair.publicKey);

    return {
      keyPair,
      publicKeyJwk
    };
  };

  const initiateVerification = async () => {
    setLoading(true);
    setError(null);

    try {
      const response = await fetch(`${API_BASE}/initiate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ minAge })
      });

      const data = await response.json();

      if (data.success) {
        setSessionId(data.sessionId);
        // After session is created, immediately request credentials
        await requestDigitalCredential(data.sessionId);
      } else {
        setError(data.message);
      }
    } catch (err) {
      setError('Failed to connect to backend: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const requestDigitalCredential = async (session) => {
    let timeoutId
    try {
      setLoading(true);

      // Generate encryption keys
      //const { keyPair, publicKeyJwk } = await generateEncryptionKeys();

      // Create nonce for request
      const nonceArray = new Uint8Array(16);
      crypto.getRandomValues(nonceArray);
      const nonce = Array.from(nonceArray, byte => byte.toString(16).padStart(2, '0')).join('');

      // Build the Digital Credentials API request
      let credentialRequest;

      // doctype_value: 'org.iso.18013.5.1.mDL'  // ❌ Only for driver's licenses
      // doctype_value: 'com.google.wallet.idcard.1' // ✅ For Google Wallet ID Pass (Utopia)
      if (useSimpleMode) {
        // Simple mode: Only request age_over_18 or age_over_21
        const ageField = minAge >= 21 ? 'age_over_21' : 'age_over_18';

        credentialRequest = {
          digital: {
            requests: [{
              protocol: 'openid4vp-v1-unsigned',
              data: {
                response_type: 'vp_token',
                response_mode: 'dc_api', // Use unencrypted for testing, let brower decide
                nonce: nonce,
                dcql_query: {
                  credentials: [
                    {
                      id: 'id_pass-simple',
                      // id: 'id_pass-request',
                      format: 'mso_mdoc',
                      meta: {
                        doctype_value: 'com.google.wallet.idcard.1'
                      },
                      claims: [
                        {
                          path: ['org.iso.18013.5.1', ageField],
                          intent_to_retain: false
                        }
                      ]
                    }
                  ]
                },
                client_metadata: {
                  vp_formats_supported: {
                    mso_mdoc: {
                      // deviceauth_alg_values: [-7],
                      // issuerauth_alg_values: [-7]
                      alg_values_supported: [-7]
                    }
                  }
                }
              }
            }]
          }
        };
      } else {
        // Full mode: Request all available fields
        credentialRequest = {
          digital: {
            requests: [{
              protocol: 'openid4vp-v1-unsigned',
              data: {
                response_type: 'vp_token',
                response_mode: 'dc_api',  // Use unencrypted for testing
                nonce: nonce,
                dcql_query: {
                  credentials: [
                    {
                      id: 'id_pass-request',
                      format: 'mso_mdoc',
                      meta: {
                        doctype_value: 'com.google.wallet.idcard.1' // Google Wallet ID Pass
                      },
                      claims: [
                        {
                          path: ['org.iso.18013.5.1', 'birth_date'],
                          intent_to_retain: false
                        },
                        {
                          path: ['org.iso.18013.5.1', 'given_name'],
                          intent_to_retain: false
                        },
                        {
                          path: ['org.iso.18013.5.1', 'family_name'],
                          intent_to_retain: false
                        },
                        {
                          path: ['org.iso.18013.5.1', 'age_over_18'],
                          intent_to_retain: false
                        },
                        {
                          path: ['org.iso.18013.5.1', 'age_over_21'],
                          intent_to_retain: false
                        }
                      ]
                    },
                    {
                      id: 'mdl-request',
                      format: 'mso_mdoc',
                      meta: {
                        doctype_value: 'org.iso.18013.5.1.mDL'  // Mobile Driver's License
                      },
                      claims: [
                        {
                          path: ['org.iso.18013.5.1', 'birth_date'],
                          intent_to_retain: false
                        },
                        {
                          path: ['org.iso.18013.5.1', 'given_name'],
                          intent_to_retain: false
                        },
                        {
                          path: ['org.iso.18013.5.1', 'family_name'],
                          intent_to_retain: false
                        }
                      ]
                    }
                  ],
                  credential_sets: [
                    {
                      options: [
                        ['id_pass-request'],
                        ['mdl-request']
                      ]
                    }
                  ]
                },
                client_metadata: {
                  vp_formats_supported: {
                    mso_mdoc: {
                      // deviceauth_alg_values: [-7],
                      // issuerauth_alg_values: [-7]
                      alg_values_supported: [-7]
                    }
                  }
                }
              }
            }]
          }
        };
      }

      console.log('Requesting credential with:', JSON.stringify(credentialRequest, null, 2));

      // Set a timeout to catch hanging requests
      const timeout = new Promise((_, reject) => {
        timeoutId = setTimeout(() => reject(new Error('Request timeout after 60 seconds')), 60000);
      });

      // Call the Digital Credentials API
      // const credential = await navigator.credentials.get(credentialRequest);

      // console.log('Received credential:', credential);

      // Call the Digital Credentials API with timeout
      const credentialPromise = navigator.credentials.get(credentialRequest);

      let credential;
      try {
        credential = await Promise.race([credentialPromise, timeout]);
        clearTimeout(timeoutId);
        console.log('Received credential:', credential);
      } catch (apiError) {
        clearTimeout(timeoutId);
        console.error('Navigator.credentials.get error:', apiError);
        console.error('Error name:', apiError.name);
        console.error('Error message:', apiError.message);
        throw apiError;
      }


      if (credential && credential.protocol === 'openid4vp-v1-unsigned') {
        // Extract the response
        const responseData = credential.data;

        console.log('Response data:', responseData);
        console.log('Response data type:', typeof responseData);
        console.log('Response data keys:', Object.keys(responseData || {}));


        // Send to backend for verification
        const verifyResponse = await fetch(`${API_BASE}/verify`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            sessionId: session,
            credential: JSON.stringify(responseData),
            simpleMode: useSimpleMode
          })
        });

        const verifyData = await verifyResponse.json();
        setVerificationStatus(verifyData);

        if (!verifyData.success) {
          setError(verifyData.message);
        }
      }
    } catch (err) {
      if (err.name === 'NotAllowedError') {
        setError('User denied the credential request or no credentials available');
      } else if (err.name === 'AbortError') {
        setError('Request was aborted');
      } else {
        setError('Failed to request credential: ' + err.message);
      }
      console.error('Digital Credentials API error:', err);
    } finally {
      setLoading(false);
    }
  };

  const reset = () => {
    setSessionId(null);
    setVerificationStatus(null);
    setError(null);
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 p-4 sm:p-8">
      <div className="max-w-2xl mx-auto">
        <div className="bg-white rounded-2xl shadow-xl p-6 sm:p-8">
          <div className="flex items-center justify-center mb-8">
            <Shield className="w-12 h-12 text-indigo-600 mr-3" />
            <h1 className="text-2xl sm:text-3xl font-bold text-gray-800">Age Verification</h1>
          </div>

          {!apiSupported && (
            <div className="mb-6 p-4 bg-yellow-50 border border-yellow-200 rounded-lg">
              <p className="text-yellow-800 text-sm">
                <strong>Browser Not Supported:</strong> This demo requires Chrome 141+ or Safari on iOS 26+.
              </p>
            </div>
          )}

          {error && (
            <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg flex items-start">
              <XCircle className="w-5 h-5 text-red-500 mr-2 mt-0.5 flex-shrink-0" />
              <span className="text-red-700 text-sm">{error}</span>
            </div>
          )}

          {!sessionId && !verificationStatus ? (
            <div className="space-y-6">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Minimum Age Required
                </label>
                <input
                  type="number"
                  value={minAge}
                  onChange={(e) => setMinAge(parseInt(e.target.value) || 18)}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                  min="1"
                  max="120"
                />
              </div>

              <div className="p-4 bg-gray-50 border border-gray-200 rounded-lg">
                <label className="flex items-start cursor-pointer">
                  <input
                    type="checkbox"
                    checked={useSimpleMode}
                    onChange={(e) => setUseSimpleMode(e.target.checked)}
                    className="mt-1 mr-3"
                  />
                  <div>
                    <p className="font-medium text-gray-900 text-sm">Privacy Mode (Recommended)</p>
                    <p className="text-gray-600 text-xs mt-1">
                      Only verify age (over 18/21) without revealing birth date or name. More privacy-friendly.
                    </p>
                  </div>
                </label>
              </div>

              <button
                onClick={initiateVerification}
                disabled={loading || !apiSupported}
                className="w-full bg-indigo-600 text-white py-3 rounded-lg font-semibold hover:bg-indigo-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center"
              >
                {loading ? (
                  <>
                    <Loader className="w-5 h-5 mr-2 animate-spin" />
                    Requesting Credential...
                  </>
                ) : (
                  <>
                    <Smartphone className="w-5 h-5 mr-2" />
                    Verify Age with Google Wallet
                  </>
                )}
              </button>

              <div className="mt-8 p-4 bg-blue-50 border border-blue-200 rounded-lg">
                <h3 className="font-semibold text-blue-900 mb-2">Real Google Wallet Integration</h3>
                <p className="text-sm text-blue-700 mb-2">
                  This uses the Digital Credentials API to request verification from Google Wallet.
                </p>
                <ul className="text-sm text-blue-700 ml-4 list-disc space-y-1">
                  <li>Google Wallet app installed on your device</li>
                  <li>Digital ID added from Utopia Passport Simulator</li>
                  <li>Chrome 141+ on Android or Safari on iOS 26+</li>
                  <li>Test on mobile device for best results</li>
                </ul>
              </div>

              <div className="p-4 bg-gray-50 border border-gray-200 rounded-lg">
                <h3 className="font-semibold text-gray-900 mb-2">How it works:</h3>
                <ol className="text-sm text-gray-700 ml-4 list-decimal space-y-1">
                  <li>Click "Verify Age with Google Wallet"</li>
                  <li>Browser will show Google Wallet consent dialog</li>
                  <li>Approve sharing your age information</li>
                  <li>Results appear automatically</li>
                </ol>
              </div>
            </div>
          ) : verificationStatus ? (
            <div className="space-y-6">
              <div className={`p-6 rounded-lg ${verificationStatus.verified
                ? 'bg-green-50 border border-green-200'
                : 'bg-red-50 border border-red-200'
                }`}>
                <div className="flex items-center mb-4">
                  {verificationStatus.verified ? (
                    <>
                      <CheckCircle className="w-8 h-8 text-green-600 mr-3" />
                      <h3 className="text-xl font-bold text-green-900">
                        Verification Successful
                      </h3>
                    </>
                  ) : (
                    <>
                      <XCircle className="w-8 h-8 text-red-600 mr-3" />
                      <h3 className="text-xl font-bold text-red-900">
                        Verification Failed
                      </h3>
                    </>
                  )}
                </div>

                <div className="space-y-2">
                  {verificationStatus.givenName && (
                    <p className={verificationStatus.verified ? 'text-green-800' : 'text-red-800'}>
                      <strong>Name:</strong> {verificationStatus.givenName} {verificationStatus.familyName}
                    </p>
                  )}
                  <p className={verificationStatus.verified ? 'text-green-800' : 'text-red-800'}>
                    <strong>Age:</strong> {verificationStatus.age} years old
                  </p>
                  <p className={verificationStatus.verified ? 'text-green-800' : 'text-red-800'}>
                    <strong>Required:</strong> {verificationStatus.minAge}+ years old
                  </p>
                  <p className={verificationStatus.verified ? 'text-green-800' : 'text-red-800'}>
                    <strong>Status:</strong> {verificationStatus.message}
                  </p>
                </div>
              </div>

              <button
                onClick={reset}
                className="w-full bg-gray-600 text-white py-3 rounded-lg font-semibold hover:bg-gray-700 transition-colors"
              >
                Start New Verification
              </button>
            </div>
          ) : (
            <div className="text-center py-8">
              <Loader className="w-12 h-12 text-indigo-600 mx-auto mb-4 animate-spin" />
              <p className="text-gray-600">Processing verification...</p>
            </div>
          )}
        </div>

        <div className="mt-6 text-center text-sm text-gray-600">
          <p>Powered by Google Wallet Digital Credentials API</p>
        </div>
      </div>
    </div>
  );
}