const BASE = import.meta.env.VITE_API_BASE_URL || 'https://pashurakshak-z8lk.onrender.com'
const APP_KEY = import.meta.env.VITE_APP_API_KEY || '3e30562f72b83d703458aa230e9c66d12147fc31ff9449a5f9c90827a10eea52'

const TOKEN_KEY = 'vet_registration_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

async function request(path, { method = 'GET', body, auth = true } = {}) {
  const headers = { 'X-App-Key': APP_KEY }
  const token = getToken()
  if (auth && token) headers['Authorization'] = `Bearer ${token}`
  if (body) headers['Content-Type'] = 'application/json'

  try {
    const res = await fetch(`${BASE}${path}`, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
    })
    const data = await res.json().catch(() => ({}))
    return { status: res.status, data }
  } catch {
    return {
      status: 0,
      data: {
        error: 'network_error',
        message: 'Could not reach the server. Check your connection and try again.',
      },
    }
  }
}

/* ── Auth (email OTP + PIN/password — no Firebase) ── */

export function sendOtp(email, purpose, phone) {
  return request('/api/v1/auth/otp/send', {
    method: 'POST',
    auth: false,
    body: { email, purpose, ...(phone ? { phone } : {}) },
  })
}

export function verifyOtp(email, purpose, code) {
  return request('/api/v1/auth/otp/verify', {
    method: 'POST',
    auth: false,
    body: { email, purpose, code },
  })
}

export function vetSetupPin(verifyToken, phone, credential, pinType) {
  return request('/api/v1/auth/vet/setup-pin', {
    method: 'POST',
    auth: false,
    body: { verifyToken, phone, pin: credential, pinType },
  })
}

export function vetLogin(phone, credential) {
  return request('/api/v1/auth/vet/login', {
    method: 'POST',
    auth: false,
    body: { phone, credential, client: 'web' },
  })
}

export function vetResetPin(verifyToken, credential, pinType) {
  return request('/api/v1/auth/vet/reset-pin', {
    method: 'POST',
    auth: false,
    body: { verifyToken, pin: credential, pinType },
  })
}

/* ── Vet application ── */

export function getMyApplication() {
  return request('/api/v1/pashu-health/vet-applications/me')
}

export function submitApplication(payload) {
  return request('/api/v1/pashu-health/vet-applications', {
    method: 'POST',
    body: payload,
  })
}

/* ── Core user ── */

export function getMe() {
  return request('/api/v1/core/users/me')
}

export function updateMe(payload) {
  return request('/api/v1/core/users/me', { method: 'PUT', body: payload })
}

/* ── Pincode areas ── */

export function getPincodeAreas(code) {
  return request(`/api/v1/core/pincode/${code}`, { auth: false })
}
