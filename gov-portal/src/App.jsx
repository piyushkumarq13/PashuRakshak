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
        <div>
          <h1>PashuRakshak — Government Portal</h1>
          {magistrate && (
            <p className="header-sub">
              {magistrate.name ? `${magistrate.name} · ` : ''}
              District Magistrate, {magistrate.district}
            </p>
          )}
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
          <LoginScreen onLoggedIn={handleLoggedIn} />
        )}
      </main>
    </div>
  )
}
