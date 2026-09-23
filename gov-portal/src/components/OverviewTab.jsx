import { useState, useEffect, useCallback } from 'react'
import { getOverview } from '../api'

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

  if (loading) return <div className="card center-text">Loading…</div>
  if (error) {
    return (
      <div className="card">
        <p className="error">{error}</p>
        <button className="btn-secondary" onClick={load}>Retry</button>
      </div>
    )
  }
  if (!overview) return null

  const cards = [
    { label: 'Farmers', value: overview.farmers },
    { label: 'Vets', value: overview.vets },
    {
      label: 'Vet applications pending',
      value: overview.vetApplications?.pending ?? 0,
      highlight: (overview.vetApplications?.pending ?? 0) > 0,
    },
    { label: 'Vet applications approved', value: overview.vetApplications?.approved ?? 0 },
    { label: 'Vet applications rejected', value: overview.vetApplications?.rejected ?? 0 },
    { label: 'Symptom reports', value: overview.reportsTotal },
    {
      label: 'Active clusters',
      value: overview.activeClusters,
      highlight: overview.activeClusters > 0,
    },
    {
      label: 'Gov alerts unacknowledged',
      value: overview.govAlerts?.unacknowledged ?? 0,
      highlight: (overview.govAlerts?.unacknowledged ?? 0) > 0,
    },
    { label: 'Alerts total', value: overview.alertsTotal },
  ]

  return (
    <div className="card">
      <div className="tab-header">
        <div>
          <h2>Platform overview</h2>
          <p className="muted">
            Applications scoped to {overview.district === '*' ? 'all districts' : overview.district}
          </p>
        </div>
        <button className="btn-secondary" onClick={load} disabled={loading}>
          {loading ? 'Refreshing…' : 'Refresh'}
        </button>
      </div>
      <div className="stat-grid">
        {cards.map((c) => (
          <div key={c.label} className={`stat ${c.highlight ? 'stat-highlight' : ''}`}>
            <span className="stat-value">{c.value}</span>
            <span className="stat-label">{c.label}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
