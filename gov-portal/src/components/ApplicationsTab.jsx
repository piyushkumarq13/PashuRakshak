import { useState, useEffect, useCallback } from 'react'
import { getVetApplications, reviewApplication } from '../api'

const STATUS_FILTERS = ['pending', 'approved', 'rejected', 'all']

function fmtDate(ms) {
  return ms ? new Date(Number(ms)).toLocaleString() : '—'
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

  const load = useCallback(async (filter) => {
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
  }, [magistrate?.district])

  useEffect(() => {
    load(status)
  }, [status, load])

  function toggleExpand(app) {
    setExpandedId((id) => (id === app.id ? null : app.id))
    setNote(app.review_note ?? '')
    setActionError('')
  }

  async function review(app, action) {
    setActionError('')
    setSuccess('')
    if (action === 'reject' && !note.trim()) {
      setActionError('Add a note explaining the rejection.')
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
          ? `${app.full_name} approved. They can now sign in to the Vet App.`
          : `${app.full_name} rejected.`,
      )
      await load(status)
      onReviewed?.()
    } else {
      setActionError(data.message || 'Could not update the application.')
    }
  }

  let areas = []
  const districtLabel =
    serverDistrict === '*' ? 'all districts' : serverDistrict || magistrate?.district || 'your district'
  return (
    <div className="card">
      <div className="tab-header">
        <div>
          <h2>Vet applications — {districtLabel}</h2>
          <p className="muted">Applications routed to your district by pincode (case-insensitive match).</p>
        </div>
        <div className="filter-row">
          {STATUS_FILTERS.map((s) => (
            <button
              key={s}
              className={`chip ${status === s ? 'chip-active' : ''}`}
              onClick={() => setStatus(s)}
            >
              {s}
            </button>
          ))}
          <button className="btn-secondary" onClick={() => load(status)} disabled={loading}>
            {loading ? 'Refreshing…' : 'Refresh'}
          </button>
        </div>
      </div>

      {loading && <p className="muted">Loading…</p>}
      {error && <p className="error">{error}</p>}
      {success && <p className="success">{success}</p>}
      {!loading && !error && applications.length === 0 && (
        <p className="muted">
          No {status === 'all' ? '' : status} applications in {districtLabel}.
          {status === 'pending' && serverDistrict !== '*' && serverDistrict !== '' && (
            <> If applicants used another district’s pincode, sign in as that district’s magistrate or set district to *.</>
          )}
        </p>
      )}

      <div className="app-list">
        {applications.map((app) => {
          try {
            areas = JSON.parse(app.service_areas ?? '[]')
          } catch {
            areas = []
          }
          const expanded = expandedId === app.id
          return (
            <div key={app.id} className={`app-item ${expanded ? 'app-item-expanded' : ''}`}>
              <button className="app-item-header" onClick={() => toggleExpand(app)}>
                <div>
                  <strong>{app.full_name}</strong>
                  <span className="muted"> · {app.village}, {app.pincode}</span>
                  <div className="muted small">
                    {app.qualification}
                    {app.experience_years != null ? ` · ${app.experience_years} yrs` : ''}
                    {app.clinic_name ? ` · ${app.clinic_name}` : ''}
                  </div>
                </div>
                <div className="app-item-right">
                  <span className={`status-badge status-${app.status}`}>{app.status}</span>
                  <span className="muted small">{fmtDate(app.created_at)}</span>
                </div>
              </button>

              {expanded && (
                <div className="app-item-body">
                  <div className="detail-grid">
                    <div>
                      <span className="field-label">Phone</span>
                      <span>{app.phone || '—'}</span>
                    </div>
                    <div>
                      <span className="field-label">Email</span>
                      <span>{app.email || '—'}</span>
                    </div>
                    <div>
                      <span className="field-label">License</span>
                      <span>{app.license_number}</span>
                    </div>
                    <div>
                      <span className="field-label">Address</span>
                      <span>{app.address}</span>
                    </div>
                    <div>
                      <span className="field-label">District</span>
                      <span>
                        {app.district}
                        {app.state ? `, ${app.state}` : ''}
                      </span>
                    </div>
                    <div>
                      <span className="field-label">Last updated</span>
                      <span>{fmtDate(app.updated_at)}</span>
                    </div>
                  </div>

                  <div>
                    <span className="field-label">Coverage areas</span>
                    <div className="tag-list">
                      {areas.length > 0 ? (
                        areas.map((a) => (
                          <span key={a} className="tag">
                            {a}
                          </span>
                        ))
                      ) : (
                        <span className="muted">—</span>
                      )}
                    </div>
                  </div>

                  {app.review_note && (
                    <div className="review-note">
                      <span className="field-label">Previous review note</span>
                      <p>{app.review_note}</p>
                    </div>
                  )}

                  <label htmlFor={`note-${app.id}`} className="field-label">
                    Review note (shown to the applicant)
                  </label>
                  <textarea
                    id={`note-${app.id}`}
                    rows={3}
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                    placeholder="Optional for approval, required for rejection…"
                  />
                  {actionError && <p className="error">{actionError}</p>}

                  <div className="btn-row">
                    <button
                      className="btn-approve"
                      disabled={busyId === app.id}
                      onClick={() => review(app, 'approve')}
                    >
                      {busyId === app.id ? 'Working…' : 'Approve'}
                    </button>
                    <button
                      className="btn-reject"
                      disabled={busyId === app.id}
                      onClick={() => review(app, 'reject')}
                    >
                      Reject
                    </button>
                  </div>
                </div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
