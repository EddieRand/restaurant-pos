import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { useTranslation } from 'react-i18next'
import { aiWorkspaceCopy } from '../i18n/aiWorkspace'
import { useState } from 'react'

const NAV_ICONS = {
  ai: (
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 20 20" fill="currentColor">
      <path d="M10 1.75a.75.75 0 01.72.54l.68 2.4a5.1 5.1 0 003.5 3.5l2.4.68a.75.75 0 010 1.44l-2.4.68a5.1 5.1 0 00-3.5 3.5l-.68 2.4a.75.75 0 01-1.44 0l-.68-2.4a5.1 5.1 0 00-3.5-3.5l-2.4-.68a.75.75 0 010-1.44l2.4-.68a5.1 5.1 0 003.5-3.5l.68-2.4a.75.75 0 01.72-.54z" />
    </svg>
  ),
  dashboard: (
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 20 20" fill="currentColor">
      <path d="M10.707 2.293a1 1 0 00-1.414 0l-7 7a1 1 0 001.414 1.414L4 10.414V17a1 1 0 001 1h2a1 1 0 001-1v-2a1 1 0 011-1h2a1 1 0 011 1v2a1 1 0 001 1h2a1 1 0 001-1v-6.586l.293.293a1 1 0 001.414-1.414l-7-7z" />
    </svg>
  ),
  menu: (
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 20 20" fill="currentColor">
      <path d="M9 2a1 1 0 000 2h2a1 1 0 100-2H9z" />
      <path fillRule="evenodd" d="M4 5a2 2 0 012-2 3 3 0 003 3h2a3 3 0 003-3 2 2 0 012 2v11a2 2 0 01-2 2H6a2 2 0 01-2-2V5zm3 4a1 1 0 100 2h.01a1 1 0 100-2H7zm3 0a1 1 0 100 2h3a1 1 0 100-2h-3zm-3 4a1 1 0 100 2h.01a1 1 0 100-2H7zm3 0a1 1 0 100 2h3a1 1 0 100-2h-3z" clipRule="evenodd" />
    </svg>
  ),
  tables: (
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 20 20" fill="currentColor">
      <path fillRule="evenodd" d="M5 3a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2V5a2 2 0 00-2-2H5zm4.707 3.707a1 1 0 00-1.414-1.414l-3 3a1 1 0 000 1.414l3 3a1 1 0 001.414-1.414L8.414 9H10a3 3 0 013 3v1a1 1 0 102 0v-1a5 5 0 00-5-5H8.414l1.293-1.293z" clipRule="evenodd" />
    </svg>
  ),
  kitchen: (
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="3" width="14" height="10" rx="1.5" fill="currentColor" stroke="none" />
      <path d="M3 7h14M6 10h2M11 10h2" />
    </svg>
  ),
  kiosk: (
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <rect x="4" y="2" width="12" height="16" rx="2" fill="currentColor" stroke="none" />
      <path d="M8 15h4" stroke="white" />
      <path d="M7 5h6M7 8h6M7 11h3" stroke="white" strokeWidth="1.2" />
    </svg>
  ),
  pad: (
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <rect x="2" y="4" width="16" height="12" rx="2" fill="currentColor" stroke="none" />
      <path d="M6 8h4M6 11h6" stroke="white" strokeWidth="1.2" />
      <circle cx="14" cy="11.5" r="1.4" fill="white" stroke="none" />
    </svg>
  ),
  cds: (
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <rect x="1" y="3" width="18" height="12" rx="2" fill="currentColor" stroke="none" />
      <path d="M2 5h16M2 13h16" stroke="white" strokeWidth="1" />
      <path d="M7 17h6M10 15v2" stroke="currentColor" strokeWidth="1.5" />
    </svg>
  ),
  qr: (
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 20 20" fill="currentColor">
      <path d="M3 3h5v5H3V3zm2 2v1h1V5H5zm7-2h5v5h-5V3zm2 2v1h1V5h-1zM3 12h5v5H3v-5zm2 2v1h1v-1H5zm8-4h2v2h-2v-2zm-3 0h2v3h-2v-3zm5 3h2v4h-4v-2h2v-2zm-5 2h2v2h-2v-2z" />
    </svg>
  ),
  reports: (
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 20 20" fill="currentColor">
      <path d="M2 11a1 1 0 011-1h2a1 1 0 011 1v5a1 1 0 01-1 1H3a1 1 0 01-1-1v-5zM8 7a1 1 0 011-1h2a1 1 0 011 1v9a1 1 0 01-1 1H9a1 1 0 01-1-1V7zM14 4a1 1 0 011-1h2a1 1 0 011 1v12a1 1 0 01-1 1h-2a1 1 0 01-1-1V4z" />
    </svg>
  ),
  orders: (
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 20 20" fill="currentColor">
      <path d="M3 1a1 1 0 000 2h1.22l.305 1.222a.997.997 0 00.01.042l1.358 5.43-.893.892C3.74 11.846 4.632 14 6.414 14H15a1 1 0 000-2H6.414l1-1H14a1 1 0 00.894-.553l3-6A1 1 0 0017 3H6.28l-.31-1.243A1 1 0 005 1H3zM16 16.5a1.5 1.5 0 11-3 0 1.5 1.5 0 013 0zM6.5 18a1.5 1.5 0 11-3 0 1.5 1.5 0 013 0z" />
    </svg>
  ),
  vouchers: (
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 20 20" fill="currentColor">
      <path d="M3 4a2 2 0 012-2h10a2 2 0 012 2v3a2 2 0 000 4v3a2 2 0 01-2 2H5a2 2 0 01-2-2v-3a2 2 0 000-4V4zm6 1a1 1 0 102 0 1 1 0 00-2 0zm0 4a1 1 0 102 0 1 1 0 00-2 0zm0 4a1 1 0 102 0 1 1 0 00-2 0z" />
    </svg>
  ),
  users: (
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 20 20" fill="currentColor">
      <path d="M9 6a3 3 0 11-6 0 3 3 0 016 0zM17 6a3 3 0 11-6 0 3 3 0 016 0zM12.93 17c.046-.327.07-.66.07-1a6.97 6.97 0 00-1.5-4.33A5 5 0 0119 16v1h-6.07zM6 11a5 5 0 015 5v1H1v-1a5 5 0 015-5z" />
    </svg>
  ),
  inventory: (
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 20 20" fill="currentColor">
      <path d="M4 3a2 2 0 100 4h12a2 2 0 100-4H4z" />
      <path fillRule="evenodd" d="M3 8h14v7a2 2 0 01-2 2H5a2 2 0 01-2-2V8zm5 3a1 1 0 011-1h2a1 1 0 110 2H9a1 1 0 01-1-1zm0 5a1 1 0 011-1h2a1 1 0 110 2H9a1 1 0 01-1-1z" clipRule="evenodd" />
    </svg>
  ),
  crm: (
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 20 20" fill="currentColor">
      <path fillRule="evenodd" d="M5 2a2 2 0 00-2 2v14l3.5-2 3.5 2 3.5-2 3.5 2V4a2 2 0 00-2-2H5zm4.707 3.707a1 1 0 00-1.414-1.414l-3 3a1 1 0 000 1.414l3 3a1 1 0 001.414-1.414L8.414 9H10a3 3 0 013 3v1a1 1 0 102 0v-1a5 5 0 00-5-5H8.414l1.293-1.293z" clipRule="evenodd" />
    </svg>
  ),
  reservations: (
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 20 20" fill="currentColor">
      <path fillRule="evenodd" d="M6 2a1 1 0 00-1 1v1H4a2 2 0 00-2 2v10a2 2 0 002 2h12a2 2 0 002-2V6a2 2 0 00-2-2h-1V3a1 1 0 10-2 0v1H7V3a1 1 0 00-1-1zm0 5a1 1 0 000 2h8a1 1 0 100-2H6z" clipRule="evenodd" />
    </svg>
  ),
  settings: (
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 20 20" fill="currentColor">
      <path fillRule="evenodd" d="M11.49 3.17c-.38-1.56-2.6-1.56-2.98 0a1.532 1.532 0 01-2.286.948c-1.372-.836-2.942.734-2.106 2.106.54.886.061 2.042-.947 2.287-1.561.379-1.561 2.6 0 2.978a1.532 1.532 0 01.947 2.287c-.836 1.372.734 2.942 2.106 2.106a1.532 1.532 0 012.287.947c.379 1.561 2.6 1.561 2.978 0a1.533 1.533 0 012.287-.947c1.372.836 2.942-.734 2.106-2.106a1.533 1.533 0 01.947-2.287c1.561-.379 1.561-2.6 0-2.978a1.532 1.532 0 01-.947-2.287c.836-1.372-.734-2.942-2.106-2.106a1.532 1.532 0 01-2.287-.947zM10 13a3 3 0 100-6 3 3 0 000 6z" clipRule="evenodd" />
    </svg>
  ),
  permissions: (
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 20 20" fill="currentColor">
      <path fillRule="evenodd" d="M5 9V7a5 5 0 0110 0v2a2 2 0 012 2v5a2 2 0 01-2 2H5a2 2 0 01-2-2v-5a2 2 0 012-2zm8-2v2H7V7a3 3 0 016 0z" clipRule="evenodd" />
    </svg>
  ),
}

