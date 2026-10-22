import { useState, useEffect, useCallback } from 'react'
import { AUTH_EXPIRED_EVENT, getToken, setToken } from './api'
import LoginScreen from './components/LoginScreen'
import Dashboard from './components/Dashboard'
import Icon, { BrandMark } from './components/Icon'

function scopeLabel(district) {
  if (!district) return 'District scope'
  return district === '*' ? 'All districts' : district
}

export default function App() {
  const [magistrate, setMagistrate] = useState(null)
  const [checking, setChecking] = useState(true)

  const restore = useCallback(() => {
    const token = getToken()
    if (!token) {
      setChecking(false)
      return
    }
    try {
      const payloadPart = token.split('.')[0]
      if (payloadPart) {
        const base64 = payloadPart.replace(/-/g, '+').replace(/_/g, '/')
        const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
        const json = JSON.parse(atob(padded))
        if (json.role === 'gov' && json.exp && json.exp > Date.now()) {
          setMagistrate({ email: json.email, district: json.district, name: null })
          setChecking(false)
          return
        }
      }
    } catch {
      // Invalid local token; clear it below.
    }
    setToken(null)
    setChecking(false)
  }, [])

  useEffect(() => {
    restore()
  }, [restore])

  useEffect(() => {
    function onAuthExpired() {
      setMagistrate(null)
      setChecking(false)
    }
    window.addEventListener(AUTH_EXPIRED_EVENT, onAuthExpired)
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, onAuthExpired)
  }, [])

  function handleLoggedIn(data) {
    setToken(data.token)
    setMagistrate(data.magistrate)
  }

  function handleSignOut() {
    setToken(null)
    setMagistrate(null)
  }

  if (checking) {
    return (
      <div className="app-shell">
        <div className="loading-screen">
          <BrandMark size={54} />
          <div>
            <strong>PashuRakshak</strong>
            <span>Restoring your secure session...</span>
          </div>
          <div className="loading-bar" aria-hidden="true" />
        </div>
      </div>
    )
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="/" aria-label="PashuRakshak Government Portal home">
          <BrandMark />
          <div className="brand-copy">
            <span className="brand-name">PashuRakshak</span>
            <span className="brand-divider" aria-hidden="true" />
            <span className="brand-product">Government Portal</span>
          </div>
        </a>

        {magistrate ? (
          <div className="topbar-actions">
            <span className="scope-pill">
              <Icon name="shield" size={15} />
              {scopeLabel(magistrate.district)}
            </span>
            <div className="account">
              <span className="avatar" aria-hidden="true">
                {(magistrate.name || 'DM').slice(0, 2).toUpperCase()}
              </span>
              <span className="account-copy">
                <strong>{magistrate.name || 'District Magistrate'}</strong>
                <span>{magistrate.email}</span>
              </span>
            </div>
            <button className="icon-button" onClick={handleSignOut} aria-label="Sign out" title="Sign out">
              <Icon name="logout" size={18} />
            </button>
          </div>
        ) : (
          <div className="topbar-actions">
            <span className="topbar-note">
              <span className="status-dot" />
              Secure district access
            </span>
          </div>
        )}
      </header>

      <main className="main">
        {magistrate ? (
          <Dashboard magistrate={magistrate} onSignOut={handleSignOut} />
        ) : (
          <div className="auth-layout">
            <section className="auth-hero">
              <div className="auth-hero-glow" aria-hidden="true" />
              <div className="hero-content">
                <span className="hero-kicker">
                  <Icon name="shield" size={15} />
                  District Magistrate Console
                </span>
                <h1>Livestock health intelligence for your district.</h1>
                <p className="hero-lead">
                  Review veterinarian applications, follow symptom reports, spot outbreak
                  clusters, and act on government alerts from one secure workspace.
                </p>

                <div className="hero-stats">
                  <div>
                    <strong>7 days</strong>
                    <span>Outbreak window</span>
                  </div>
                  <div>
                    <strong>5 km</strong>
                    <span>Cluster radius</span>
                  </div>
                  <div>
                    <strong>OTP</strong>
                    <span>Passwordless access</span>
                  </div>
                </div>
              </div>

              <div className="hero-features">
                <div className="hero-feature">
                  <span className="feature-icon">
                    <Icon name="applications" size={18} />
                  </span>
                  <div>
                    <strong>Review with context</strong>
                    <span>Approve or reject vet applications with a clear audit note.</span>
                  </div>
                </div>
                <div className="hero-feature">
                  <span className="feature-icon">
                    <Icon name="clusters" size={18} />
                  </span>
                  <div>
                    <strong>See risks earlier</strong>
                    <span>AI-scored reports and live clusters surface what needs attention.</span>
                  </div>
                </div>
              </div>
            </section>

            <section className="auth-panel">
              <LoginScreen onLoggedIn={handleLoggedIn} />
              <p className="security-note">
                <Icon name="shield" size={14} />
                Protected by email OTP. Sessions are signed and expire automatically.
              </p>
            </section>
          </div>
        )}
      </main>

      <footer className="site-footer">
        <span>PashuRakshak · Livestock Health Network</span>
        <span>District administration workspace</span>
      </footer>
    </div>
  )
}
