import { useState, useCallback } from 'react'
import { getPincodeAreas, submitApplication } from '../api'
import Icon from './Icon'

/**
 * The vet application: professional details + coverage areas.
 * District is resolved from the pincode server-side and routed to that
 * District Magistrate. Pass `existing` to edit/resubmit a rejected application.
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
        for (const office of data.offices) {
          const area = (office.areaName || office.name || '').trim()
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
      <div className="card application-card">
        <div className="progress-header">
          <span className="progress-step is-complete">
            <i>
              <Icon name="check" size={11} />
            </i>{' '}
            Details
          </span>
          <span className="progress-line is-complete" />
          <span className="progress-step is-current">
            <i>2</i> Coverage
          </span>
        </div>

        <div className="card-head">
          <span className="card-icon">
            <Icon name="pin" size={21} />
          </span>
          <div>
            <p className="eyebrow">Service coverage</p>
            <h2>Select coverage areas</h2>
            <p>
              Pincode <strong>{pincode}</strong>. Choose the communities where you offer services.
            </p>
          </div>
        </div>

        <div className="area-toolbar">
          <span className="result-count">
            {offices.length} area{offices.length === 1 ? '' : 's'} available
          </span>
          <div className="area-toolbar-actions">
            <button
              type="button"
              className="text-button"
              onClick={() => setSelected(new Set(offices))}
            >
              Select all
            </button>
            <button type="button" className="text-button" onClick={() => setSelected(new Set())}>
              Clear
            </button>
          </div>
        </div>

        <div className="area-grid">
          {offices.map((area) => {
            const checked = selected.has(area)
            return (
              <label key={area} className={`area-option${checked ? ' is-selected' : ''}`}>
                <input type="checkbox" checked={checked} onChange={() => toggleArea(area)} />
                <span className="area-check">
                  {checked && <Icon name="check" size={12} strokeWidth={2.5} />}
                </span>
                <span>{area}</span>
              </label>
            )
          })}
        </div>

        {error && (
          <p className="form-message form-message-error" role="alert">
            <Icon name="alert" size={15} /> {error}
          </p>
        )}

        <div className="selection-summary">
          <span className="selection-count">{selected.size}</span>
          <div>
            <strong>{selected.size === 1 ? 'Area selected' : 'Areas selected'}</strong>
            <span>Farmers in these areas can reach you through the network.</span>
          </div>
        </div>

        <div className="btn-row">
          <button type="button" className="btn btn-secondary" onClick={() => setStep(1)}>
            <Icon name="arrowRight" size={15} className="rotate-180" /> Back to details
          </button>
          <button
            type="button"
            className="btn btn-primary"
            onClick={handleSubmit}
            disabled={submitting || selected.size === 0}
          >
            {submitting ? (
              <>
                <span className="spinner" /> Submitting...
              </>
            ) : (
              <>
                Submit application ({selected.size}) <Icon name="arrowRight" size={15} />
              </>
            )}
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="card application-card">
      <div className="progress-header">
        <span className="progress-step is-current">
          <i>1</i> Details
        </span>
        <span className="progress-line" />
        <span className="progress-step">
          <i>2</i> Coverage
        </span>
      </div>

      <div className="card-head">
        <span className="card-icon">
          <Icon name="file" size={21} />
        </span>
        <div>
          <p className="eyebrow">Veterinary application</p>
          <h2>{existing ? 'Edit and resubmit' : 'Tell us about your practice'}</h2>
          <p>
            Your application goes to the District Magistrate for your pincode for verification.
          </p>
        </div>
      </div>

      {existing?.status === 'rejected' && existing.review_note && (
        <div className="review-note">
          <span className="field-label">Reviewer note</span>
          <p>{existing.review_note}</p>
        </div>
      )}

      <form
        className="application-form"
        onSubmit={(e) => {
          e.preventDefault()
          fetchAreas()
        }}
      >
        <div className="form-section">
          <div className="form-section-head">
            <span className="section-icon">
              <Icon name="award" size={16} />
            </span>
            <div>
              <h3>Professional details</h3>
              <p>Your qualifications and veterinary registration.</p>
            </div>
          </div>

          <div className="form-grid">
            <div className="form-field">
              <label htmlFor="full-name">Full name</label>
              <input
                id="full-name"
                type="text"
                autoComplete="name"
                placeholder="Dr. Rajesh Kumar"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                required
              />
            </div>

            <div className="form-field">
              <label htmlFor="qualification">Qualification</label>
              <input
                id="qualification"
                type="text"
                placeholder="BVSc & AH"
                value={qualification}
                onChange={(e) => setQualification(e.target.value)}
                required
              />
            </div>

            <div className="form-field">
              <label htmlFor="license">Veterinary license number</label>
              <input
                id="license"
                type="text"
                placeholder="VCI-12345"
                value={licenseNumber}
                onChange={(e) => setLicenseNumber(e.target.value)}
                required
              />
            </div>

            <div className="form-field">
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
            </div>
          </div>
        </div>

        <div className="form-section">
          <div className="form-section-head">
            <span className="section-icon">
              <Icon name="briefcase" size={16} />
            </span>
            <div>
              <h3>Practice details</h3>
              <p>Where you work and which pincode routes your application.</p>
            </div>
          </div>

          <div className="form-grid">
            <div className="form-field full">
              <label htmlFor="clinic">Clinic or hospital name</label>
              <input
                id="clinic"
                type="text"
                placeholder="Care Animal Clinic"
                value={clinicName}
                onChange={(e) => setClinicName(e.target.value)}
              />
            </div>

            <div className="form-field full">
              <label htmlFor="address">Address</label>
              <input
                id="address"
                type="text"
                autoComplete="street-address"
                placeholder="12, Main Road"
                value={address}
                onChange={(e) => setAddress(e.target.value)}
                required
              />
            </div>

            <div className="form-field">
              <label htmlFor="village">Village or town</label>
              <input
                id="village"
                type="text"
                placeholder="Rampur"
                value={village}
                onChange={(e) => setVillage(e.target.value)}
                required
              />
            </div>

            <div className="form-field">
              <label htmlFor="pincode">Pincode</label>
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
            </div>
          </div>
          <p className="field-help">
            <Icon name="info" size={13} /> Your district is assigned automatically from this
            pincode.
          </p>
        </div>

        {error && (
          <p className="form-message form-message-error" role="alert">
            <Icon name="alert" size={15} /> {error}
          </p>
        )}

        <div className="form-footer">
          <div className="form-footer-note">
            <Icon name="shield" size={15} />
            Details are shared only with your District Magistrate for review.
          </div>
          <button
            className="btn btn-primary"
            type="submit"
            disabled={pincodeLoading || pincode.length !== 6}
          >
            {pincodeLoading ? (
              <>
                <span className="spinner" /> Finding areas...
              </>
            ) : (
              <>
                Find coverage areas <Icon name="arrowRight" size={15} />
              </>
            )}
          </button>
        </div>
      </form>
    </div>
  )
}
