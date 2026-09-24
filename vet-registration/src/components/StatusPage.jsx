const STATUS_LABELS = {
  pending: 'Pending review',
  approved: 'Approved',
  rejected: 'Rejected',
  none: 'Not submitted',
}

/**
 * Live application status — shown after submitting, and to returning vets
 * who sign in while their application is still (or no longer) under review.
 */
export default function StatusPage({ application, onResubmit, onSignOut, onRefresh, refreshing }) {
  const status = application?.status ?? 'none'

  let statusMessage = ''
  if (status === 'pending') {
    statusMessage = application?.district
      ? `Your application is being reviewed by the District Magistrate of ${application.district}.`
      : 'Your application is under review.'
  } else if (status === 'approved') {
    statusMessage =
      'You’re approved! Sign in to the PashuRakshak Vet App with your phone number and the PIN/password you set.'
  } else if (status === 'rejected') {
    statusMessage = 'Your application was not approved. Review the note below, update your details and resubmit.'
  } else {
    statusMessage = 'You haven’t submitted an application yet.'
  }

  let areas = []
  try {
    areas = JSON.parse(application?.service_areas ?? '[]')
  } catch {
    areas = []
  }

  const fmtDate = (ms) => (ms ? new Date(Number(ms)).toLocaleDateString() : '—')

  return (
    <div className="card">
      <div className="profile-header">
        <div>
          <h2>Application status</h2>
          <p className="muted" style={{ marginTop: 2 }}>
            {application?.full_name ? `${application.full_name} · ` : ''}
            {application?.district ? `District of ${application.district}` : 'PashuRakshak network'}
          </p>
        </div>
        <button className="btn-secondary" onClick={onRefresh} disabled={refreshing}>
          {refreshing ? 'Refreshing…' : 'Refresh'}
        </button>
      </div>

      <span className={`status-badge status-${status}`}>{STATUS_LABELS[status] ?? status}</span>
      <p className="status-message">{statusMessage}</p>

      {application && (
        <div className="profile-view">
          <div className="field">
            <span className="field-label">Full name</span>
            <span className="field-value">{application.full_name}</span>
          </div>
          <div className="field">
            <span className="field-label">Qualification</span>
            <span className="field-value">{application.qualification}</span>
          </div>
          <div className="field">
            <span className="field-label">License number</span>
            <span className="field-value">{application.license_number}</span>
          </div>
          <div className="field">
            <span className="field-label">Phone</span>
            <span className="field-value">{application.phone || '—'}</span>
          </div>
          <div className="field">
            <span className="field-label">Email</span>
            <span className="field-value">{application.email || '—'}</span>
          </div>
          <div className="field">
            <span className="field-label">Village</span>
            <span className="field-value">{application.village}</span>
          </div>
          <div className="field">
            <span className="field-label">Pincode / district</span>
            <span className="field-value">
              {application.pincode} · {application.district}
              {application.state ? `, ${application.state}` : ''}
            </span>
          </div>
          <div className="field">
            <span className="field-label">Coverage areas</span>
            <div className="tag-list">
              {areas.length > 0 ? (
                areas.map((a) => (
                  <span key={a} className="tag">
                    {a}
                  </span>
                ))
              ) : (
                <span className="field-value">—</span>
              )}
            </div>
          </div>
          <div className="field">
            <span className="field-label">Submitted</span>
            <span className="field-value">{fmtDate(application.created_at)}</span>
          </div>
          {status !== 'none' && (
            <div className="field">
              <span className="field-label">Last updated</span>
              <span className="field-value">{fmtDate(application.updated_at)}</span>
            </div>
          )}
          {application.review_note && (
            <div className="review-note">
              <span className="field-label">Reviewer’s note</span>
              <p>{application.review_note}</p>
            </div>
          )}
        </div>
      )}

      <div className="btn-row">
        {status === 'rejected' || status === 'none' ? (
          <button onClick={onResubmit}>
            {status === 'rejected' ? 'Edit & resubmit' : 'Complete application'}
          </button>
        ) : (
          <button className="btn-secondary" onClick={onSignOut}>
            Sign out
          </button>
        )}
        {status === 'rejected' || status === 'none' ? (
          <button className="btn-secondary" onClick={onSignOut}>
            Sign out
          </button>
        ) : null}
      </div>
    </div>
  )
}
