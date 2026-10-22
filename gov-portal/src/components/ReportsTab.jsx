import { useState, useEffect, useCallback } from 'react'
import { getReports } from '../api'
import Icon from './Icon'

const STATUS_FILTERS = [
  'all',
  'reported',
  'vet_assigned',
  'examined',
  'sample_sent',
  'confirmed',
  'resolved',
]

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

function formatStatus(value) {
  return String(value ?? 'unknown').replace(/_/g, ' ')
}

function severityFor(report) {
  if (report.ai_risk_category) return report.ai_risk_category
  const score = Number(report.risk_score ?? 0)
  if (score >= 60) return 'high'
  if (score >= 30) return 'mid'
  return 'low'
}

function symptomsText(report) {
  const raw = report.symptoms ?? '[]'
  try {
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (Array.isArray(parsed)) return parsed.join(', ')
  } catch {
    // Keep plain-text symptoms as-is.
  }
  return String(raw || '—')
}

export default function ReportsTab() {
  const [reports, setReports] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [filter, setFilter] = useState('all')

  const load = useCallback(async () => {
    setLoading(true)
    const { status, data } = await getReports()
    if (status === 200) {
      setReports(data.reports ?? [])
      setError('')
    } else {
      setError(data.message || 'Could not load reports.')
    }
    setLoading(false)
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const filtered = filter === 'all' ? reports : reports.filter((report) => report.status === filter)
  const highRisk = reports.filter((report) => severityFor(report) === 'high').length
  const unresolved = reports.filter((report) => report.status !== 'resolved').length

  return (
    <div className="panel-card">
      <div className="panel-toolbar">
        <div>
          <p className="eyebrow">Reports</p>
          <h2>District case log</h2>
          <p>Every livestock case reported across your district.</p>
        </div>
        <button className="btn btn-secondary" onClick={load} disabled={loading}>
          <Icon name="refresh" size={16} />
          {loading ? 'Refreshing' : 'Refresh'}
        </button>
      </div>

      <div className="summary-strip">
        <div>
          <span>Total reports</span>
          <strong>{reports.length}</strong>
        </div>
        <div>
          <span>Open cases</span>
          <strong>{unresolved}</strong>
        </div>
        <div className={highRisk > 0 ? 'is-warning' : ''}>
          <span>High risk</span>
          <strong>{highRisk}</strong>
        </div>
      </div>

      <div className="filter-bar">
        <div className="segmented scrollable" role="group" aria-label="Filter reports by status">
          {STATUS_FILTERS.map((item) => (
            <button
              key={item}
              type="button"
              className={filter === item ? 'is-active' : ''}
              onClick={() => setFilter(item)}
            >
              {item === 'all' ? 'All' : formatStatus(item)}
            </button>
          ))}
        </div>
        <span className="result-count">
          {filtered.length} {filtered.length === 1 ? 'report' : 'reports'}
        </span>
      </div>

      {loading && (
        <div className="loading-panel">
          <span className="spinner spinner-dark" />
          <p>Loading reports...</p>
        </div>
      )}
      {error && (
        <p className="form-message form-message-error" role="alert">
          <Icon name="close" size={15} /> {error}
        </p>
      )}

      {!loading && !error && filtered.length === 0 && (
        <div className="empty-state">
          <span className="empty-icon">
            <Icon name="reports" size={24} />
          </span>
          <h3>No reports found</h3>
          <p>Reports matching this status will appear here.</p>
        </div>
      )}

      {!loading && filtered.length > 0 && (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Reported</th>
                <th>Animal</th>
                <th>Symptoms</th>
                <th>Village</th>
                <th>Status</th>
                <th>Risk</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((report) => (
                <tr key={report.id}>
                  <td className="date-text">{fmtDate(report.created_at)}</td>
                  <td>
                    <span className="table-primary">
                      {report.animal_species ?? report.animal_type ?? report.animalType ?? '—'}
                    </span>
                  </td>
                  <td>
                    <span className="truncate-cell" title={symptomsText(report)}>
                      {symptomsText(report)}
                    </span>
                  </td>
                  <td>{report.village ?? '—'}</td>
                  <td>
                    <span className={`status-badge status-${report.status ?? 'none'}`}>
                      {formatStatus(report.status)}
                    </span>
                  </td>
                  <td>
                    <span className={`risk-badge risk-${severityFor(report)}`}>
                      {severityFor(report)}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
