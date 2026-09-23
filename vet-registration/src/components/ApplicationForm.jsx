import { useState, useCallback } from 'react'
import { getPincodeAreas, submitApplication } from '../api'

/**
 * The vet application itself — professional details + coverage areas.
 * Submitted straight to the server (district is resolved from the pincode
 * server-side and routed to that District Magistrate).
 * Pass `existing` to edit/resubmit a rejected application.
 */
export default function ApplicationForm({ existing, onSubmitted }) {
  const [step, setStep] = useState(1)
  const [fullName, setFullName] = useState(existing?.full_name ?? '')
  const [qualification, setQualification] = useState(existing?.qualification ?? '')
  const [licenseNumber, setLicenseNumber] = useState(existing?.license_number ?? '')
  const [experienceYears, setExperienceYears] = useState(
    existing?.experience_years != null ? String(existing.experience_years) : '',
  )
  const [clinicName, setClinicName] = useState(existing?.clinic_name ?? '')
  const [address, setAddress] = useState(existing?.address ?? '')
  const [village, setVillage] = useState(existing?.village ?? '')
  const [pincode, setPincode] = useState(existing?.pincode ?? '')
  const [offices, setOffices] = useState([])
  const [selected, setSelected] = useState(() => {
    try {
      return new Set(JSON.parse(existing?.service_areas ?? '[]'))
    } catch {
      return new Set()
    }
  })
  const [pincodeLoading, setPincodeLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

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
    if (!fullName.trim()) return setError('Full name is required.')
    if (!qualification.trim()) return setError('Qualification is required.')
    if (!licenseNumber.trim()) return setError('License number is required.')
    if (!address.trim()) return setError('Address is required.')
    if (!village.trim()) return setError('Village is required.')
    if (selected.size === 0) return setError('Select at least one coverage area.')

    setSubmitting(true)
    try {
      const { status, data } = await submitApplication({
        fullName: fullName.trim(),
        qualification: qualification.trim(),
        licenseNumber: licenseNumber.trim(),
        experienceYears: experienceYears ? Number(experienceYears) : null,
        clinicName: clinicName.trim() || null,
        address: address.trim(),
        village: village.trim(),
        pincode: pincode.replace(/\D/g, ''),
        serviceAreas: [...selected],
      })
      if (status === 200 && data.application) {
        onSubmitted(data.application)
      } else {
        setError(data.message || 'Could not submit the application.')
        setSubmitting(false)
      }
    } catch {
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
        {selected.size > 0 && (
          <p className="info">{selected.size} area{selected.size === 1 ? '' : 's'} selected</p>
        )}
        {error && <p className="error">{error}</p>}
        <div className="btn-row">
          <button className="btn-secondary" onClick={() => setStep(1)}>
            Back
          </button>
          <button onClick={handleSubmit} disabled={submitting || selected.size === 0}>
            {submitting ? 'Submitting…' : `Submit application (${selected.size})`}
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="card">
      <h2>{existing ? 'Edit & resubmit application' : 'Vet application'}</h2>
      <p className="muted">
        Your application goes to the District Magistrate of your pincode’s district for approval.
      </p>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          fetchAreas()
        }}
      >
        <label htmlFor="full-name">Full name *</label>
        <input
          id="full-name"
          type="text"
          placeholder="Dr. Rajesh Kumar"
          value={fullName}
          onChange={(e) => setFullName(e.target.value)}
          required
        />

        <label htmlFor="qualification">Qualification *</label>
        <input
          id="qualification"
          type="text"
          placeholder="BVSc & AH"
          value={qualification}
          onChange={(e) => setQualification(e.target.value)}
          required
        />

        <label htmlFor="license">Veterinary license number *</label>
        <input
          id="license"
          type="text"
          placeholder="VCI-12345"
          value={licenseNumber}
          onChange={(e) => setLicenseNumber(e.target.value)}
          required
        />

        <label htmlFor="experience">Years of experience</label>
        <input
          id="experience"
          type="text"
          inputMode="numeric"
          maxLength={2}
          placeholder="5"
          value={experienceYears}
          onChange={(e) => setExperienceYears(e.target.value.replace(/\D/g, ''))}
        />

        <label htmlFor="clinic">Clinic / hospital name</label>
        <input
          id="clinic"
          type="text"
          placeholder="Care Animal Clinic"
          value={clinicName}
          onChange={(e) => setClinicName(e.target.value)}
        />

        <label htmlFor="address">Address *</label>
        <input
          id="address"
          type="text"
          placeholder="12, Main Road"
          value={address}
          onChange={(e) => setAddress(e.target.value)}
          required
        />

        <label htmlFor="village">Village / town *</label>
        <input
          id="village"
          type="text"
          placeholder="Rampur"
          value={village}
          onChange={(e) => setVillage(e.target.value)}
          required
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
