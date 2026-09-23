import { useState, useEffect, useCallback } from 'react'
import {
  getMyVetProfile,
  upsertVetProfile,
  updateMe,
  getPincodeAreas,
} from '../api'

export default function ProfilePage({ idToken, me, onMeUpdated, onRefresh }) {
  const [name, setName] = useState(me.name || '')
  const [email, setEmail] = useState(me.email || '')
  const [pincode, setPincode] = useState('')
  const [serviceAreas, setServiceAreas] = useState([])
  const [offices, setOffices] = useState([])
  const [loadingProfile, setLoadingProfile] = useState(true)
  const [saving, setSaving] = useState(false)
  const [fetchingAreas, setFetchingAreas] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [editing, setEditing] = useState(false)

  const loadProfile = useCallback(async () => {
    setLoadingProfile(true)
    try {
      const { status, data } = await getMyVetProfile(idToken)
      if (status === 200 && data.profile) {
        setPincode(data.profile.pincode || '')
        try {
          const areas = JSON.parse(data.profile.service_areas || '[]')
          setServiceAreas(Array.isArray(areas) ? areas : [])
        } catch {
          setServiceAreas([])
        }
      }
    } catch {
      // profile not found yet — leave defaults
    } finally {
      setLoadingProfile(false)
    }
  }, [idToken])

  useEffect(() => {
    loadProfile()
  }, [loadProfile])

  async function fetchAreas() {
    setError('')
    const code = pincode.replace(/\D/g, '')
    if (code.length !== 6) {
      setError('Pincode must be 6 digits.')
      return
    }
    setFetchingAreas(true)
    try {
      const { status, data } = await getPincodeAreas(code)
      if (status === 200 && data.offices?.length) {
        const unique = []
        const seen = new Set()
        for (const o of data.offices) {
          const area = (o.areaName || o.name || '').trim()
          if (area && !seen.has(area)) {
            seen.add(area)
            unique.push(area)
          }
        }
        setOffices(unique)
      } else {
        setError(data.message || 'No areas found for this pincode.')
        setOffices([])
      }
    } catch {
      setError('Failed to fetch areas.')
    } finally {
      setFetchingAreas(false)
    }
  }

  function toggleArea(area) {
    setServiceAreas((prev) =>
      prev.includes(area) ? prev.filter((a) => a !== area) : [...prev, area],
    )
  }

  async function handleSave(e) {
    e.preventDefault()
    setError('')
    setSuccess('')
    if (!name.trim()) return setError('Name is required.')
    if (!pincode.trim()) return setError('Pincode is required.')
    if (serviceAreas.length === 0) return setError('Select at least one coverage area.')

    setSaving(true)
    try {
      // 1. Update core user
      const userRes = await updateMe(idToken, {
        name: name.trim(),
        email: email.trim() || null,
      })
      if (userRes.status !== 200) {
        setError(userRes.data.message || 'Failed to update profile.')
        return
      }
      onMeUpdated(userRes.data.user)

      // 2. Upsert vet profile
      const profileRes = await upsertVetProfile(idToken, {
        pincode: pincode.replace(/\D/g, ''),
        serviceAreas,
      })
      if (profileRes.status !== 200) {
        setError(profileRes.data.message || 'Failed to save coverage areas.')
        return
      }

      setSuccess('Profile saved successfully!')
      setEditing(false)
      setOffices([])
    } catch (err) {
      console.error(err)
      setError('Something went wrong.')
    } finally {
      setSaving(false)
    }
  }

  if (loadingProfile) {
    return (
      <div className="card center-text">
        <p>Loading your profile…</p>
      </div>
    )
  }

  return (
    <div className="card">
      <div className="profile-header">
        <h2>Your Profile</h2>
        {!editing && (
          <button className="btn-secondary" onClick={() => setEditing(true)}>
            Edit
          </button>
        )}
      </div>

      {success && <p className="success">{success}</p>}
      {error && <p className="error">{error}</p>}

      {!editing ? (
        <div className="profile-view">
          <div className="field">
            <span className="field-label">Name</span>
            <span className="field-value">{me.name || '—'}</span>
          </div>
          <div className="field">
            <span className="field-label">Phone</span>
            <span className="field-value">{me.phone || '—'}</span>
          </div>
          <div className="field">
            <span className="field-label">Email</span>
            <span className="field-value">{me.email || '—'}</span>
          </div>
          <div className="field">
            <span className="field-label">Pincode</span>
            <span className="field-value">{pincode || '—'}</span>
          </div>
          <div className="field">
            <span className="field-label">Coverage areas</span>
            <div className="tag-list">
              {serviceAreas.length > 0 ? (
                serviceAreas.map((a) => (
                  <span key={a} className="tag">
                    {a}
                  </span>
                ))
              ) : (
                <span className="field-value">—</span>
              )}
            </div>
          </div>
        </div>
      ) : (
        <form onSubmit={handleSave}>
          <label htmlFor="p-name">Full name *</label>
          <input
            id="p-name"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
          />

          <label htmlFor="p-email">Email</label>
          <input
            id="p-email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />

          <label htmlFor="p-pincode">Pincode *</label>
          <div className="phone-row">
            <input
              id="p-pincode"
              type="text"
              inputMode="numeric"
              maxLength={6}
              value={pincode}
              onChange={(e) => setPincode(e.target.value.replace(/\D/g, ''))}
              required
            />
            <button
              type="button"
              className="btn-secondary"
              onClick={fetchAreas}
              disabled={fetchingAreas || pincode.length !== 6}
            >
              {fetchingAreas ? '…' : 'Load areas'}
            </button>
          </div>

          <label>Coverage areas *</label>
          {offices.length > 0 ? (
            <div className="area-list">
              {offices.map((area) => (
                <label key={area} className="area-item">
                  <input
                    type="checkbox"
                    checked={serviceAreas.includes(area)}
                    onChange={() => toggleArea(area)}
                  />
                  <span>{area}</span>
                </label>
              ))}
            </div>
          ) : serviceAreas.length > 0 ? (
            <div className="area-list">
              <p className="muted">
                Current areas (click "Load areas" to modify):
              </p>
              {serviceAreas.map((area) => (
                <label key={area} className="area-item">
                  <input
                    type="checkbox"
                    checked
                    onChange={() => toggleArea(area)}
                  />
                  <span>{area}</span>
                </label>
              ))}
            </div>
          ) : (
            <p className="muted">Load areas for your pincode first.</p>
          )}

          <div className="btn-row">
            <button
              type="button"
              className="btn-secondary"
              onClick={() => {
                setEditing(false)
                setError('')
                loadProfile()
              }}
            >
              Cancel
            </button>
            <button type="submit" disabled={saving}>
              {saving ? 'Saving…' : 'Save changes'}
            </button>
          </div>
        </form>
      )}
    </div>
  )
}
