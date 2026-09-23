import { useState, useEffect } from 'react'
import { getUsers } from '../api'

function fmtDate(ms) {
  return ms ? new Date(Number(ms)).toLocaleDateString() : '—'
}

export default function UsersTab() {
  const [users, setUsers] = useState({ farmers: [], vets: [] })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [role, setRole] = useState('farmers')

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    getUsers().then(({ status, data }) => {
      if (cancelled) return
      if (status === 200) {
        setUsers({ farmers: data.farmers ?? [], vets: data.vets ?? [] })
        setError('')
      } else {
        setError(data.message || 'Could not load users.')
      }
      setLoading(false)
    })
    return () => {
      cancelled = true
    }
  }, [])

  const rows = users[role]

  return (
    <div className="card">
      <div className="tab-header">
        <div>
          <h2>Users</h2>
          <p className="muted">
            {users.farmers.length} farmers · {users.vets.length} vets
          </p>
        </div>
        <div className="filter-row">
          <button
            className={`chip ${role === 'farmers' ? 'chip-active' : ''}`}
            onClick={() => setRole('farmers')}
          >
            Farmers
          </button>
          <button
            className={`chip ${role === 'vets' ? 'chip-active' : ''}`}
            onClick={() => setRole('vets')}
          >
            Vets
          </button>
        </div>
      </div>

      {loading && <p className="muted">Loading…</p>}
      {error && <p className="error">{error}</p>}

      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Phone</th>
              <th>Email</th>
              <th>{role === 'farmers' ? 'Village' : 'Pincode'}</th>
              <th>{role === 'farmers' ? 'Animals' : 'District / status'}</th>
              <th>Joined</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((u) => (
              <tr key={u.id}>
                <td>{u.name || '—'}</td>
                <td>{u.phone || '—'}</td>
                <td>{u.email || '—'}</td>
                <td>{role === 'farmers' ? u.village || '—' : u.pincode || '—'}</td>
                <td>
                  {role === 'farmers'
                    ? u.animal_count ?? '—'
                    : `${u.district || '—'}${
                        u.application_status ? ` · ${u.application_status}` : ''
                      }`}
                </td>
                <td>{fmtDate(u.created_at)}</td>
              </tr>
            ))}
            {!loading && rows.length === 0 && (
              <tr>
                <td colSpan={6} className="muted center-text">
                  No {role} yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
