import Icon from './Icon'

const STATUS_META = {
  pending: {
    label: 'Pending review',
    icon: 'clock',
    tone: 'pending',
    title: 'Your application is under review',
  },
  approved: {
    label: 'Approved',
    icon: 'check',
    tone: 'approved',
    title: 'You are approved',
  },
  rejected: {
    label: 'Needs attention',
    icon: 'alert',
    tone: 'rejected',
    title: 'Your application needs an update',
  },
  none: {
    label: 'Not submitted',
    icon: 'file',
    tone: 'none',
    title: 'No application submitted',
  },
}

/**
 * Live application status, shown after submitting and to returning vets
 * whose application is under review or has a decision.
 */
export default function StatusPage({ application, onResubmit, onSignOut, onRefresh, refreshing }) {
  const status = application?.status ?? 'none'
  const meta = STATUS_META[status] ?? STATUS_META.none

  let statusMessage = ''
  if (status === 'pending') {
    statusMessage = application?.district
      ? `The District Magistrate of ${application.district} is reviewing your credentials and coverage areas.`
      : 'Your credentials and coverage areas are being reviewed.'
  } else if (status === 'approved') {
    statusMessage =
      'Sign in to the PashuRakshak Vet App with your phone number and the PIN or password you set.'
  } else if (status === 'rejected') {
    statusMessage =
      'Review the note below, update your details, and resubmit. Your application will return to the review queue.'
  } else {
    statusMessage = 'Complete your professional details and coverage areas to apply.'
  }

  let areas = []
  try {
    areas = JSON.parse(application?.service_areas ?? '[]')
  } catch {
    areas = []
  }

  const fmtDate = (ms) =>
    ms
      ? new Date(Number(ms)).toLocaleDateString('en-IN', {
          day: 'numeric',
          month: 'short',
          year: 'numeric',
        })
      : '—'

  const timeline = [
    {
      label: 'Application submitted',
      text: application ? fmtDate(application.created_at) : 'Not submitted',
      state: application ? 'complete' : 'upcoming',
    },
    {
      label: 'District review',
      text:
        status === 'pending'
          ? 'In progress'
          : status === 'approved' || status === 'rejected'
            ? 'Review completed'
            : 'Waiting for submission',
      state:
        status === 'approved' || status === 'rejected'
          ? 'complete'
          : status === 'pending'
            ? 'current'
            : 'upcoming',
    },
    {
      label: status === 'rejected' ? 'Changes requested' : 'Final decision',
      text:
        status === 'approved'
          ? 'Approved'
          : status === 'rejected'
            ? 'Action required'
            : status === 'pending'
              ? 'Pending'
              : 'Not started',
      state:
        status === 'approved'
          ? 'complete'
          : status === 'rejected'
            ? 'failed'
            : status === 'pending'
              ? 'upcoming'
              : 'upcoming',
    },
  ]

  return (
    <div className="card status-card">
      <div className="status-head">
        <div>
          <p className="eyebrow">Veterinarian workspace</p>
          <h2>Application status</h2>
          <p>
            {application?.full_name ? `${application.full_name} · ` : ''}
            {application?.district ? `District of ${application.district}` : 'PashuRakshak network'}
          </p>
        </div>
        <button className="btn btn-secondary btn-small" onClick={onRefresh} disabled={refreshing}>
          <Icon name="refresh" size={15} className={refreshing ? 'spin' : ''} />
          {refreshing ? 'Refreshing' : 'Refresh'}
        </button>
      </div>

      <div className={`status-banner status-${meta.tone}`}>
        <span className="status-banner-icon">
          <Icon name={meta.icon} size={22} />
        </span>
        <div>
          <span className="status-badge">{meta.label}</span>
          <h3>{meta.title}</h3>
          <p>{statusMessage}</p>
        </div>
      </div>

      <div className="status-timeline">
        {timeline.map((item, index) => (
          <div key={item.label} className={`timeline-step is-${item.state}`}>
            <span className="timeline-marker">
              {item.state === 'complete' ? (
                <Icon name="check" size={12} strokeWidth={2.5} />
              ) : item.state === 'failed' ? (
                <Icon name="alert" size={12} strokeWidth={2.2} />
              ) : (
                index + 1
              )}
            </span>
            <div>
              <strong>{item.label}</strong>
              <span>{item.text}</span>
            </div>
          </div>
        ))}
      </div>

      {application.review_note && (
        <div className="review-note">
          <span className="field-label">Reviewer note</span>
          <p>{application.review_note}</p>
        </div>
      )}

      {application && (
        <div className="status-details">
          <div className="section-heading">
            <div>
              <h3>Application details</h3>
              <p>Submitted information and coverage areas.</p>
            </div>
          </div>

          <div className="detail-grid">
            <div className="detail-item">
              <span className="field-label">Full name</span>
              <strong>{application.full_name || '—'}</strong>
            </div>
            <div className="detail-item">
              <span className="field-label">Qualification</span>
              <strong>{application.qualification || '—'}</strong>
            </div>
            <div className="detail-item">
              <span className="field-label">License number</span>
              <strong>{application.license_number || '—'}</strong>
            </div>
            <div className="detail-item">
              <span className="field-label">Experience</span>
              <strong>
                {application.experience_years != null
                  ? `${application.experience_years} year${application.experience_years === 1 ? '' : 's'}`
                  : '—'}
              </strong>
            </div>
            <div className="detail-item">
              <span className="field-label">Phone</span>
              <strong>{application.phone || '—'}</strong>
            </div>
            <div className="detail-item">
              <span className="field-label">Email</span>
              <strong>{application.email || '—'}</strong>
            </div>
            <div className="detail-item">
              <span className="field-label">Clinic</span>
              <strong>{application.clinic_name || '—'}</strong>
            </div>
            <div className="detail-item">
              <span className="field-label">Village or town</span>
              <strong>{application.village || '—'}</strong>
            </div>
            <div className="detail-item">
              <span className="field-label">Pincode</span>
              <strong>{application.pincode || '—'}</strong>
            </div>
            <div className="detail-item">
              <span className="field-label">District and state</span>
              <strong>
                {application.district || '—'}
                {application.state ? `, ${application.state}` : ''}
              </strong>
            </div>
            <div className="detail-item">
              <span className="field-label">Submitted</span>
              <strong>{fmtDate(application.created_at)}</strong>
            </div>
            <div className="detail-item">
              <span className="field-label">Last updated</span>
              <strong>{fmtDate(application.updated_at)}</strong>
            </div>
          </div>

          <div className="coverage-block">
            <span className="field-label">Coverage areas</span>
            {areas.length > 0 ? (
              <div className="tag-list">
                {areas.map((area) => (
                  <span key={area} className="tag">
                    <Icon name="pin" size={12} /> {area}
                  </span>
                ))}
              </div>
            ) : (
              <p className="muted">No coverage areas selected.</p>
            )}
          </div>

          <div className="detail-item full">
            <span className="field-label">Practice address</span>
            <strong>{application.address || '—'}</strong>
          </div>
        </div>
      )}

      <div className="btn-row">
        {status === 'rejected' || status === 'none' ? (
          <button className="btn btn-primary" onClick={onResubmit}>
            {status === 'rejected' ? 'Edit and resubmit' : 'Complete application'}
            <Icon name="arrowRight" size={15} />
          </button>
        ) : null}
        <button className="btn btn-secondary" onClick={onSignOut}>
          <Icon name="logout" size={15} /> Sign out
        </button>
      </div>
    </div>
  )
}
