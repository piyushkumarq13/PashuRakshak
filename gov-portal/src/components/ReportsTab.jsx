import { useState, useEffect } from 'react'
import { getReports } from '../api'

function fmtDate(ms) {
  return ms ? new Date(Number(ms)).toLocaleString() : '—'
}

export default function ReportsTab() {
  const [reports, setReports] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [filter, setFilter] = useState('all')

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    getReports().then(({ status, data }) => {
      if (cancelled) return
      if (status === 200) {
        setReports(data.reports ?? [])
        setError('')
      } else {
        setError(data.message || 'Could not load reports.')
      }
      setLoading(false)
    })
    return () => {
      cancelled = true
    }
  }, [])

  const filtered = filter === 'all' ? reports : reports.filter((r) => r.status === filter)

  return (
    <div className="card">
      <div className="tab-header">
        <div>
          <h2>Symptom reports</h2>
          <p className="muted">{reports.length} total</p>
        </div>
        <div className="filter-row">
          {['all', 'pending', 'assigned', 'resolved'].map((s) => (
            <button
              key={s}
              className={`chip ${filter === s ? 'chip-active' : ''}`}
              onClick={() => setFilter(s)}
            >
              {s}
            </button>
          ))}
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
              <th>Severity</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((r) => (
              <tr key={r.id}>
                <td>{fmtDate(r.created_at)}</td>
                <td>{r.animal_type ?? r.animalType ?? '—'}</td>
                <td className="cell-truncate">
                  {r.symptoms ?? r.symptom_text ?? r.symptoms_text ?? '—'}
                </td>
                <td>{r.village ?? '—'}</td>
                <td>
                  <span className={`status-badge status-${r.status ?? 'none'}`}>
                    {r.status ?? 'unknown'}
                  </span>
                </td>
                <td>{r.severity ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
