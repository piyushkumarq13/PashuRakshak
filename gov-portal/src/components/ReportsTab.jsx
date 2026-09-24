import { useState, useEffect, useCallback } from 'react'
import { getReports } from '../api'

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
  return ms ? new Date(Number(ms)).toLocaleString() : '—'
}

function severityFor(r) {
  if (r.ai_risk_category) return r.ai_risk_category
  const score = Number(r.risk_score ?? 0)
  if (score >= 60) return 'high'
  if (score >= 30) return 'mid'
  return 'low'
}

function symptomsText(r) {
  const raw = r.symptoms ?? '[]'
  try {
    const arr = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (Array.isArray(arr)) return arr.join(', ')
  } catch {
    /* plain string */
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

  const filtered = filter === 'all' ? reports : reports.filter((r) => r.status === filter)

  return (
    <div className="card">
      <div className="tab-header">
        <div>
          <h2>Symptom reports</h2>
          <p className="muted">{reports.length} total</p>
        </div>
        <div className="filter-row">
          {STATUS_FILTERS.map((s) => (
            <button
              key={s}
              className={`chip ${filter === s ? 'chip-active' : ''}`}
              onClick={() => setFilter(s)}
            >
              {s}
            </button>
          ))}
          <button className="btn-secondary" onClick={load} disabled={loading}>
            {loading ? 'Refreshing…' : 'Refresh'}
          </button>
        </div>
      </div>

      {loading && <p className="muted">Loading…</p>}
      {error && <p className="error">{error}</p>}
      {!loading && !error && filtered.length === 0 && (
        <p className="muted">No reports{filter !== 'all' ? ` with status "${filter}"` : ''}.</p>
      )}

      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Date</th>
              <th>Animal</th>
              <th>Symptoms</th>
              <th>Village</th>
              <th>Status</th>
              <th>Risk</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((r) => (
              <tr key={r.id}>
                <td>{fmtDate(r.created_at)}</td>
                <td>{r.animal_species ?? r.animal_type ?? r.animalType ?? '—'}</td>
                <td className="cell-truncate">{symptomsText(r)}</td>
                <td>{r.village ?? '—'}</td>
                <td>
                  <span className={`status-badge status-${r.status ?? 'none'}`}>
                    {(r.status ?? 'unknown').replace(/_/g, ' ')}
                  </span>
                </td>
                <td>
                  <span className={`risk-badge risk-${severityFor(r)}`}>
                    {severityFor(r)}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
