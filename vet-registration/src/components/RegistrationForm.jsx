import { useState, useCallback } from 'react'
import { createUser, getPincodeAreas, upsertVetProfile } from '../api'

export default function RegistrationForm({ idToken, phone, onDone }) {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [pincode, setPincode] = useState('')
  const [offices, setOffices] = useState([])
  const [selected, setSelected] = useState(new Set())
  const [pincodeLoading, setPincodeLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [step, setStep] = useState(1) // 1 = basic info, 2 = coverage areas

  const fetchAreas = useCallback(async () => {
    setError('')
    const code = pincode.replace(/\D/g, '')
    if (code.length !== 6) {
      setError('Pincode must be 6 digits.')
      return
    }
    setPincodeLoading(true)
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
        if (unique.length === 0) {
          setError('No areas found for this pincode.')
          setOffices([])
        } else {
          setOffices(unique)
          setSelected(new Set())
          setStep(2)
        }
      } else {
        setError(data.message || 'No post offices found for this pincode.')
        setOffices([])
      }
    } catch {
      setError('Failed to fetch areas. Check your connection.')
    } finally {
      setPincodeLoading(false)
    }
  }, [pincode])

  function toggleArea(area) {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(area)) next.delete(area)
      else next.add(area)
      return next
    })
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    if (!name.trim()) return setError('Name is required.')
    if (selected.size === 0) return setError('Select at least one coverage area.')
    setSubmitting(true)
    try {
      // 1. Create core user
      const userRes = await createUser(idToken, {
        phone,
        role: 'vet',
        name: name.trim(),
        email: email.trim() || null,
      })
      if (userRes.status !== 200) {
        setError(userRes.data.message || 'Failed to create user.')
        setSubmitting(false)
        return
      }

      // 2. Upsert vet profile
      const profileRes = await upsertVetProfile(idToken, {
        pincode: pincode.replace(/\D/g, ''),
        serviceAreas: [...selected],
      })
      if (profileRes.status !== 200) {
        setError(profileRes.data.message || 'Failed to save coverage areas.')
        setSubmitting(false)
        return
      }

      onDone()
    } catch (err) {
      console.error(err)
      setError('Something went wrong. Please try again.')
      setSubmitting(false)
    }
  }

  if (step === 2) {
    return (
      <div className="card">
        <h2>Select coverage areas</h2>
        <p className="muted">
          Pincode <strong>{pincode}</strong> — choose where you offer services.
        </p>
        <div className="area-list">
          {offices.map((area) => (
            <label key={area} className="area-item">
              <input
                type="checkbox"
                checked={selected.has(area)}
                onChange={() => toggleArea(area)}
              />
              <span>{area}</span>
            </label>
          ))}
        </div>
        {error && <p className="error">{error}</p>}
        <div className="btn-row">
          <button className="btn-secondary" onClick={() => setStep(1)}>
            Back
          </button>
          <button onClick={handleSubmit} disabled={submitting || selected.size === 0}>
            {submitting ? 'Saving…' : `Register (${selected.size} area${selected.size === 1 ? '' : 's'})`}
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="card">
      <h2>Complete your registration</h2>
      <p className="muted">Tell us about yourself so farmers can find you.</p>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          fetchAreas()
        }}
      >
        <label htmlFor="name">Full name *</label>
        <input
          id="name"
          type="text"
          placeholder="Dr. Rajesh Kumar"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
        />

        <label htmlFor="email">Email (optional)</label>
        <input
          id="email"
          type="email"
          placeholder="you@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />

        <label htmlFor="pincode">Pincode *</label>
        <input
          id="pincode"
          type="text"
          inputMode="numeric"
          maxLength={6}
          placeholder="110001"
          value={pincode}
          onChange={(e) => setPincode(e.target.value.replace(/\D/g, ''))}
          required
        />

        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={pincodeLoading || pincode.length !== 6}>
          {pincodeLoading ? 'Fetching areas…' : 'Find coverage areas →'}
        </button>
      </form>
    </div>
  )
}
