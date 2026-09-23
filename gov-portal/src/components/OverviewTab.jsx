import { useState, useEffect } from 'react'
import { getOverview } from '../api'

export default function OverviewTab() {
  const [overview, setOverview] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    getOverview().then(({ status, data }) => {
      if (cancelled) return
      if (status === 200) {
        setOverview(data.overview)
        setError('')
      } else {
        setError(data.message || 'Could not load the overview.')
      }
      setLoading(false)
    })
    return () => {
      cancelled = true
    }
  }, [])

  if (loading) return <div className="card center-text">Loading…</div>
  if (error) return <div className="card"><p className="error">{error}</p></div>
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
      <h2>Platform overview</h2>
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
