import { useState, useEffect, useCallback } from 'react'
import { getToken, setToken } from './api'
import LoginScreen from './components/LoginScreen'
import Dashboard from './components/Dashboard'

/**
 * Government portal for District Magistrates.
 *
 * Login: email → OTP → session token (no password).
 * Dashboard: overview, vet applications (approve/reject, district-scoped),
 * reports, clusters, gov alerts (acknowledge), users.
 */
export default function App() {
  const [magistrate, setMagistrate] = useState(null)
  const [checking, setChecking] = useState(true)

  const restore = useCallback(() => {
    const token = getToken()
    if (!token) {
      setChecking(false)
      return
    }
    // Try to decode the JWT-ish payload (it's base64url JSON) to restore
    // basic info; the backend re-validates on every API call anyway.
    try {
      const payloadPart = token.split('.')[0]
      if (payloadPart) {
        const json = JSON.parse(atob(payloadPart.replace(/-/g, '+').replace(/_/g, '/')))
        if (json.role === 'gov' && json.exp && json.exp > Date.now()) {
          setMagistrate({ email: json.email, district: json.district, name: null })
          setChecking(false)
          return
        }
      }
    } catch {
      // fall through to logout
    }
    setToken(null)
    setChecking(false)
  }, [])

  useEffect(() => {
    restore()
  }, [restore])

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
      <div className="app">
        <div className="card center-text">
          <p>Loading…</p>
        </div>
      </div>
    )
  }

  return (
    <div className="app">
      <header className="header">
        <div className="brand">
          <div className="brand-mark" aria-hidden="true">🛡️</div>
          <div className="brand-text">
            <h1>
              PashuRakshak <span>Government Portal</span>
            </h1>
            {magistrate && (
              <p className="header-sub">
                {magistrate.name ? `${magistrate.name} · ` : ''}
                District Magistrate, {magistrate.district}
              </p>
            )}
          </div>
        </div>
        {magistrate && (
          <button className="btn-secondary" onClick={handleSignOut}>
            Sign out
          </button>
        )}
      </header>

      <main className="main">
        {magistrate ? (
          <Dashboard magistrate={magistrate} />
        ) : (
          <div className="auth-shell">
            <aside className="auth-brand">
              <div>
                <p className="auth-brand-kicker">District oversight</p>
                <h2>Livestock health, one dashboard.</h2>
                <p className="auth-brand-lead">
                  Review vet applications, track symptom reports, monitor outbreak clusters and
                  acknowledge government alerts — scoped to your district.
                </p>
              </div>
              <ul className="auth-brand-points">
                <li>
                  <span className="tick" aria-hidden="true">✓</span>
                  Approve or reject veterinarian applications with review notes
                </li>
                <li>
                  <span className="tick" aria-hidden="true">✓</span>
                  Live symptom reports with AI risk scoring and status filters
                </li>
                <li>
                  <span className="tick" aria-hidden="true">✓</span>
                  Outbreak clusters detected on a 7-day / 5 km rule
                </li>
                <li>
                  <span className="tick" aria-hidden="true">✓</span>
                  Passwordless sign-in — email OTP only
                </li>
              </ul>
            </aside>
            <div className="auth-form-side">
              <LoginScreen onLoggedIn={handleLoggedIn} />
            </div>
          </div>
        )}
      </main>
    </div>
  )
}
