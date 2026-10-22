import { useState, useEffect, useCallback } from 'react'
import { getGovAlerts, acknowledgeAlert } from '../api'
import Icon from './Icon'

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

export default function GovAlertsTab() {
  const [alerts, setAlerts] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState(null)
  const [ackError, setAckError] = useState('')

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
    setAckError('')
    setBusyId(id)
    const { status, data } = await acknowledgeAlert(id)
    setBusyId(null)
    if (status === 200) {
      load()
    } else {
      setAckError(data?.message || 'Could not acknowledge this alert.')
    }
  }

  const unacknowledged = alerts.filter((alert) => !Number(alert.acknowledged))

  return (
    <div className="panel-card">
      <div className="panel-toolbar">
        <div>
          <p className="eyebrow">Alerts</p>
          <h2>Response register</h2>
          <p>Review and acknowledge critical district notifications.</p>
        </div>
        <button className="btn btn-secondary" onClick={load} disabled={loading}>
          <Icon name="refresh" size={16} />
          {loading ? 'Refreshing' : 'Refresh'}
        </button>
      </div>

      <div className="summary-strip">
        <div className={unacknowledged.length > 0 ? 'is-warning' : ''}>
          <span>Unacknowledged</span>
          <strong>{unacknowledged.length}</strong>
        </div>
        <div>
          <span>Total alerts</span>
          <strong>{alerts.length}</strong>
        </div>
        <div>
          <span>Acknowledged</span>
          <strong>{alerts.length - unacknowledged.length}</strong>
        </div>
      </div>

      {loading && (
        <div className="loading-panel">
          <span className="spinner spinner-dark" />
          <p>Loading alerts...</p>
        </div>
      )}
      {error && (
        <p className="form-message form-message-error" role="alert">
          <Icon name="close" size={15} /> {error}
        </p>
      )}
      {ackError && (
        <p className="form-message form-message-error" role="alert">
          <Icon name="close" size={15} /> {ackError}
        </p>
      )}

      {!loading && !error && alerts.length === 0 && (
        <div className="empty-state success-state">
          <span className="empty-icon">
            <Icon name="shield" size={24} />
          </span>
          <h3>No government alerts</h3>
          <p>New district alerts will appear here when they are issued.</p>
        </div>
      )}

      <div className="alert-list">
        {alerts.map((alert) => {
          const acked = Number(alert.acknowledged) === 1
          return (
            <article key={alert.id} className={`alert-card ${acked ? 'is-acked' : ''}`}>
              <span className={`alert-icon severity-${alert.severity ?? 'medium'}`}>
                <Icon name={acked ? 'check' : 'alerts'} size={19} />
              </span>
              <div className="alert-content">
                <div className="alert-topline">
                  <strong>{alert.message ?? alert.title ?? alert.body ?? 'Government alert'}</strong>
                  <span className={`status-badge ${acked ? 'status-approved' : 'status-pending'}`}>
                    {acked ? 'acknowledged' : 'unacknowledged'}
                  </span>
                </div>
                <div className="alert-meta">
                  {alert.severity && <span>Severity: {alert.severity}</span>}
                  {alert.report_id && <span>Report: {alert.report_id.slice(0, 8)}</span>}
                  <span>{fmtDate(alert.created_at)}</span>
                </div>
              </div>
              {!acked && (
                <button
                  className="btn btn-secondary btn-small"
                  disabled={busyId === alert.id}
                  onClick={() => acknowledge(alert.id)}
                >
                  {busyId === alert.id ? <span className="spinner spinner-dark" /> : <Icon name="check" size={15} />}
                  {busyId === alert.id ? 'Saving' : 'Acknowledge'}
                </button>
              )}
            </article>
          )
        })}
      </div>
    </div>
  )
}
