import React, { useState, useEffect } from 'react';
import { Shield, CheckCircle, XCircle, Loader, Smartphone, ExternalLink, Settings } from 'lucide-react';
import './App.css';

export default function App() {
  const [minAge, setMinAge] = useState(18);
  const [sessionId, setSessionId] = useState(null);
  const [verificationStatus, setVerificationStatus] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [mockCredential, setMockCredential] = useState('');
  const [walletUrl, setWalletUrl] = useState('');
  const [polling, setPolling] = useState(false);
  const [qrCode, setQrCode] = useState('');
  const [showSettings, setShowSettings] = useState(false);
  const [isSimulate, setIsSimulate] = useState(false);

  const API_BASE = 'https://nonsinkable-fungicidal-concepcion.ngrok-free.dev/api/verification';

  // Check for callback parameters on mount
  useEffect(() => {
    const urlParams = new URLSearchParams(window.location.search);
    const success = urlParams.get('success');
    const session = urlParams.get('session');
    const errorParam = urlParams.get('error');

    if (errorParam) {
      setError(`Verification error: ${errorParam}`);
      window.history.replaceState({}, '', '/');
    }

    if (success === 'true' && session) {
      setSessionId(session);
      setPolling(true);
      checkForToken(session);
      window.history.replaceState({}, '', '/');
    }
  }, []);

  // Poll for token after callback
  const checkForToken = async (session) => {
    let attempts = 0;
    const maxAttempts = 30;

    const poll = setInterval(async () => {
      attempts++;

      try {
        const response = await fetch(`${API_BASE}/token/${session}`);
        const data = await response.json();

        if (data.success && data.credential) {
          clearInterval(poll);
          setPolling(false);
          // Automatically verify with the received token
          await verifyWithToken(session, data.credential);
        }

        if (attempts >= maxAttempts) {
          clearInterval(poll);
          setPolling(false);
          setError('Timeout waiting for verification response');
        }
      } catch (err) {
        console.error('Polling error:', err);
      }
    }, 2000);
  };

  const verifyWithToken = async (session, credential) => {
    setLoading(true);

    try {
      const response = await fetch(`${API_BASE}/verify`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          sessionId: session,
          credential
        })
      });

      const data = await response.json();
      setVerificationStatus(data);

      if (!data.success) {
        setError(data.message);
      }
    } catch (err) {
      setError('Verification failed: ' + err.message);
    } finally {
      setLoading(false);
    }
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
        setWalletUrl(data.requestUrl);

        // if simulate alert else set qr
        // Generate QR code URL
        if (!isSimulate) {

          const qrUrl = `https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=${encodeURIComponent(data.requestUrl)}`;
          setQrCode(qrUrl);
        } else {

        }
      } else {
        setError(data.message);
      }
    } catch (err) {
      setError('Failed to connect to backend: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const openGoogleWallet = () => {
    if (walletUrl) {
      // Open in new window (for desktop) or same window (for mobile)
      if (window.innerWidth <= 768) {
        window.location.href = walletUrl;
      } else {
        window.open(walletUrl, '_blank');
      }
      setPolling(true);
      checkForToken(sessionId);
    }
  };

  const simulateGoogleWalletResponse = () => {
    // Create a mock JWT-like credential for testing
    const header = btoa(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
    const payload = btoa(JSON.stringify({
      birthdate: '1995-06-15',
      given_name: 'John',
      family_name: 'Doe',
      iss: 'https://wallet.google.com',
      iat: Math.floor(Date.now() / 1000)
    }));
    const signature = btoa('mock_signature');

    return `${header}.${payload}.${signature}`;
  };

  const verifyAge = async () => {
    if (!sessionId) {
      setError('Please initiate verification first');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const credential = mockCredential || simulateGoogleWalletResponse();

      const response = await fetch(`${API_BASE}/verify`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          sessionId,
          credential
        })
      });

      const data = await response.json();
      setVerificationStatus(data);

      if (!data.success) {
        setError(data.message);
      }
    } catch (err) {
      setError('Verification failed: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const checkStatus = async () => {
    if (!sessionId) return;

    try {
      const response = await fetch(`${API_BASE}/status/${sessionId}`);
      const data = await response.json();
      setVerificationStatus(data);
    } catch (err) {
      console.error('Status check failed:', err);
    }
  };

  const reset = () => {
    setSessionId(null);
    setVerificationStatus(null);
    setError(null);
    setMockCredential('');
    setWalletUrl('');
    setQrCode('');
    setPolling(false);
  };

  return (
    <div>
      <div className="settings-container">
        <button
          type="button"
          aria-label="Settings"
          onClick={() => setShowSettings((s) => !s)}
          className="settings-btn"
        >
          <Settings className="w-5 h-5" />
        </button>

        {showSettings && (
          <div className="settings-panel">
            <label className="settings-row">
              <input
                type="checkbox"
                checked={isSimulate}
                onChange={(e) => setIsSimulate(e.target.checked)}
              />
              <span className="ml-2">Simulate mode</span>
            </label>
          </div>
        )}
      </div>
      {!isSimulate ? (
        <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 p-4 sm:p-8">
          <div className="max-w-2xl mx-auto">
            <div className="bg-white rounded-2xl shadow-xl p-6 sm:p-8">
              <div className="flex items-center justify-center mb-8">
                <Shield className="w-12 h-12 text-indigo-600 mr-3" />
                <h1 className="text-2xl sm:text-3xl font-bold text-gray-800">Age Verification</h1>
              </div>

              {error && (
                <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg flex items-start">
                  <XCircle className="w-5 h-5 text-red-500 mr-2 mt-0.5 flex-shrink-0" />
                  <span className="text-red-700 text-sm">{error}</span>
                </div>
              )}

              {polling && (
                <div className="mb-6 p-4 bg-blue-50 border border-blue-200 rounded-lg flex items-center">
                  <Loader className="w-5 h-5 text-blue-500 mr-2 animate-spin flex-shrink-0" />
                  <span className="text-blue-700 text-sm">Waiting for verification response from Google Wallet...</span>
                </div>
              )}

              {!sessionId ? (
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

                  <button
                    onClick={initiateVerification}
                    disabled={loading}
                    className="w-full bg-indigo-600 text-white py-3 rounded-lg font-semibold hover:bg-indigo-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center"
                  >
                    {loading ? (
                      <>
                        <Loader className="w-5 h-5 mr-2 animate-spin" />
                        Initializing...
                      </>
                    ) : (
                      <>
                        <Smartphone className="w-5 h-5 mr-2" />
                        Start Verification with Google Wallet
                      </>
                    )}
                  </button>

                  <div className="mt-8 p-4 bg-blue-50 border border-blue-200 rounded-lg">
                    <h3 className="font-semibold text-blue-900 mb-2">Real Google Wallet Integration</h3>
                    <p className="text-sm text-blue-700">
                      This will connect to your actual Google Wallet app. Make sure you have:
                    </p>
                    <ul className="text-sm text-blue-700 mt-2 ml-4 list-disc">
                      <li>Google Wallet app installed on your phone</li>
                      <li>Digital ID added from Utopia Passport Simulator</li>
                      <li>Backend running with ngrok tunnel</li>
                    </ul>
                  </div>
                </div>
              ) : verificationStatus && verificationStatus.verified !== undefined ? (
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
                <div className="space-y-6">
                  <div className="p-4 bg-gray-50 rounded-lg">
                    <p className="text-sm text-gray-600 mb-1">Session ID:</p>
                    <p className="font-mono text-xs text-gray-800 break-all">{sessionId}</p>
                  </div>


                  <div className="text-center">
                    <h3 className="text-lg font-semibold mb-4">Scan QR Code with Your Phone</h3>
                    {qrCode && (
                      <div className="flex justify-center mb-4">
                        <img src={qrCode} alt="QR Code" className="w-64 h-64 border-2 border-gray-300 rounded-lg" />
                      </div>
                    )}
                    <p className="text-sm text-gray-600 mb-4">Or click the button below if you're on mobile:</p>
                  </div>

                  <button
                    onClick={openGoogleWallet}
                    disabled={loading}
                    className="w-full bg-green-600 text-white py-3 rounded-lg font-semibold hover:bg-green-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center"
                  >
                    <ExternalLink className="w-5 h-5 mr-2" />
                    Open Google Wallet
                  </button>

                  <button
                    onClick={reset}
                    className="w-full bg-gray-600 text-white py-3 rounded-lg font-semibold hover:bg-gray-700 transition-colors"
                  >
                    Cancel
                  </button>

                  <div className="p-4 bg-yellow-50 border border-yellow-200 rounded-lg">
                    <p className="text-sm text-yellow-800">
                      <strong>Instructions:</strong>
                    </p>
                    <ol className="text-sm text-yellow-800 mt-2 ml-4 list-decimal">
                      <li>Scan the QR code with your phone camera or click "Open Google Wallet"</li>
                      <li>Approve the verification request in Google Wallet</li>
                      <li>Wait for the verification to complete</li>
                    </ol>
                  </div>
                </div>
              )}
            </div>

            <div className="mt-6 text-center text-sm text-gray-600">
              <p>Powered by Google Wallet Digital ID Integration</p>
            </div>
          </div>
        </div>
      ) : (
        <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 p-8">
          <div className="max-w-2xl mx-auto">
            <div className="bg-white rounded-2xl shadow-xl p-8">
              <div className="flex items-center justify-center mb-8">
                <Shield className="w-12 h-12 text-indigo-600 mr-3" />
                <h1 className="text-3xl font-bold text-gray-800">Age Verification</h1>
              </div>

              {error && (
                <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg flex items-start">
                  <XCircle className="w-5 h-5 text-red-500 mr-2 mt-0.5 flex-shrink-0" />
                  <span className="text-red-700">{error}</span>
                </div>
              )}

              {!sessionId ? (
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

                  <button
                    onClick={initiateVerification}
                    disabled={loading}
                    className="w-full bg-indigo-600 text-white py-3 rounded-lg font-semibold hover:bg-indigo-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center"
                  >
                    {loading ? (
                      <>
                        <Loader className="w-5 h-5 mr-2 animate-spin" />
                        Initializing...
                      </>
                    ) : (
                      'Start Verification with Google Wallet'
                    )}
                  </button>

                  <div className="mt-8 p-4 bg-blue-50 border border-blue-200 rounded-lg">
                    <h3 className="font-semibold text-blue-900 mb-2">Testing Mode</h3>
                    <p className="text-sm text-blue-700">
                      This demo uses a simulated Google Wallet response. In production,
                      the user would scan their digital ID from Google Wallet.
                    </p>
                  </div>
                </div>
              ) : (
                <div className="space-y-6">
                  <div className="p-4 bg-gray-50 rounded-lg">
                    <p className="text-sm text-gray-600 mb-1">Session ID:</p>
                    <p className="font-mono text-xs text-gray-800 break-all">{sessionId}</p>
                  </div>

                  {!verificationStatus || !verificationStatus.verified ? (
                    <>
                      <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">
                          Mock Credential (Optional - for testing different dates)
                        </label>
                        <textarea
                          value={mockCredential}
                          onChange={(e) => setMockCredential(e.target.value)}
                          placeholder="Leave empty to use default mock data (birthdate: 1995-06-15)"
                          className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent font-mono text-xs"
                          rows="3"
                        />
                      </div>

                      <button
                        onClick={verifyAge}
                        disabled={loading}
                        className="w-full bg-green-600 text-white py-3 rounded-lg font-semibold hover:bg-green-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center"
                      >
                        {loading ? (
                          <>
                            <Loader className="w-5 h-5 mr-2 animate-spin" />
                            Verifying...
                          </>
                        ) : (
                          'Simulate Google Wallet Verification'
                        )}
                      </button>
                    </>
                  ) : null}

                  {verificationStatus && (
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
                  )}

                  <button
                    onClick={reset}
                    className="w-full bg-gray-600 text-white py-3 rounded-lg font-semibold hover:bg-gray-700 transition-colors"
                  >
                    Start New Verification
                  </button>
                </div>
              )}
            </div>

            <div className="mt-6 text-center text-sm text-gray-600">
              <p>Powered by Google Wallet Digital ID Integration</p>
            </div>
          </div>
        </div >
      )
      }
    </div >
  );
}

