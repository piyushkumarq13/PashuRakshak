import { useState, useEffect, useCallback } from 'react'
import { getVetApplications, reviewApplication } from '../api'
import Icon from './Icon'

const STATUS_FILTERS = ['pending', 'approved', 'rejected', 'all']

function fmtDate(ms) {
  if (!ms) return '—'
  return new Date(Number(ms)).toLocaleString('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function formatStatus(value) {
  return String(value ?? 'unknown').replace(/_/g, ' ')
}

function initials(name) {
  return String(name || 'VA')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join('')
    .toUpperCase()
}

export default function ApplicationsTab({ magistrate, onReviewed }) {
  const [status, setStatus] = useState('pending')
  const [applications, setApplications] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [expandedId, setExpandedId] = useState(null)
  const [note, setNote] = useState('')
  const [busyId, setBusyId] = useState(null)
  const [actionError, setActionError] = useState('')
  const [success, setSuccess] = useState('')
  const [serverDistrict, setServerDistrict] = useState(magistrate?.district ?? '')

  const load = useCallback(
    async (filter) => {
      setLoading(true)
      const { status: code, data } = await getVetApplications(filter)
      if (code === 200) {
        setApplications(data.applications ?? [])
        setServerDistrict(data.district ?? magistrate?.district ?? '')
        setError('')
      } else {
        setError(data.message || 'Could not load applications.')
      }
      setLoading(false)
    },
    [magistrate?.district],
  )

  useEffect(() => {
    load(status)
  }, [status, load])

  function toggleExpand(app) {
    setExpandedId((id) => (id === app.id ? null : app.id))
    setNote(app.review_note ?? '')
    setActionError('')
    setSuccess('')
  }

  async function review(app, action) {
    setActionError('')
    setSuccess('')
    if (action === 'reject' && !note.trim()) {
      setActionError('Add a note explaining the rejection before continuing.')
      return
    }
    setBusyId(app.id)
    const { status: code, data } = await reviewApplication(app.id, action, note.trim() || undefined)
    setBusyId(null)
    if (code === 200) {
      setExpandedId(null)
      setNote('')
      setSuccess(
        action === 'approve'
          ? `${app.full_name} is approved and can now sign in to the Vet App.`
          : `${app.full_name}'s application was rejected and the note was shared.`,
      )
      await load(status)
      onReviewed?.()
    } else {
      setActionError(data.message || 'Could not update the application.')
    }
  }

  const districtLabel =
    serverDistrict === '*' ? 'all districts' : serverDistrict || magistrate?.district || 'your district'

  return (
    <div className="panel-card">
      <div className="panel-toolbar">
        <div>
          <p className="eyebrow">Applications</p>
          <h2>Review queue</h2>
          <p>Applications routed to {districtLabel} by pincode.</p>
        </div>
        <button className="btn btn-secondary" onClick={() => load(status)} disabled={loading}>
          <Icon name="refresh" size={16} />
          {loading ? 'Refreshing' : 'Refresh'}
        </button>
      </div>

      <div className="filter-bar">
        <div className="segmented" role="group" aria-label="Filter applications by status">
          {STATUS_FILTERS.map((item) => (
            <button
              key={item}
              type="button"
              className={status === item ? 'is-active' : ''}
              onClick={() => setStatus(item)}
            >
              {item === 'all' ? 'All' : formatStatus(item)}
            </button>
          ))}
        </div>
        <span className="result-count">
          {loading ? 'Loading...' : `${applications.length} application${applications.length === 1 ? '' : 's'}`}
        </span>
      </div>

      {error && (
        <p className="form-message form-message-error" role="alert">
          <Icon name="close" size={15} /> {error}
        </p>
      )}
      {success && (
        <p className="form-message form-message-success" role="status">
          <Icon name="check" size={15} /> {success}
        </p>
      )}

      {!loading && !error && applications.length === 0 && (
        <div className="empty-state">
          <span className="empty-icon">
            <Icon name="applications" size={24} />
          </span>
          <h3>No {status === 'all' ? '' : `${status} `}applications here</h3>
          <p>
            Applications in {districtLabel} will appear here as soon as they are submitted.
          </p>
        </div>
      )}

      <div className="application-list">
        {applications.map((app) => {
          let areas = []
          try {
            areas = JSON.parse(app.service_areas ?? '[]')
          } catch {
            areas = []
          }
          const expanded = expandedId === app.id
          return (
            <article key={app.id} className={`application-card ${expanded ? 'is-open' : ''}`}>
              <button
                className="application-summary"
                type="button"
                onClick={() => toggleExpand(app)}
                aria-expanded={expanded}
              >
                <span className="person-avatar">{initials(app.full_name)}</span>
                <span className="application-person">
                  <strong>{app.full_name}</strong>
                  <span>
                    {app.village}, {app.pincode}
                  </span>
                  <span className="application-meta">
                    {app.qualification}
                    {app.experience_years != null ? ` · ${app.experience_years} yrs experience` : ''}
                    {app.clinic_name ? ` · ${app.clinic_name}` : ''}
                  </span>
                </span>
                <span className="application-side">
                  <span className={`status-badge status-${app.status}`}>{formatStatus(app.status)}</span>
                  <span className="date-text">{fmtDate(app.created_at)}</span>
                  <Icon name="chevron" size={17} className="expand-icon" />
                </span>
              </button>

              {expanded && (
                <div className="application-body">
                  <div className="detail-grid">
                    <div className="detail-item">
                      <span className="field-label">Phone</span>
                      <span>{app.phone || '—'}</span>
                    </div>
                    <div className="detail-item">
                      <span className="field-label">Email</span>
                      <span>{app.email || '—'}</span>
                    </div>
                    <div className="detail-item">
                      <span className="field-label">License</span>
                      <span>{app.license_number}</span>
                    </div>
                    <div className="detail-item">
                      <span className="field-label">Address</span>
                      <span>{app.address}</span>
                    </div>
                    <div className="detail-item">
                      <span className="field-label">District</span>
                      <span>
                        {app.district}
                        {app.state ? `, ${app.state}` : ''}
                      </span>
                    </div>
                    <div className="detail-item">
                      <span className="field-label">Last updated</span>
                      <span>{fmtDate(app.updated_at)}</span>
                    </div>
                  </div>

                  <div>
                    <span className="field-label">Coverage areas</span>
                    <div className="tag-list">
                      {areas.length > 0 ? (
                        areas.map((area) => (
                          <span key={area} className="tag">
                            <Icon name="pin" size={12} /> {area}
                          </span>
                        ))
                      ) : (
                        <span className="muted">No areas listed</span>
                      )}
                    </div>
                  </div>

                  {app.review_note && (
                    <div className="review-note">
                      <span className="field-label">Previous review note</span>
                      <p>{app.review_note}</p>
                    </div>
                  )}

                  <div className="review-box">
                    <label htmlFor={`note-${app.id}`}>Review note</label>
                    <p className="field-help">
                      This note is visible to the applicant. Required when rejecting.
                    </p>
                    <textarea
                      id={`note-${app.id}`}
                      rows={3}
                      value={note}
                      onChange={(e) => setNote(e.target.value)}
                      placeholder="Add a short, clear reason or next step..."
                    />
                  </div>

                  {actionError && (
                    <p className="form-message form-message-error" role="alert">
                      <Icon name="close" size={15} /> {actionError}
                    </p>
                  )}

                  <div className="btn-row">
                    <button
                      className="btn btn-approve"
                      disabled={busyId === app.id}
                      onClick={() => review(app, 'approve')}
                    >
                      <Icon name="check" size={16} />
                      {busyId === app.id ? 'Saving...' : 'Approve application'}
                    </button>
                    <button
                      className="btn btn-reject"
                      disabled={busyId === app.id}
                      onClick={() => review(app, 'reject')}
                    >
                      <Icon name="close" size={16} />
                      Reject
                    </button>
                  </div>
                </div>
              )}
            </article>
          )
        })}
      </div>
    </div>
  )
}
