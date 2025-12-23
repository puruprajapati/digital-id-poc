import React from 'react';
import { QRCodeSVG } from 'qrcode.react';  // Changed from QRCode to QRCodeSVG
import './PassPreview.css';

const PassPreview = ({ userData }) => {
  const currentDate = new Date().toLocaleDateString('en-US', {
    weekday: 'short',
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  });

  const expirationDate = new Date(Date.now() + 365 * 24 * 60 * 60 * 1000)
    .toLocaleDateString('en-US', { month: 'short', year: 'numeric' });

  return (
    <div className="pass-preview">
      <div className="pass-header">
        <div className="pass-logo">
          <div className="logo-circle">AV</div>
          <div className="logo-text">
            <span className="logo-main">Age</span>
            <span className="logo-sub">Verified</span>
          </div>
        </div>
        <div className="pass-badge">DIGITAL</div>
      </div>

      <div className="pass-content">
        <div className="pass-user-info">
          <div className="user-avatar">
            {userData.name.charAt(0).toUpperCase()}
          </div>
          <div className="user-details">
            <h3 className="user-name">{userData.name}</h3>
            <p className="user-email">{userData.email}</p>
          </div>
        </div>

        <div className="pass-details">
          <div className="detail-row">
            <div className="detail-label">Age</div>
            <div className="detail-value">{userData.age} years</div>
          </div>
          <div className="detail-row">
            <div className="detail-label">Status</div>
            <div className="detail-value verified">
              <span className="status-icon">✓</span> Verified
            </div>
          </div>
          <div className="detail-row">
            <div className="detail-label">Issued</div>
            <div className="detail-value">{currentDate}</div>
          </div>
          <div className="detail-row">
            <div className="detail-label">Expires</div>
            <div className="detail-value">{expirationDate}</div>
          </div>
        </div>

        <div className="pass-qr">
          <div className="qr-container">
            <QRCodeSVG  // Changed from QRCode to QRCodeSVG
              value={userData.verificationId}
              size={160}
              level="H"
              includeMargin={true}
              bgColor="#ffffff"
              fgColor="#4285F4"
            />
          </div>
          <p className="qr-label">Scan to verify</p>
        </div>

        <div className="pass-footer">
          <div className="pass-id">ID: {userData.verificationId}</div>
          <div className="wallet-branding">
            <img
              src="https://www.gstatic.com/images/branding/product/1x/pass_64dp.png"
              alt="Google Wallet"
              className="wallet-small-logo"
            />
            <span>Google Wallet</span>
          </div>
        </div>
      </div>

      <div className="pass-back">
        <div className="back-header">
          <h4>Terms & Conditions</h4>
        </div>
        <div className="back-content">
          <p>This digital pass verifies age only. Must be presented with a valid photo ID upon request.</p>
          <p>Valid until expiration date shown. Non-transferable. Subject to verification.</p>
          <div className="contact-info">
            <p><strong>Issuer:</strong> Digital Age Verification System</p>
            <p><strong>Contact:</strong> support@ageverify.com</p>
          </div>
        </div>
        <div className="barcode">
          <div className="barcode-line"></div>
          <div className="barcode-line short"></div>
          <div className="barcode-line"></div>
          <div className="barcode-line short"></div>
          <div className="barcode-line"></div>
          <div className="barcode-line short"></div>
          <div className="barcode-line"></div>
        </div>
      </div>
    </div>
  );
};

export default PassPreview;