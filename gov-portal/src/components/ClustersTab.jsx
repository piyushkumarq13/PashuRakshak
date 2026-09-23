import { useState, useEffect } from 'react'
import { getClusters } from '../api'

export default function ClustersTab() {
  const [clusters, setClusters] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    getClusters().then(({ status, data }) => {
      if (cancelled) return
      if (status === 200) {
        setClusters(data.clusters ?? [])
        setError('')
      } else {
        setError(data.message || 'Could not load clusters.')
      }
      setLoading(false)
    })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div className="card">
      <h2>Active outbreak clusters</h2>
      <p className="muted">
        7-day / 5 km / 3+ reports rule — the same detection the vets and farmers see.
      </p>

      {loading && <p className="muted">Loading…</p>}
      {error && <p className="error">{error}</p>}
      {!loading && !error && clusters.length === 0 && (
        <p className="success">No active clusters right now.</p>
      )}

      <div className="app-list">
        {(clusters ?? []).map((c, i) => (
          <div key={c.id ?? i} className="cluster-item">
            <div className="cluster-header">
              <strong>{c.areaName ?? c.area_name ?? `Cluster ${i + 1}`}</strong>
              <span className="status-badge status-pending">{c.reportCount ?? c.count ?? 0} reports</span>
            </div>
            <div className="detail-grid">
              <div>
                <span className="field-label">Location</span>
                <span>
                  {c.lat ?? c.center_lat ?? '—'}, {c.lng ?? c.center_lng ?? '—'}
                </span>
              </div>
              <div>
                <span className="field-label">Radius</span>
                <span>{c.radiusMeters ?? c.radius ?? '—'} m</span>
              </div>
              <div>
                <span className="field-label">Window</span>
                <span>
                  {c.windowStart ? new Date(Number(c.windowStart)).toLocaleDateString() : 'last 7 days'}
                </span>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
