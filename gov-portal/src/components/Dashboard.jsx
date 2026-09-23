import { useState } from 'react'
import OverviewTab from './OverviewTab'
import ApplicationsTab from './ApplicationsTab'
import ReportsTab from './ReportsTab'
import ClustersTab from './ClustersTab'
import GovAlertsTab from './GovAlertsTab'
import UsersTab from './UsersTab'

const TABS = [
  { id: 'overview', label: 'Overview' },
  { id: 'applications', label: 'Vet Applications' },
  { id: 'reports', label: 'Reports' },
  { id: 'clusters', label: 'Clusters' },
  { id: 'alerts', label: 'Gov Alerts' },
  { id: 'users', label: 'Users' },
]

export default function Dashboard({ magistrate }) {
  const [tab, setTab] = useState('overview')
  // Bumped after approve/reject so Overview refetches when the user returns.
  const [overviewVersion, setOverviewVersion] = useState(0)

  function onApplicationReviewed() {
    setOverviewVersion((n) => n + 1)
    // Applications tab reloads itself after a successful review.
  }

  return (
    <div className="dashboard">
      <nav className="tabs" role="tablist">
        {TABS.map((t) => (
          <button
            key={t.id}
            role="tab"
            aria-selected={tab === t.id}
            className={`tab ${tab === t.id ? 'tab-active' : ''}`}
            onClick={() => setTab(t.id)}
          >
            {t.label}
          </button>
        ))}
      </nav>

      <section className="tab-panel" role="tabpanel">
        {tab === 'overview' && <OverviewTab refreshKey={overviewVersion} />}
        {tab === 'applications' && (
          <ApplicationsTab magistrate={magistrate} onReviewed={onApplicationReviewed} />
        )}
        {tab === 'reports' && <ReportsTab />}
        {tab === 'clusters' && <ClustersTab />}
        {tab === 'alerts' && <GovAlertsTab />}
        {tab === 'users' && <UsersTab />}
      </section>
    </div>
  )
}
