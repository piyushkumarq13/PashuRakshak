import { useState, useEffect, useCallback } from 'react'
import { getOverview } from '../api'
import Icon from './Icon'

export default function OverviewTab({ refreshKey = 0 }) {
  const [overview, setOverview] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    const { status, data } = await getOverview()
    if (status === 200) {
      setOverview(data.overview)
      setError('')
    } else {
      setError(data.message || 'Could not load the overview.')
    }
    setLoading(false)
  }, [])

  useEffect(() => {
    load()
  }, [load, refreshKey])

  if (loading && !overview) {
    return (
      <div className="panel-card loading-panel">
        <span className="spinner spinner-dark" />
        <p>Loading district overview...</p>
      </div>
    )
  }

  if (error && !overview) {
    return (
      <div className="panel-card empty-panel">
        <span className="empty-icon">
          <Icon name="alerts" size={24} />
        </span>
        <h2>Overview unavailable</h2>
        <p>{error}</p>
        <button className="btn btn-secondary" onClick={load}>
          <Icon name="refresh" size={16} /> Try again
        </button>
      </div>
    )
  }

  if (!overview) return null

  const pendingApplications = overview.vetApplications?.pending ?? 0
  const activeClusters = overview.activeClusters ?? 0
  const unacknowledgedAlerts = overview.govAlerts?.unacknowledged ?? 0
  const attentionCount = pendingApplications + activeClusters + unacknowledgedAlerts

  const primaryCards = [
    {
      label: 'Registered farmers',
      value: overview.farmers ?? 0,
      icon: 'users',
      tone: 'green',
      hint: 'Across your district',
    },
    {
      label: 'Active veterinarians',
      value: overview.vets ?? 0,
      icon: 'shield',
      tone: 'teal',
      hint: 'Available in the network',
    },
    {
      label: 'Symptom reports',
      value: overview.reportsTotal ?? 0,
      icon: 'reports',
      tone: 'blue',
      hint: 'All time',
    },
    {
      label: 'Active clusters',
      value: activeClusters,
      icon: 'clusters',
      tone: activeClusters > 0 ? 'amber' : 'slate',
      hint: activeClusters > 0 ? 'Needs review' : 'No hotspots',
    },
  ]

  const applicationCards = [
    {
      label: 'Pending review',
      value: pendingApplications,
      icon: 'clock',
      tone: pendingApplications > 0 ? 'amber' : 'slate',
    },
    {
      label: 'Approved',
      value: overview.vetApplications?.approved ?? 0,
      icon: 'check',
      tone: 'green',
    },
    {
      label: 'Rejected',
      value: overview.vetApplications?.rejected ?? 0,
      icon: 'close',
      tone: 'red',
    },
  ]

  const attentionItems = [
    {
      label: 'Vet applications waiting',
      value: pendingApplications,
      icon: 'applications',
      tone: 'amber',
      copy: 'Review credentials to keep the network growing.',
    },
    {
      label: 'Clusters to investigate',
      value: activeClusters,
      icon: 'clusters',
      tone: 'red',
      copy: 'Multiple reports are linked by time and distance.',
    },
    {
      label: 'Alerts to acknowledge',
      value: unacknowledgedAlerts,
      icon: 'alerts',
      tone: 'blue',
      copy: 'Confirm the district has seen each critical alert.',
    },
  ]

  return (
    <div className="panel-stack">
      <section className="panel-card overview-hero">
        <div className="overview-hero-copy">
          <span className="hero-chip">
            <span className="status-dot" /> Live district snapshot
          </span>
          <h2>{attentionCount > 0 ? `${attentionCount} items need your attention` : 'Your district is up to date'}</h2>
          <p>
            {attentionCount > 0
              ? 'Clear the review queue, check outbreak signals, and acknowledge alerts to keep response times short.'
              : 'No pending reviews, active clusters, or unacknowledged alerts right now.'}
          </p>
        </div>
        <button className="btn btn-secondary on-dark" onClick={load} disabled={loading}>
          <Icon name="refresh" size={16} />
          {loading ? 'Refreshing' : 'Refresh data'}
        </button>
        <div className="overview-orbit" aria-hidden="true">
          <span />
          <span />
          <span />
        </div>
      </section>

      <div className="kpi-grid">
        {primaryCards.map((card) => (
          <article key={card.label} className={`kpi-card tone-${card.tone}`}>
            <span className="kpi-icon">
              <Icon name={card.icon} size={19} />
            </span>
            <div className="kpi-copy">
              <span>{card.label}</span>
              <strong>{card.value}</strong>
              <small>{card.hint}</small>
            </div>
          </article>
        ))}
      </div>

      <div className="overview-columns">
        <section className="panel-card">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Applications</p>
              <h2>Veterinarian pipeline</h2>
            </div>
          </div>
          <div className="mini-stat-grid">
            {applicationCards.map((card) => (
              <div key={card.label} className={`mini-stat tone-${card.tone}`}>
                <span className="mini-stat-icon">
                  <Icon name={card.icon} size={16} />
                </span>
                <strong>{card.value}</strong>
                <span>{card.label}</span>
              </div>
            ))}
          </div>
          <div className="section-divider" />
          <div className="inline-summary">
            <span>
              <Icon name="alerts" size={16} /> {overview.alertsTotal ?? 0} total alerts generated
            </span>
            <span>
              <Icon name="activity" size={16} /> {unacknowledgedAlerts} awaiting acknowledgement
            </span>
          </div>
        </section>

        <section className="panel-card">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Priority queue</p>
              <h2>Needs attention</h2>
            </div>
            <span className="count-badge">{attentionCount}</span>
          </div>
          <div className="attention-list">
            {attentionItems.map((item) => (
              <div key={item.label} className="attention-row">
                <span className={`attention-icon tone-${item.tone}`}>
                  <Icon name={item.icon} size={17} />
                </span>
                <div>
                  <strong>
                    {item.value} {item.label}
                  </strong>
                  <span>{item.copy}</span>
                </div>
                <span className={`attention-count tone-${item.tone}`}>{item.value}</span>
              </div>
            ))}
          </div>
        </section>
      </div>
    </div>
  )
}
