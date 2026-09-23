import { useState, useEffect, useCallback } from 'react'
import { getGovAlerts, acknowledgeAlert } from '../api'

function fmtDate(ms) {
  return ms ? new Date(Number(ms)).toLocaleString() : '—'
}

export default function GovAlertsTab() {
  const [alerts, setAlerts] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    const { status, data } = await getGovAlerts()
    if (status === 200) {
      setAlerts(data.govAlerts ?? [])
      setError('')
    } else {
      setError(data.message || 'Could not load alerts.')
    }
    setLoading(false)
  }, [])

  useEffect(() => {
    load()
  }, [load])

  async function acknowledge(id) {
    setBusyId(id)
    const { status } = await acknowledgeAlert(id)
    setBusyId(null)
    if (status === 200) load()
  }

  const unacknowledged = alerts.filter((a) => !Number(a.acknowledged))

  return (
    <div className="card">
      <div className="tab-header">
        <div>
          <h2>Government alerts</h2>
          <p className="muted">
            {unacknowledged.length} unacknowledged · {alerts.length} total
          </p>
        </div>
        <button className="btn-secondary" onClick={load} disabled={loading}>
          {loading ? 'Refreshing…' : 'Refresh'}
        </button>
      </div>

      {loading && <p className="muted">Loading…</p>}
      {error && <p className="error">{error}</p>}
      {!loading && !error && alerts.length === 0 && (
        <p className="muted">No government alerts yet.</p>
      )}

      <div className="app-list">
        {alerts.map((a) => {
          const acked = Number(a.acknowledged) === 1
          return (
            <div key={a.id} className={`alert-item ${acked ? 'alert-acked' : ''}`}>
              <div>
                <strong>{a.message ?? a.title ?? a.body ?? 'Alert'}</strong>
                <div className="muted small">
                  {a.severity ? `Severity: ${a.severity}` : ''}
                  {a.report_id ? ` · report ${a.report_id}` : ''}
                </div>
                <div className="muted small">{fmtDate(a.created_at)}</div>
              </div>
              <div className="alert-right">
                <span className={`status-badge ${acked ? 'status-approved' : 'status-pending'}`}>
                  {acked ? 'acknowledged' : 'unacknowledged'}
                </span>
                {!acked && (
                  <button
                    className="btn-secondary"
                    disabled={busyId === a.id}
                    onClick={() => acknowledge(a.id)}
                  >
                    {busyId === a.id ? '…' : 'Acknowledge'}
                  </button>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
