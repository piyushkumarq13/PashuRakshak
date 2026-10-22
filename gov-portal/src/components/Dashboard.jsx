import { useState } from 'react'
import OverviewTab from './OverviewTab'
import ApplicationsTab from './ApplicationsTab'
import ReportsTab from './ReportsTab'
import ClustersTab from './ClustersTab'
import GovAlertsTab from './GovAlertsTab'
import UsersTab from './UsersTab'
import Icon from './Icon'

const TABS = [
  {
    id: 'overview',
    label: 'Overview',
    icon: 'overview',
    title: 'District overview',
    description: 'A live snapshot of your livestock health network.',
  },
  {
    id: 'applications',
    label: 'Vet Applications',
    icon: 'applications',
    title: 'Veterinarian applications',
    description: 'Review credentials and decide who can serve your district.',
  },
  {
    id: 'reports',
    label: 'Reports',
    icon: 'reports',
    title: 'Symptom reports',
    description: 'Follow cases from first report to resolution.',
  },
  {
    id: 'clusters',
    label: 'Clusters',
    icon: 'clusters',
    title: 'Outbreak clusters',
    description: 'Watch emerging hotspots using the 7-day, 5 km rule.',
  },
  {
    id: 'alerts',
    label: 'Gov Alerts',
    icon: 'alerts',
    title: 'Government alerts',
    description: 'Acknowledge critical notifications and keep the response moving.',
  },
  {
    id: 'users',
    label: 'Users',
    icon: 'users',
    title: 'Network users',
    description: 'See farmers and veterinarians connected to your district.',
  },
]

export default function Dashboard({ magistrate, onSignOut }) {
  const [tab, setTab] = useState('overview')
  const [overviewVersion, setOverviewVersion] = useState(0)

  const activeTab = TABS.find((item) => item.id === tab) ?? TABS[0]
  const scope = magistrate?.district === '*' ? 'All districts' : magistrate?.district || 'Your district'
  const today = new Date().toLocaleDateString('en-IN', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  })

  function onApplicationReviewed() {
    setOverviewVersion((n) => n + 1)
  }

  return (
    <div className="dashboard-shell">
      <aside className="sidebar">
        <div className="sidebar-scope">
          <span className="scope-icon">
            <Icon name="shield" size={18} />
          </span>
          <div>
            <span>Active scope</span>
            <strong>{scope}</strong>
          </div>
        </div>

        <nav className="side-nav" aria-label="Dashboard sections">
          <p className="nav-label">Workspace</p>
          {TABS.map((item) => (
            <button
              key={item.id}
              type="button"
              className={`nav-item ${tab === item.id ? 'is-active' : ''}`}
              aria-current={tab === item.id ? 'page' : undefined}
              onClick={() => setTab(item.id)}
            >
              <Icon name={item.icon} size={18} />
              <span>{item.label}</span>
              <Icon name="chevron" size={14} className="nav-chevron" />
            </button>
          ))}
        </nav>

        <div className="sidebar-card">
          <span className="sidebar-card-icon">
            <Icon name="calendar" size={17} />
          </span>
          <div>
            <strong>{today}</strong>
            <span>District operations</span>
          </div>
        </div>

        <button className="sidebar-signout" type="button" onClick={onSignOut}>
          <Icon name="logout" size={16} />
          Sign out securely
        </button>
      </aside>

      <section className="workspace">
        <div className="workspace-header">
          <div>
            <p className="eyebrow">District console</p>
            <h1>{activeTab.title}</h1>
            <p>{activeTab.description}</p>
          </div>
          <div className="workspace-meta">
            <span>
              <Icon name="clock" size={14} />
              Live data
            </span>
            <span>
              <Icon name="shield" size={14} />
              {scope}
            </span>
          </div>
        </div>

        <nav className="mobile-nav" aria-label="Dashboard sections">
          {TABS.map((item) => (
            <button
              key={item.id}
              type="button"
              className={`mobile-nav-item ${tab === item.id ? 'is-active' : ''}`}
              onClick={() => setTab(item.id)}
            >
              <Icon name={item.icon} size={17} />
              {item.label}
            </button>
          ))}
        </nav>

        <section className="tab-panel" role="tabpanel" aria-label={activeTab.title}>
          {tab === 'overview' && <OverviewTab refreshKey={overviewVersion} />}
          {tab === 'applications' && (
            <ApplicationsTab magistrate={magistrate} onReviewed={onApplicationReviewed} />
          )}
          {tab === 'reports' && <ReportsTab />}
          {tab === 'clusters' && <ClustersTab />}
          {tab === 'alerts' && <GovAlertsTab />}
          {tab === 'users' && <UsersTab />}
        </section>
      </section>
    </div>
  )
}
