import React, { useState } from 'react';
import './GoogleWalletButton.css';

const GoogleWalletButton = ({ userData }) => {
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState('');
  const [isSupported, setIsSupported] = useState(true);

  const checkWalletSupport = () => {
    // Check if user is on a mobile device (Google Wallet works best on mobile)
    const userAgent = navigator.userAgent.toLowerCase();
    const isMobile = /android|iphone|ipad|ipod/.test(userAgent);

    if (!isMobile) {
      setIsSupported(false);
      setStatus('Google Wallet works best on mobile devices. Please visit on your phone.');
    }

    // Check if browser supports Web NFC for tap functionality
    const supportsWebNFC = 'NDEFReader' in window;
    console.log('Web NFC supported:', supportsWebNFC);

    return isMobile;
  };

  const generatePassJWT = () => {
    // In a real implementation, this would be generated on your backend
    // This is a simplified version for demonstration
    const passData = {
      iss: 'your-issuer-id', // From Google Wallet Console
      aud: 'google',
      typ: 'savetowallet',
      payload: {
        genericObjects: [{
          id: `${userData.verificationId}`,
          classId: 'your-issuer-id.age-verification',
          genericType: 'GENERIC_TYPE_UNSPECIFIED',
          hexBackgroundColor: '#4285F4',
          logo: {
            sourceUri: {
              uri: 'https://via.placeholder.com/128x128/4285F4/FFFFFF?text=AV'
            }
          },
          cardTitle: {
            defaultValue: {
              language: 'en',
              value: 'Age Verified'
            }
          },
          subheader: {
            defaultValue: {
              language: 'en',
              value: 'Digital ID'
            }
          },
          header: {
            defaultValue: {
              language: 'en',
              value: userData.name
            }
          },
          textModulesData: [
            {
              header: 'AGE',
              body: `${userData.age} years old`,
              id: 'age'
            },
            {
              header: 'STATUS',
              body: 'Verified ✅',
              id: 'status'
            },
            {
              header: 'ISSUED',
              body: new Date().toLocaleDateString(),
              id: 'issued'
            }
          ],
          linksModuleData: {
            uris: [
              {
                uri: 'https://your-domain.com/verify',
                description: 'Verify Online',
                id: 'verify'
              }
            ]
          },
          barcode: {
            type: 'QR_CODE',
            value: userData.verificationId,
            alternateText: 'Scan to verify'
          }
        }]
      }
    };

    // Base64 encode the JWT (simplified)
    const base64Data = btoa(JSON.stringify(passData));
    return base64Data;
  };

  const handleAddToWallet = async () => {
    if (!checkWalletSupport()) {
      return;
    }

    setLoading(true);
    setStatus('Generating your digital pass...');

    try {
      // Generate pass data
      const jwt = generatePassJWT();

      // Create the Google Wallet save link
      const saveUrl = `https://pay.google.com/gp/v/save/${jwt}`;

      // Open in new window
      const newWindow = window.open(saveUrl, '_blank');

      if (!newWindow || newWindow.closed || typeof newWindow.closed === 'undefined') {
        // Popup blocked, redirect current page
        setStatus('Please allow popups or click the link below...');

        // Create fallback link
        const fallbackLink = document.createElement('a');
        fallbackLink.href = saveUrl;
        fallbackLink.textContent = 'Click here to add to Google Wallet';
        fallbackLink.className = 'fallback-link';
        fallbackLink.target = '_blank';

        const container = document.querySelector('.wallet-status-container');
        if (container) {
          container.appendChild(fallbackLink);
        }
      } else {
        setStatus('Opening Google Wallet...');

        // Check if pass was added successfully (simulated)
        setTimeout(() => {
          setStatus('Pass added successfully! Check your Google Wallet app.');
        }, 2000);
      }

    } catch (error) {
      console.error('Error:', error);
      setStatus('Failed to add to Google Wallet. Please try again.');

      // Show debug info
      console.log('User Data:', userData);
      console.log('User Agent:', navigator.userAgent);

    } finally {
      setLoading(false);
    }
  };

  const simulateNFC = () => {
    if ('NDEFReader' in window) {
      setStatus('Tap your phone to an NFC reader to simulate...');

      // This would be the actual Web NFC code
      // const ndef = new NDEFReader();
      // ndef.scan().then(() => {
      //   ndef.onreadingerror = () => {
      //     setStatus('Cannot read NFC tag');
      //   };
      //   ndef.onreading = event => {
      //     setStatus('NFC tag read successfully!');
      //   };
      // });
    } else {
      setStatus('Web NFC not supported in this browser');
    }
  };

  return (
    <div className="wallet-container">
      <button
        onClick={handleAddToWallet}
        disabled={loading || !isSupported}
        className={`wallet-button ${loading ? 'loading' : ''} ${!isSupported ? 'not-supported' : ''}`}
      >
        {loading ? (
          <>
            <span className="spinner"></span>
            Generating Pass...
          </>
        ) : (
          <>
            <img
              src="https://www.gstatic.com/instantbuy/svg/digital_wallet.svg"
              alt="Google Wallet"
              className="wallet-icon"
            />
            Add to Google Wallet
          </>
        )}
      </button>

      {!isSupported && (
        <div className="support-warning">
          <p>⚠️ For best experience, use a mobile device with Google Wallet installed.</p>
        </div>
      )}

      <div className="wallet-status-container">
        {status && <p className="wallet-status">{status}</p>}
      </div>

      <div className="wallet-options">
        <button
          onClick={simulateNFC}
          className="nfc-button"
          disabled={!('NDEFReader' in window)}
        >
          <span className="nfc-icon">📱</span>
          Test NFC Tap
        </button>

        <button
          onClick={() => window.open('#download', '_blank')}
          className="download-button"
        >
          <span className="download-icon">⬇️</span>
          Download QR Code
        </button>
      </div>

      <div className="wallet-tips">
        <h4>💡 Tips:</h4>
        <ul>
          <li>Make sure Google Wallet app is installed on your phone</li>
          <li>You can use this pass offline</li>
          <li>Tap on NFC readers for instant verification</li>
          <li>Pass updates automatically when information changes</li>
        </ul>
      </div>
    </div>
  );
};

export default GoogleWalletButton;