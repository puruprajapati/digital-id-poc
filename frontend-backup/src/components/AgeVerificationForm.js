import React, { useState } from 'react';
import './AgeVerificationForm.css';

const AgeVerificationForm = ({ onVerify, onReset }) => {
  const [formData, setFormData] = useState({
    name: '',
    age: '',
    email: '',
    acceptTerms: false
  });
  const [errors, setErrors] = useState({});

  const validateForm = () => {
    const newErrors = {};

    if (!formData.name.trim()) {
      newErrors.name = 'Name is required';
    }

    if (!formData.age) {
      newErrors.age = 'Age is required';
    } else if (formData.age < 1 || formData.age > 120) {
      newErrors.age = 'Please enter a valid age';
    }

    if (!formData.email) {
      newErrors.email = 'Email is required';
    } else if (!/\S+@\S+\.\S+/.test(formData.email)) {
      newErrors.email = 'Email is invalid';
    }

    if (!formData.acceptTerms) {
      newErrors.acceptTerms = 'You must accept the terms';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = (e) => {
    e.preventDefault();

    if (validateForm()) {
      const ageNum = parseInt(formData.age);
      const isVerified = ageNum >= 18;

      onVerify({
        name: formData.name,
        age: ageNum,
        email: formData.email,
        isVerified: isVerified
      });
    }
  };

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData({
      ...formData,
      [name]: type === 'checkbox' ? checked : value
    });

    // Clear error for this field
    if (errors[name]) {
      setErrors({
        ...errors,
        [name]: ''
      });
    }
  };

  const handleReset = () => {
    setFormData({
      name: '',
      age: '',
      email: '',
      acceptTerms: false
    });
    setErrors({});
    onReset();
  };

  return (
    <div className="verification-form">
      <div className="form-header">
        <h2>Verify Your Age</h2>
        <p>Enter your details to create a digital age verification pass</p>
      </div>

      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label htmlFor="name">
            Full Name *
            {errors.name && <span className="error-text"> - {errors.name}</span>}
          </label>
          <input
            type="text"
            id="name"
            name="name"
            value={formData.name}
            onChange={handleChange}
            placeholder="Enter your full name"
            className={errors.name ? 'error' : ''}
          />
        </div>

        <div className="form-row">
          <div className="form-group">
            <label htmlFor="age">
              Age *
              {errors.age && <span className="error-text"> - {errors.age}</span>}
            </label>
            <input
              type="number"
              id="age"
              name="age"
              value={formData.age}
              onChange={handleChange}
              placeholder="Enter age"
              min="1"
              max="120"
              className={errors.age ? 'error' : ''}
            />
          </div>

          <div className="form-group">
            <label htmlFor="email">
              Email *
              {errors.email && <span className="error-text"> - {errors.email}</span>}
            </label>
            <input
              type="email"
              id="email"
              name="email"
              value={formData.email}
              onChange={handleChange}
              placeholder="your@email.com"
              className={errors.email ? 'error' : ''}
            />
          </div>
        </div>

        <div className="form-group terms-group">
          <label className="checkbox-label">
            <input
              type="checkbox"
              name="acceptTerms"
              checked={formData.acceptTerms}
              onChange={handleChange}
              className={errors.acceptTerms ? 'error' : ''}
            />
            <span>
              I accept the{' '}
              <a href="#terms" className="terms-link">
                Terms of Service
              </a>{' '}
              and{' '}
              <a href="#privacy" className="terms-link">
                Privacy Policy
              </a>
            </span>
          </label>
          {errors.acceptTerms && (
            <p className="error-text">{errors.acceptTerms}</p>
          )}
        </div>

        <div className="form-actions">
          <button type="submit" className="verify-btn">
            Verify Age
          </button>
          <button
            type="button"
            onClick={handleReset}
            className="reset-btn"
          >
            Clear Form
          </button>
        </div>

        <div className="form-info">
          <p><strong>Note:</strong> Your data is used only to generate the digital pass.
            We don't store personal information permanently.</p>
        </div>
      </form>
    </div>
  );
};

export default AgeVerificationForm;