const BASE = import.meta.env.VITE_API_BASE_URL || 'https://pashurakshak-z8lk.onrender.com'
const APP_KEY = import.meta.env.VITE_APP_API_KEY || '46108593bdff58a2952c1ed83649a6e24f8c7b37405aeee9744c035189e25db9'

const TOKEN_KEY = 'gov_portal_token'

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

  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  })

  const data = await res.json().catch(() => ({}))
  return { status: res.status, data }
}

/* ── Auth (magistrate: email → OTP → session) ── */

export function govRequestOtp(email) {
  return request('/api/v1/auth/gov/request-otp', {
    method: 'POST',
    auth: false,
    body: { email },
  })
}

export function govVerifyOtp(email, code) {
  return request('/api/v1/auth/gov/verify', {
    method: 'POST',
    auth: false,
    body: { email, code },
  })
}

/* ── Dashboard ── */

export function getOverview() {
  return request('/api/v1/gov/overview')
}

export function getVetApplications(status = 'pending') {
  return request(`/api/v1/gov/vet-applications?status=${encodeURIComponent(status)}`)
}

export function reviewApplication(id, action, note) {
  return request(`/api/v1/gov/vet-applications/${id}/${action}`, {
    method: 'POST',
    body: { note },
  })
}

export function getReports() {
  return request('/api/v1/gov/reports')
}

export function getClusters() {
  return request('/api/v1/gov/clusters')
}

export function getGovAlerts() {
  return request('/api/v1/gov/gov-alerts')
}

export function acknowledgeAlert(id) {
  return request(`/api/v1/gov/gov-alerts/${id}/acknowledge`, { method: 'POST' })
}

export function getUsers() {
  return request('/api/v1/gov/users')
}
