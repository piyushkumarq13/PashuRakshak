import { useState, useEffect, useCallback } from 'react'
import { getClusters } from '../api'
import Icon from './Icon'

function formatWindow(cluster) {
  if (cluster.firstReportAt) {
    const start = new Date(Number(cluster.firstReportAt)).toLocaleDateString('en-IN', {
      day: 'numeric',
      month: 'short',
    })
    const end = new Date(Number(cluster.lastReportAt ?? cluster.firstReportAt)).toLocaleDateString(
      'en-IN',
      { day: 'numeric', month: 'short' },
    )
    return start === end ? start : `${start} – ${end}`
  }
  if (cluster.windowStart) {
    return new Date(Number(cluster.windowStart)).toLocaleDateString('en-IN', {
      day: 'numeric',
      month: 'short',
    })
  }
  return 'Last 7 days'
}

export default function ClustersTab() {
  const [clusters, setClusters] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    const { status, data } = await getClusters()
    if (status === 200) {
      setClusters(data.clusters ?? [])
      setError('')
    } else {
      setError(data.message || 'Could not load clusters.')
    }
    setLoading(false)
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const reportTotal = clusters.reduce(
    (sum, cluster) => sum + Number(cluster.reportCount ?? cluster.count ?? 0),
    0,
  )

  return (
    <div className="panel-card">
      <div className="panel-toolbar">
        <div>
          <p className="eyebrow">Clusters</p>
          <h2>Live hotspots</h2>
          <p>Detected with the same 7-day, 5 km, 3-report rule used across PashuRakshak.</p>
        </div>
        <button className="btn btn-secondary" onClick={load} disabled={loading}>
          <Icon name="refresh" size={16} />
          {loading ? 'Refreshing' : 'Refresh'}
        </button>
      </div>

      <div className="summary-strip">
        <div>
          <span>Active clusters</span>
          <strong>{clusters.length}</strong>
        </div>
        <div>
          <span>Linked reports</span>
          <strong>{reportTotal}</strong>
        </div>
        <div>
          <span>Detection rule</span>
          <strong className="summary-text">7d · 5km · 3+</strong>
        </div>
      </div>

      {loading && (
        <div className="loading-panel">
          <span className="spinner spinner-dark" />
          <p>Checking for clusters...</p>
        </div>
      )}
      {error && (
        <p className="form-message form-message-error" role="alert">
          <Icon name="close" size={15} /> {error}
        </p>
      )}

      {!loading && !error && clusters.length === 0 && (
        <div className="empty-state success-state">
          <span className="empty-icon">
            <Icon name="shield" size={24} />
          </span>
          <h3>No active clusters</h3>
          <p>No linked outbreak signals are active in your district right now.</p>
        </div>
      )}

      <div className="cluster-grid">
        {clusters.map((cluster, index) => {
          const count = cluster.reportCount ?? cluster.count ?? 0
          const radius =
            cluster.radiusKm != null
              ? `${cluster.radiusKm} km`
              : cluster.radiusMeters != null
                ? `${cluster.radiusMeters} m`
                : cluster.radius ?? '—'
          return (
            <article key={cluster.id ?? index} className="cluster-card">
              <div className="cluster-visual" aria-hidden="true">
                <span className="radar-ring ring-one" />
                <span className="radar-ring ring-two" />
                <span className="radar-core">
                  <Icon name="clusters" size={20} />
                </span>
              </div>
              <div className="cluster-content">
                <div className="cluster-topline">
                  <span className="eyebrow">Cluster {String(index + 1).padStart(2, '0')}</span>
                  <span className="status-badge status-pending">{count} reports</span>
                </div>
                <h3>{cluster.areaName ?? cluster.area_name ?? 'Unnamed hotspot'}</h3>
                <div className="cluster-facts">
                  <span>
                    <Icon name="pin" size={14} />
                    {cluster.centerLat ?? cluster.lat ?? cluster.center_lat ?? '—'},{' '}
                    {cluster.centerLng ?? cluster.lng ?? cluster.center_lng ?? '—'}
                  </span>
                  <span>
                    <Icon name="activity" size={14} />
                    {radius} radius
                  </span>
                  <span>
                    <Icon name="calendar" size={14} />
                    {formatWindow(cluster)}
                  </span>
                </div>
                <div className="cluster-foot">
                  <span className="status-dot" />
                  Active monitoring
                </div>
              </div>
            </article>
          )
        })}
      </div>
    </div>
  )
}
