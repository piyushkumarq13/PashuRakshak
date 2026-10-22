import { useState, useEffect, useCallback } from 'react'
import { AUTH_EXPIRED_EVENT, getMe, getMyApplication, getToken, setToken, vetSetupPin } from './api'
import RegisterStart from './components/RegisterStart'
import OtpVerify from './components/OtpVerify'
import SetCredential from './components/SetCredential'
import SignInForm from './components/SignInForm'
import ForgotStart from './components/ForgotStart'
import ResetCredential from './components/ResetCredential'
import ApplicationForm from './components/ApplicationForm'
import StatusPage from './components/StatusPage'
import Icon, { BrandMark } from './components/Icon'

/**
 * Stage machine (all state lives server-side; only the session token is
 * kept in localStorage):
 *
 *   landing -> register -> otp -> set-credential -> application -> status
 *        |                     |
 *        -> sign-in ----------> status (pending/approved/rejected)
 *        |                     |         `-> resubmit -> application
 *        `-> forgot -> otp(reset) -> reset-credential -> status/application
 */
export default function App() {
  const [stage, setStage] = useState('checking')
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

  useEffect(() => {
    function onAuthExpired() {
      setUser(null)
      setApplication(null)
      setHeaderAuthed(false)
      setFlow({ email: null, phone: null, purpose: null, verifyToken: null })
      setStage('landing')
    }
    window.addEventListener(AUTH_EXPIRED_EVENT, onAuthExpired)
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, onAuthExpired)
  }, [])

  const handleAuthResponse = useCallback(async (data) => {
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
    const appRes = await getMyApplication()
    const app = appRes.status === 200 ? appRes.data.application : null
    setApplication(app)
    setStage(app ? 'status' : 'application')
  }, [])

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
        setSetupError('Verification expired. Please start again.')
        setTimeout(() => setStage('register'), 1500)
        return
      }
      setSetupError(data.message || 'Could not set up your account.')
    }
  }

  function handleSubmitted(app) {
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

  const inFlow = ['register', 'otp', 'set-credential', 'forgot-start', 'reset-credential'].includes(
    stage,
  )
  const authedName = user?.name || application?.full_name || ''
  const initials = authedName
    ? authedName
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 2)
        .map((part) => part[0])
        .join('')
        .toUpperCase()
    : 'VT'

  const flowCopy = {
    register: {
      kicker: 'Step 1 of 3',
      title: 'Start your registration',
      text: 'Verify your email, secure your account, then tell us about your practice.',
    },
    otp: {
      kicker: 'Step 2 of 3',
      title: 'Verify your email',
      text: 'A six-digit code keeps your account and application secure.',
    },
    'set-credential': {
      kicker: 'Step 3 of 3',
      title: 'Secure your account',
      text: 'Choose a PIN or password you will use in the PashuRakshak Vet App.',
    },
    'forgot-start': {
      kicker: 'Account recovery',
      title: 'Regain access',
      text: 'We will send a reset code to the email registered with your application.',
    },
    'reset-credential': {
      kicker: 'Account recovery',
      title: 'Set a new credential',
      text: 'Your code is verified. Choose a fresh PIN or password to continue.',
    },
  }
  const currentFlow = flowCopy[stage]

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">
          <BrandMark size={40} />
          <div className="brand-copy">
            <span className="brand-name">PashuRakshak</span>
            <span className="brand-divider" aria-hidden="true" />
            <span className="brand-product">Veterinary Network</span>
          </div>
        </div>

        <div className="topbar-actions">
          {headerAuthed ? (
            <>
              <div className="account">
                <span className="avatar">{initials}</span>
                <span className="account-copy">
                  <strong>{authedName || 'Veterinarian'}</strong>
                  <span>{user?.phone || application?.phone || 'Verified account'}</span>
                </span>
              </div>
              <button className="icon-button" onClick={handleSignOut} aria-label="Sign out">
                <Icon name="logout" size={17} />
              </button>
            </>
          ) : (
            <span className="topbar-note">
              <span className="status-dot" /> Registration portal
            </span>
          )}
        </div>
      </header>

      <main className="main">
        {stage === 'checking' && (
          <div className="loading-screen">
            <BrandMark size={54} />
            <div>
              <strong>Opening your workspace</strong>
              <span>Checking your secure session...</span>
            </div>
            <span className="loading-bar" aria-hidden="true" />
          </div>
        )}

        {stage === 'landing' && (
          <div className="auth-layout">
            <section className="auth-hero vet-hero">
              <span className="hero-glow" aria-hidden="true" />
              <div className="hero-content">
                <span className="hero-kicker">
                  <Icon name="shield" size={14} />
                  Verified veterinary network
                </span>
                <h1>Join the livestock health network.</h1>
                <p className="hero-lead">
                  Apply once with your professional credentials, choose the communities you serve,
                  and get reviewed by your District Magistrate.
                </p>

                <div className="hero-stats">
                  <div>
                    <strong>Email OTP</strong>
                    <span>No paperwork queues</span>
                  </div>
                  <div>
                    <strong>District</strong>
                    <span>Local review</span>
                  </div>
                  <div>
                    <strong>Live</strong>
                    <span>Application updates</span>
                  </div>
                </div>

                <ul className="hero-checks">
                  <li>
                    <span className="hero-check-icon">
                      <Icon name="check" size={13} />
                    </span>
                    <div>
                      <strong>Professional review</strong>
                      <span>License and qualifications checked locally.</span>
                    </div>
                  </li>
                  <li>
                    <span className="hero-check-icon">
                      <Icon name="check" size={13} />
                    </span>
                    <div>
                      <strong>Clear next steps</strong>
                      <span>See your status, reviewer notes, and coverage areas.</span>
                    </div>
                  </li>
                  <li>
                    <span className="hero-check-icon">
                      <Icon name="check" size={13} />
                    </span>
                    <div>
                      <strong>Ready for the field</strong>
                      <span>Once approved, sign in to the Vet App and start triaging.</span>
                    </div>
                  </li>
                </ul>
              </div>

              <div className="hero-footnote">
                <Icon name="lock" size={14} />
                Your information is used only for verification and district routing.
              </div>
            </section>

            <div className="auth-panel">
              <SignInForm
                onSignedIn={handleAuthResponse}
                onGoRegister={() => setStage('register')}
                onForgot={() => setStage('forgot-start')}
              />
            </div>
          </div>
        )}

        {inFlow && (
          <div className="flow-layout">
            <aside className="flow-aside">
              <span className="eyebrow">{currentFlow?.kicker}</span>
              <h2>{currentFlow?.title}</h2>
              <p>{currentFlow?.text}</p>
              <ul className="flow-checks">
                <li>
                  <Icon name="mail" size={15} />
                  Email verification
                </li>
                <li>
                  <Icon name="lock" size={15} />
                  PIN or password
                </li>
                <li>
                  <Icon name="file" size={15} />
                  Practice application
                </li>
              </ul>
              <div className="flow-aside-note">
                <Icon name="info" size={15} />
                <span>Applications are reviewed by the District Magistrate for your pincode.</span>
              </div>
            </aside>
            <div className="flow-panel">
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
                  onBack={() =>
                    setStage(flow.purpose === 'vet_reset_pin' ? 'forgot-start' : 'register')
                  }
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
            </div>
          </div>
        )}

        {stage === 'application' && (
          <div className="workspace">
            <ApplicationForm existing={application} onSubmitted={handleSubmitted} />
          </div>
        )}

        {stage === 'status' && application && (
          <div className="workspace">
            <StatusPage
              application={application}
              refreshing={refreshing}
              onRefresh={refreshStatus}
              onResubmit={() => setStage('application')}
              onSignOut={handleSignOut}
            />
          </div>
        )}

        {stage === 'status' && !application && (
          <div className="workspace">
            <div className="card empty-state">
              <span className="empty-icon">
                <Icon name="file" size={24} />
              </span>
              <h2>No application found</h2>
              <p>Your account is active, but we could not find an application yet.</p>
              <button className="btn btn-primary" onClick={() => setStage('application')}>
                Complete application
              </button>
            </div>
          </div>
        )}

        {stage === 'sign-in' && (
          <div className="auth-layout auth-layout-compact">
            <div className="auth-panel">
              <SignInForm
                onSignedIn={handleAuthResponse}
                onGoRegister={() => setStage('register')}
                onForgot={() => setStage('forgot-start')}
              />
            </div>
          </div>
        )}
      </main>

      <footer className="site-footer">
        <span>PashuRakshak Veterinary Network</span>
        <span>Secure registration for verified practitioners</span>
      </footer>
    </div>
  )
}
