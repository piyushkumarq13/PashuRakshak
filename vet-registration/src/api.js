const BASE = import.meta.env.VITE_API_BASE_URL
const APP_KEY = import.meta.env.VITE_APP_API_KEY

async function request(path, { method = 'GET', body, idToken } = {}) {
  const headers = { 'X-App-Key': APP_KEY }
  if (idToken) headers['Authorization'] = `Bearer ${idToken}`
  if (body) headers['Content-Type'] = 'application/json'

  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  })

  const data = await res.json().catch(() => ({}))
  return { status: res.status, data }
}

/* ── Core users ── */

export function getMe(idToken) {
  return request('/api/v1/core/users/me', { idToken })
}

export function createUser(idToken, payload) {
  return request('/api/v1/core/users', {
    method: 'POST',
    idToken,
    body: payload,
  })
}

export function updateMe(idToken, payload) {
  return request('/api/v1/core/users/me', {
    method: 'PUT',
    idToken,
    body: payload,
  })
}

/* ── Pincode ── */

export function getPincodeAreas(code) {
  return request(`/api/v1/core/pincode/${code}`)
}

/* ── Vet profiles ── */

export function getMyVetProfile(idToken) {
  return request('/api/v1/pashu-health/vet-profiles/me', { idToken })
}

export function upsertVetProfile(idToken, payload) {
  return request('/api/v1/pashu-health/vet-profiles', {
    method: 'POST',
    idToken,
    body: payload,
  })
}
