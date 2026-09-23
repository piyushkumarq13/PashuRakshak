import { useState, useEffect, useCallback } from 'react'
import { onAuthStateChanged, signOut } from 'firebase/auth'
import { auth } from './firebase'
import { getMe } from './api'
import PhoneAuth from './components/PhoneAuth'
import RegistrationForm from './components/RegistrationForm'
import ProfilePage from './components/ProfilePage'

export default function App() {
  const [user, setUser] = useState(null)
  const [idToken, setIdToken] = useState(null)
  const [stage, setStage] = useState('auth') // auth | checking | register | profile
  const [meData, setMeData] = useState(null)

  useEffect(() => {
    const unsub = onAuthStateChanged(auth, async (fbUser) => {
      if (fbUser) {
        setUser(fbUser)
        const token = await fbUser.getIdToken()
        setIdToken(token)
        setStage('checking')
        const { status, data } = await getMe(token)
        if (status === 200) {
          setMeData(data.user)
          setStage('profile')
        } else if (status === 404) {
          setStage('register')
        } else {
          setStage('auth')
          await signOut(auth)
        }
      } else {
        setUser(null)
        setIdToken(null)
        setMeData(null)
        setStage('auth')
      }
    })
    return unsub
  }, [])

  const refreshMe = useCallback(async (token) => {
    const { status, data } = await getMe(token)
    if (status === 200) {
      setMeData(data.user)
      setStage('profile')
    } else if (status === 404) {
      setStage('register')
    }
  }, [])

  const handleSignedIn = useCallback(
    async (token) => {
      setIdToken(token)
      setStage('checking')
      await refreshMe(token)
    },
    [refreshMe],
  )

  const handleSignOut = useCallback(async () => {
    await signOut(auth)
  }, [])

  return (
    <div className="app">
      <div id="recaptcha-container" />
      <header className="header">
        <h1>PashuRakshak — Vet Registration</h1>
        {user && (
          <button className="btn-link" onClick={handleSignOut}>
            Sign out
          </button>
        )}
      </header>

      <main className="main">
        {stage === 'auth' && <PhoneAuth onSignedIn={handleSignedIn} />}

        {stage === 'checking' && (
          <div className="card center-text">
            <p>Checking your account…</p>
          </div>
        )}

        {stage === 'register' && idToken && user && (
          <RegistrationForm
            idToken={idToken}
            phone={user.phoneNumber || ''}
            onDone={() => refreshMe(idToken)}
          />
        )}

        {stage === 'profile' && idToken && meData && (
          <ProfilePage
            idToken={idToken}
            me={meData}
            onMeUpdated={(updated) => setMeData(updated)}
            onRefresh={() => refreshMe(idToken)}
          />
        )}
      </main>
    </div>
  )
}
