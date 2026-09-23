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
        {tab === 'overview' && <OverviewTab />}
        {tab === 'applications' && <ApplicationsTab magistrate={magistrate} />}
        {tab === 'reports' && <ReportsTab />}
        {tab === 'clusters' && <ClustersTab />}
        {tab === 'alerts' && <GovAlertsTab />}
        {tab === 'users' && <UsersTab />}
      </section>
    </div>
  )
}
