import React, { useState } from 'react';
import AgeVerificationForm from './components/AgeVerificationForm';
import GoogleWalletButton from './components/GoogleWalletButton';
import PassPreview from './components/PassPreview';
import './App.css';

function App() {
  const [userData, setUserData] = useState({
    name: '',
    age: '',
    email: '',
    isVerified: false,
    verificationId: ''
  });

  const handleVerification = (data) => {
    setUserData({
      ...data,
      verificationId: `VER_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
    });
  };

  const handleReset = () => {
    setUserData({
      name: '',
      age: '',
      email: '',
      isVerified: false,
      verificationId: ''
    });
  };

  return (
    <div className="App">
      <div className="container">
        <header className="app-header">
          <img
            src="https://www.gstatic.com/images/branding/product/1x/pass_64dp.png"
            alt="Google Wallet"
            className="app-logo"
          />
          <h1>Digital Age Verification</h1>
          <p className="app-subtitle">Verify your age and add to Google Wallet</p>
        </header>

        <main className="app-main">
          <div className="form-section">
            <AgeVerificationForm
              onVerify={handleVerification}
              onReset={handleReset}
            />
          </div>

          {userData.isVerified && (
            <>
              <div className="wallet-section">
                <div className="section-header">
                  <h2>Digital Wallet Pass</h2>
                  <p>Add your verified age to Google Wallet for easy access</p>
                </div>

                <div className="pass-section">
                  <div className="pass-preview-container">
                    <PassPreview userData={userData} />
                  </div>

                  <div className="wallet-button-container">
                    <GoogleWalletButton userData={userData} />

                    <div className="wallet-instructions">
                      <h4>How to use:</h4>
                      <ol>
                        <li>Click "Add to Google Wallet"</li>
                        <li>Save the pass to your Google Wallet</li>
                        <li>Show the pass when age verification is needed</li>
                        <li>Tap on NFC-enabled devices for instant verification</li>
                      </ol>
                    </div>
                  </div>
                </div>
              </div>

              <div className="benefits-section">
                <h3>Benefits of Digital Age Verification</h3>
                <div className="benefits-grid">
                  <div className="benefit-card">
                    <div className="benefit-icon">🔒</div>
                    <h4>Secure & Private</h4>
                    <p>Your data is encrypted and only shows necessary information</p>
                  </div>
                  <div className="benefit-card">
                    <div className="benefit-icon">⚡</div>
                    <h4>Instant Verification</h4>
                    <p>No need to carry physical ID. Verify instantly anywhere</p>
                  </div>
                  <div className="benefit-card">
                    <div className="benefit-icon">📱</div>
                    <h4>Always Accessible</h4>
                    <p>Available offline on your phone. Never lose your ID again</p>
                  </div>
                  <div className="benefit-card">
                    <div className="benefit-icon">🔄</div>
                    <h4>Easy to Update</h4>
                    <p>Update your information instantly without physical documents</p>
                  </div>
                </div>
              </div>
            </>
          )}

          {!userData.isVerified && userData.age && (
            <div className="verification-failed">
              <h3>Verification Failed</h3>
              <p>You must be 18 years or older to use Google Wallet for age verification.</p>
              <button onClick={handleReset} className="try-again-btn">
                Try Again
              </button>
            </div>
          )}
        </main>

        <footer className="app-footer">
          <p>© 2024 Age Verification System. This is a demo application.</p>
          <p className="disclaimer">
            We use secure protocols to protect your data. No personal information is stored permanently.
          </p>
          <div className="compliance-badges">
            <span className="badge">GDPR Compliant</span>
            <span className="badge">Privacy First</span>
            <span className="badge">Secure API</span>
          </div>
        </footer>
      </div>
    </div>
  );
}

export default App;