const NAV_ROUTES = [
  { to: '/',        end: true,  key: 'dashboard' as const },
  { to: '/ai',                   key: 'ai'        as const },
  { to: '/menu',                 key: 'menu'      as const },
  { to: '/tables',               key: 'tables'    as const },
  { to: '/kitchen',             key: 'kitchen'   as const },
  { to: '/kiosk',               key: 'kiosk'     as const },
  { to: '/pad',                 key: 'pad'       as const },
  { to: '/cds',                 key: 'cds'       as const },
  { to: '/qr-ordering',         key: 'qr'        as const },
  { to: '/inventory',          key: 'inventory' as const },
  { to: '/reservations',         key: 'reservations' as const },
  { to: '/crm',                 key: 'crm'       as const },
  { to: '/reports',              key: 'reports'   as const },
  { to: '/orders',               key: 'orders'    as const },
  { to: '/group-buying',         key: 'vouchers'  as const },
  { to: '/users',                key: 'users'     as const },
  { to: '/settings',             key: 'settings'  as const },
]

export default function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const { t } = useTranslation()
  const [mobileNavOpen, setMobileNavOpen] = useState(false)

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="flex h-screen flex-col bg-gray-50 md:flex-row">
      <header className="flex h-14 shrink-0 items-center justify-between border-b border-gray-100 bg-white px-3 md:hidden">
        <div className="flex min-w-0 items-center gap-2">
          <button type="button" onClick={() => setMobileNavOpen(true)} className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-gray-200 text-gray-500" aria-label={aiWorkspaceCopy.openNavigation}>☰</button>
          <NavLink to="/" className="flex min-w-0 items-center gap-2">
          <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-brand-500 text-xs font-bold text-white">R</span>
          <span className="truncate text-sm font-semibold text-gray-900">{t('nav.brandTitle')}</span>
          </NavLink>
        </div>
        <NavLink to="/ai" state={{ fromRoute: location.pathname }} className="flex items-center gap-1.5 rounded-lg bg-brand-50 px-3 py-2 text-xs font-semibold text-brand-600">
          {NAV_ICONS.ai}<span>{aiWorkspaceCopy.quickEntry}</span>
        </NavLink>
      </header>
      {mobileNavOpen && (
        <div className="fixed inset-0 z-50 bg-black/30 md:hidden" onClick={() => setMobileNavOpen(false)}>
          <aside className="flex h-full w-[min(300px,86vw)] flex-col bg-white shadow-xl" onClick={event => event.stopPropagation()}>
            <div className="flex items-center justify-between border-b border-gray-100 px-4 py-4">
              <div><p className="text-sm font-semibold text-gray-900">{t('nav.brandTitle')}</p><p className="text-[10px] text-gray-400">{aiWorkspaceCopy.navigation}</p></div>
              <button type="button" className="flex h-8 w-8 items-center justify-center rounded-lg text-gray-400 hover:bg-gray-100" onClick={() => setMobileNavOpen(false)} aria-label={aiWorkspaceCopy.close}>×</button>
            </div>
            <div className="p-3"><NavLink to="/ai" state={{ fromRoute: location.pathname }} onClick={() => setMobileNavOpen(false)} className="flex items-center gap-2 rounded-xl bg-gradient-to-r from-brand-500 to-orange-500 px-3 py-3 text-sm font-semibold text-white">{NAV_ICONS.ai}<span>{aiWorkspaceCopy.quickEntry}</span><span className="ms-auto text-white/70">→</span></NavLink></div>
            <nav className="flex-1 space-y-0.5 overflow-y-auto px-2 pb-3">{NAV_ROUTES.map(({ to, end, key }) => <NavLink key={to} to={to} end={end} state={key === 'ai' ? { fromRoute: location.pathname } : undefined} onClick={() => setMobileNavOpen(false)} className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>{NAV_ICONS[key]}<span className="text-sm">{key === 'ai' ? aiWorkspaceCopy.nav : key === 'vouchers' ? '团购核销' : t(`nav.${key}`)}</span></NavLink>)}</nav>
            <div className="border-t border-gray-100 p-3"><button type="button" className="btn-secondary w-full" onClick={handleLogout}>{t('common.logout')}</button></div>
          </aside>
        </div>
      )}
      <aside className="hidden w-48 rtl:w-56 flex-shrink-0 flex-col border-e border-gray-100 bg-white md:flex">
        {/* Brand */}
        <div className="flex items-center gap-2.5 px-4 py-4 border-b border-gray-100">
          <div className="w-7 h-7 rounded-lg bg-brand-500 flex items-center justify-center flex-shrink-0">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M6 2L3 6v14a2 2 0 002 2h14a2 2 0 002-2V6l-3-4z" />
              <line x1="3" y1="6" x2="21" y2="6" />
              <path d="M16 10a4 4 0 01-8 0" />
            </svg>
          </div>
          <div className="min-w-0">
            <p className="text-xs font-semibold text-gray-900 truncate leading-tight">{t('nav.brandTitle')}</p>
            <p className="text-[10px] text-gray-400 truncate leading-tight">{t('nav.brandSub')}</p>
          </div>
        </div>

        <div className="px-2 pt-3">
          <NavLink to="/ai" state={{ fromRoute: location.pathname }} className="flex items-center gap-2 rounded-xl bg-gradient-to-r from-brand-500 to-orange-500 px-3 py-2.5 text-sm font-semibold text-white shadow-sm hover:from-brand-600 hover:to-orange-600">
            {NAV_ICONS.ai}<span>{aiWorkspaceCopy.quickEntry}</span><span className="ms-auto text-white/70">→</span>
          </NavLink>
        </div>

        {/* Nav */}
        <nav className="flex-1 px-2 py-3 space-y-0.5 overflow-y-auto">
          {NAV_ROUTES.map(({ to, end, key }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              state={key === 'ai' ? { fromRoute: location.pathname } : undefined}
              className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
            >
              {NAV_ICONS[key]}
              <span className="text-sm">{key === 'ai' ? aiWorkspaceCopy.nav : key === 'vouchers' ? '团购核销' : t(`nav.${key}`)}</span>
            </NavLink>
          ))}
        </nav>

        {/* User footer */}
        <div className="px-2 py-3 border-t border-gray-100">
          <div className="flex items-center gap-2 px-2 py-2 rounded-lg">
            <div className="w-6 h-6 rounded-full bg-brand-100 flex items-center justify-center flex-shrink-0">
              <span className="text-[10px] font-semibold text-brand-600">
                {(user?.displayName ?? 'A').charAt(0).toUpperCase()}
              </span>
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-xs font-medium text-gray-900 truncate leading-tight">{user?.displayName ?? '管理员'}</p>
              <p className="text-[10px] text-gray-400 truncate leading-tight">{user?.role ?? ''}</p>
            </div>
            <button
              onClick={handleLogout}
              className="flex-shrink-0 p-1 rounded hover:bg-gray-100 text-gray-400 hover:text-gray-600 transition-colors"
              title={t('common.logout')}
            >
              <svg width="13" height="13" viewBox="0 0 20 20" fill="currentColor" className="rtl:scale-x-[-1]">
                <path fillRule="evenodd" d="M3 3a1 1 0 00-1 1v12a1 1 0 102 0V4a1 1 0 00-1-1zm10.293 9.293a1 1 0 001.414 1.414l3-3a1 1 0 000-1.414l-3-3a1 1 0 10-1.414 1.414L14.586 9H7a1 1 0 100 2h7.586l-1.293 1.293z" clipRule="evenodd" />
              </svg>
            </button>
          </div>
        </div>
      </aside>

      <main className="min-h-0 flex-1 overflow-auto">
        <Outlet />
      </main>
    </div>
  )
}
