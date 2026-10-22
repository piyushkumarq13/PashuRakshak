import { useState, useEffect, useCallback } from 'react'
import { getUsers } from '../api'
import Icon from './Icon'

function fmtDate(ms) {
  if (!ms) return '—'
  return new Date(Number(ms)).toLocaleDateString('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
}

function initials(name) {
  return String(name || '?')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join('')
    .toUpperCase()
}

export default function UsersTab() {
  const [users, setUsers] = useState({ farmers: [], vets: [] })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [role, setRole] = useState('farmers')

  const load = useCallback(async () => {
    setLoading(true)
    const { status, data } = await getUsers()
    if (status === 200) {
      setUsers({ farmers: data.farmers ?? [], vets: data.vets ?? [] })
      setError('')
    } else {
      setError(data.message || 'Could not load users.')
    }
    setLoading(false)
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const rows = users[role]

  return (
    <div className="panel-card">
      <div className="panel-toolbar">
        <div>
          <p className="eyebrow">Directory</p>
          <h2>Network users</h2>
          <p>Farmers and veterinarians connected to your district.</p>
        </div>
        <button className="btn btn-secondary" onClick={load} disabled={loading}>
          <Icon name="refresh" size={16} />
          {loading ? 'Refreshing' : 'Refresh'}
        </button>
      </div>

      <div className="summary-strip">
        <div>
          <span>Farmers</span>
          <strong>{users.farmers.length}</strong>
        </div>
        <div>
          <span>Veterinarians</span>
          <strong>{users.vets.length}</strong>
        </div>
        <div>
          <span>Total users</span>
          <strong>{users.farmers.length + users.vets.length}</strong>
        </div>
      </div>

      <div className="filter-bar">
        <div className="segmented" role="group" aria-label="Filter users by role">
          <button
            type="button"
            className={role === 'farmers' ? 'is-active' : ''}
            onClick={() => setRole('farmers')}
          >
            Farmers
          </button>
          <button
            type="button"
            className={role === 'vets' ? 'is-active' : ''}
            onClick={() => setRole('vets')}
          >
            Veterinarians
          </button>
        </div>
        <span className="result-count">
          {rows.length} {role === 'farmers' ? 'farmer' : 'veterinarian'}
          {rows.length === 1 ? '' : 's'}
        </span>
      </div>

      {loading && (
        <div className="loading-panel">
          <span className="spinner spinner-dark" />
          <p>Loading directory...</p>
        </div>
      )}
      {error && (
        <p className="form-message form-message-error" role="alert">
          <Icon name="close" size={15} /> {error}
        </p>
      )}

      {!loading && !error && rows.length === 0 && (
        <div className="empty-state">
          <span className="empty-icon">
            <Icon name="users" size={24} />
          </span>
          <h3>No {role} yet</h3>
          <p>New {role === 'farmers' ? 'farmers' : 'veterinarians'} will appear here.</p>
        </div>
      )}

      {!loading && rows.length > 0 && (
        <div className="table-wrap">
          <table className="data-table">
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
              {rows.map((user) => (
                <tr key={user.id}>
                  <td>
                    <div className="person-cell">
                      <span className="person-avatar small">{initials(user.name)}</span>
                      <span className="table-primary">{user.name || '—'}</span>
                    </div>
                  </td>
                  <td>{user.phone || '—'}</td>
                  <td>{user.email || '—'}</td>
                  <td>{role === 'farmers' ? user.village || '—' : user.pincode || '—'}</td>
                  <td>
                    {role === 'farmers'
                      ? user.animal_count ?? '—'
                      : `${user.district || '—'}${
                          user.application_status ? ` · ${user.application_status}` : ''
                        }`}
                  </td>
                  <td className="date-text">{fmtDate(user.created_at)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
