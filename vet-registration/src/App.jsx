import { useState, useEffect, useCallback } from 'react'
import { getMe, getMyApplication, getToken, setToken, vetSetupPin } from './api'
import RegisterStart from './components/RegisterStart'
import OtpVerify from './components/OtpVerify'
import SetCredential from './components/SetCredential'
import SignInForm from './components/SignInForm'
import ForgotStart from './components/ForgotStart'
import ResetCredential from './components/ResetCredential'
import ApplicationForm from './components/ApplicationForm'
import StatusPage from './components/StatusPage'

/**
 * Stage machine (all state lives server-side; only the session token is
 * kept in localStorage):
 *
 *   landing ── register ──▶ otp ──▶ set-credential ──▶ application ──▶ status
 *        │                     ▲
 *        ├── sign-in ──────────┼──▶ status (pending/approved/rejected)
 *        │                     │        └── resubmit ──▶ application
 *        └── forgot ──▶ otp(reset) ──▶ reset-credential ──▶ status/application
 */
export default function App() {
  const [stage, setStage] = useState('checking') // checking|landing|register|otp|set-credential|application|status|sign-in|forgot-start|forgot-otp|reset-credential
  const [flow, setFlow] = useState({ email: null, phone: null, purpose: null, verifyToken: null })
  const [user, setUser] = useState(null)
  const [application, setApplication] = useState(null)
  const [setupSubmitting, setSetupSubmitting] = useState(false)
  const [setupError, setSetupError] = useState('')
  const [refreshing, setRefreshing] = useState(false)
  const [headerAuthed, setHeaderAuthed] = useState(false)

  const loadSession = useCallback(async () => {
    if (!getToken()) {
      setStage('landing')
      setHeaderAuthed(false)
      return
    }
    setStage('checking')
    const appRes = await getMyApplication()
    if (appRes.status === 401) {
      setToken(null)
      setStage('landing')
      setHeaderAuthed(false)
      return
    }
    setHeaderAuthed(true)
    const meRes = await getMe()
    if (meRes.status === 200) setUser(meRes.data.user)

    const app = appRes.status === 200 ? appRes.data.application : null
    setApplication(app)
    setStage(app ? 'status' : 'application')
  }, [])

  useEffect(() => {
    loadSession()
  }, [loadSession])

  const handleAuthResponse = useCallback(
    async (data) => {
      // data: { token?, user?, application?, applicationStatus? }
      if (data.token) setToken(data.token)
      setHeaderAuthed(true)
      if (data.user) setUser(data.user)
      if (data.user === undefined && getToken()) {
        const meRes = await getMe()
        if (meRes.status === 200) setUser(meRes.data.user)
      }
      const status = data.applicationStatus
      if (status === 'none' || (!status && !data.application)) {
        setApplication(null)
        setStage('application')
        return
      }
      if (data.application) {
        setApplication(data.application)
        setStage('status')
        return
      }
      // Signed in but application not included — fetch it.
      const appRes = await getMyApplication()
      const app = appRes.status === 200 ? appRes.data.application : null
      setApplication(app)
      setStage(app ? 'status' : 'application')
    },
    [],
  )

  async function handleSetupPin(credential, pinType) {
    setSetupSubmitting(true)
    setSetupError('')
    const { status, data } = await vetSetupPin(flow.verifyToken, flow.phone, credential, pinType)
    setSetupSubmitting(false)
    if (status === 200 && data.token) {
      setToken(data.token)
      setUser(data.user)
      setHeaderAuthed(true)
      setStage('application')
    } else {
      if (status === 401) {
        setSetupError('Verification expired — please start again.')
        setTimeout(() => setStage('register'), 1500)
        return
      }
      setSetupError(data.message || 'Could not set up your account.')
    }
  }

  async function handleSubmitted(app) {
    setApplication(app)
    setStage('status')
  }

  async function refreshStatus() {
    setRefreshing(true)
    const appRes = await getMyApplication()
    if (appRes.status === 401) {
      setToken(null)
      setHeaderAuthed(false)
      setStage('landing')
    } else {
      setApplication(appRes.status === 200 ? appRes.data.application : null)
    }
    setRefreshing(false)
  }

  function handleSignOut() {
    setToken(null)
    setUser(null)
    setApplication(null)
    setHeaderAuthed(false)
    setFlow({ email: null, phone: null, purpose: null, verifyToken: null })
    setStage('landing')
  }

  return (
    <div className="app">
      <header className="header">
        <h1>PashuRakshak — Vet Registration</h1>
        {headerAuthed && (
          <button className="btn-link" onClick={handleSignOut}>
            Sign out
          </button>
        )}
      </header>

      <main className="main">
        {stage === 'checking' && (
          <div className="card center-text">
            <p>Loading…</p>
          </div>
        )}

        {stage === 'landing' && (
          <>
            <SignInForm
              onSignedIn={handleAuthResponse}
              onGoRegister={() => setStage('register')}
              onForgot={() => setStage('forgot-start')}
            />
            <div className="landing-alt">
              <p className="muted">New vet? Register to apply for approval.</p>
              <button className="btn-secondary" onClick={() => setStage('register')}>
                Create an account
              </button>
            </div>
          </>
        )}

        {stage === 'register' && (
          <RegisterStart
            onSubmit={(phone, email) => {
              setFlow({ phone, email, purpose: 'vet_register', verifyToken: null })
              setStage('otp')
            }}
            onBack={() => setStage('landing')}
          />
        )}

        {stage === 'otp' && (
          <OtpVerify
            email={flow.email}
            phone={flow.phone}
            purpose={flow.purpose}
            heading={
              flow.purpose === 'vet_reset_pin' ? 'Enter the reset code' : 'Verify your email'
            }
            onVerified={(verifyToken) => {
              if (flow.purpose === 'vet_reset_pin') {
                setFlow((f) => ({ ...f, verifyToken }))
                setStage('reset-credential')
              } else {
                setFlow((f) => ({ ...f, verifyToken }))
                setStage('set-credential')
              }
            }}
            onBack={() => setStage(flow.purpose === 'vet_reset_pin' ? 'forgot-start' : 'register')}
          />
        )}

        {stage === 'set-credential' && (
          <SetCredential
            phone={flow.phone}
            verifyToken={flow.verifyToken}
            onSubmit={handleSetupPin}
            onBack={() => setStage('otp')}
            submitting={setupSubmitting}
            error={setupError}
          />
        )}

        {stage === 'forgot-start' && (
          <ForgotStart
            onStart={(email) => {
              setFlow({ email, phone: null, purpose: 'vet_reset_pin', verifyToken: null })
              setStage('otp')
            }}
            onBack={() => setStage('landing')}
          />
        )}

        {stage === 'reset-credential' && (
          <ResetCredential
            verifyToken={flow.verifyToken}
            onDone={handleAuthResponse}
            onBack={() => setStage('forgot-start')}
          />
        )}

        {stage === 'application' && (
          <ApplicationForm existing={null} onSubmitted={handleSubmitted} />
        )}

        {stage === 'status' && application && (
          <StatusPage
            application={application}
            refreshing={refreshing}
            onRefresh={refreshStatus}
            onResubmit={() => setStage('application')}
            onSignOut={handleSignOut}
          />
        )}

        {stage === 'status' && !application && (
          <div className="card center-text">
            <p>No application found.</p>
            <button onClick={() => setStage('application')}>Complete application</button>
          </div>
        )}

        {stage === 'sign-in' && (
          <SignInForm
            onSignedIn={handleAuthResponse}
            onGoRegister={() => setStage('register')}
            onForgot={() => setStage('forgot-start')}
          />
        )}
      </main>
    </div>
  )
}
