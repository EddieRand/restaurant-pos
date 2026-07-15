import { useEffect, useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { userApi, newId, fmtDate, timecardApi, scheduleApi, type PosUser, type TimecardDto, type TimecardListResponse, type ShiftSchedule, type LaborCostReportResponse } from '../api/admin'
import { fmtMoney } from '../api/reports'
import { roleApi, type Role } from '../api/permissions'
import { useAuth } from '../hooks/useAuth'
import { ErrorBoundary } from '../components/ErrorBoundary'

// ── Constants ──────────────────────────────────────────────────

const ROLE_STYLES: Record<string, { bg: string; text: string; border: string }> = {
  admin:   { bg: 'bg-purple-50',   text: 'text-purple-700',   border: 'border-purple-200' },
  manager: { bg: 'bg-blue-50',     text: 'text-blue-700',     border: 'border-blue-200' },
  cashier: { bg: 'bg-green-50',    text: 'text-green-700',    border: 'border-green-200' },
  waiter:  { bg: 'bg-amber-50',    text: 'text-amber-700',    border: 'border-amber-200' },
}
const DEFAULT_STYLE = { bg: 'bg-gray-50', text: 'text-gray-600', border: 'border-gray-200' }
const ROLE_ICONS: Record<string, string> = {
  admin: '👑', manager: '📋', cashier: '💳', waiter: '🍽️',
}
function roleIcon(id: string) { return ROLE_ICONS[id] ?? '🔧' }
function roleStyle(id: string) { return ROLE_STYLES[id] ?? DEFAULT_STYLE }

function generatePin(len = 4): string {
  return Array.from(crypto.getRandomValues(new Uint8Array(len)))
    .map(b => b % 10).join('')
}

// ── Permission definitions ──────────────────────────────────────

interface PermItem { key: string; label: string }
interface DomainGroup { id: string; label: string; permissions: PermItem[] }

const DOMAINS: DomainGroup[] = [
  {
    id: 'order', label: '订单管理',
    permissions: [
      { key: 'order.create', label: '创建订单/开台' },
      { key: 'order.modify', label: '修改订单内容' },
      { key: 'order.void_item', label: '退项/作废单品' },
      { key: 'order.void', label: '作废整单' },
      { key: 'order.transfer', label: '转台' },
      { key: 'order.merge', label: '并桌' },
      { key: 'order.split', label: '拆单' },
      { key: 'order.note', label: '添加订单备注' },
    ],
  },
  {
    id: 'payment', label: '收银管理',
    permissions: [
      { key: 'payment.process', label: '处理支付/结账' },
      { key: 'payment.discount', label: '应用折扣' },
      { key: 'payment.refund', label: '退款' },
      { key: 'payment.split', label: '拆账/混合支付' },
      { key: 'payment.tip', label: '管理小费' },
      { key: 'payment.coupon', label: '应用优惠券' },
    ],
  },
  {
    id: 'menu', label: '菜单管理',
    permissions: [
      { key: 'menu.view', label: '查看菜单' },
      { key: 'menu.edit', label: '编辑菜品/分类/规格' },
      { key: 'menu.sold_out', label: '沽清操作' },
      { key: 'menu.combo', label: '套餐管理' },
    ],
  },
  {
    id: 'report', label: '报表管理',
    permissions: [
      { key: 'report.daily', label: '日结报表' },
      { key: 'report.shift', label: '交班报表' },
      { key: 'report.export', label: '导出报表(PDF)' },
    ],
  },
  {
    id: 'settings', label: '系统设置',
    permissions: [
      { key: 'settings.region', label: '区域配置(货币/税/语言)' },
      { key: 'settings.printer', label: '打印机配置' },
      { key: 'settings.receipt', label: '小票模板' },
      { key: 'settings.tax', label: '税档配置' },
    ],
  },
  {
    id: 'staff', label: '员工管理',
    permissions: [
      { key: 'staff.manage', label: '管理员工(增删改查)' },
      { key: 'staff.roles', label: '管理角色权限' },
    ],
  },
]

const ALL_PERM_KEYS = DOMAINS.flatMap(d => d.permissions.map(p => p.key))

const DEFAULT_MATRIX: Record<string, string[]> = {
  admin: ALL_PERM_KEYS,
  manager: ALL_PERM_KEYS.filter(k => k !== 'settings.region' && k !== 'settings.tax'),
  cashier: ['order.create','order.note','payment.process','payment.discount','payment.tip','payment.coupon','menu.view','menu.sold_out','report.daily'],
  waiter: ['order.create','order.note','menu.view'],
}

// ── Main Page ───────────────────────────────────────────────────

export default function UsersPage() {
  const { t } = useTranslation()
  const { user: authUser } = useAuth()
  const isAdmin = authUser?.role?.toLowerCase() === 'admin'
  const [activeTab, setActiveTab] = useState<'staff' | 'roles' | 'timecards' | 'schedule'>('staff')

  return (
    <div className="p-6 md:p-8 w-full">
      <div className="flex items-center justify-between mb-5">
        <div>
          <h1 className="text-2xl font-semibold text-gray-900">{t('users.title')}</h1>
          <p className="mt-1 text-sm text-gray-500">{t('users.subtitle')}</p>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-0.5 mb-6 bg-gray-100 p-1 rounded-xl w-fit">
        <TabButton active={activeTab === 'staff'} onClick={() => setActiveTab('staff')}>
          <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor">
            <path d="M9 6a3 3 0 11-6 0 3 3 0 016 0zM17 6a3 3 0 11-6 0 3 3 0 016 0zM12.93 17c.046-.327.07-.66.07-1a6.97 6.97 0 00-1.5-4.33A5 5 0 0119 16v1h-6.07zM6 11a5 5 0 015 5v1H1v-1a5 5 0 015-5z" />
          </svg>
          {t('users.tabStaffManagement')}
        </TabButton>
        {isAdmin && (
          <TabButton active={activeTab === 'roles'} onClick={() => setActiveTab('roles')}>
            <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M5 9V7a5 5 0 0110 0v2a2 2 0 012 2v5a2 2 0 01-2 2H5a2 2 0 01-2-2v-5a2 2 0 012-2zm8-2v2H7V7a3 3 0 016 0z" clipRule="evenodd" />
            </svg>
            {t('users.tabRolesPermissions')}
          </TabButton>
        )}
        <TabButton active={activeTab === 'timecards'} onClick={() => setActiveTab('timecards')}>
          <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor">
            <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z" clipRule="evenodd" />
          </svg>
          {t('users.tabTimecards')}
        </TabButton>
        <TabButton active={activeTab === 'schedule'} onClick={() => setActiveTab('schedule')}>
          <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor">
            <path fillRule="evenodd" d="M6 2a1 1 0 00-1 1v1H4a2 2 0 00-2 2v10a2 2 0 002 2h12a2 2 0 002-2V6a2 2 0 00-2-2h-1V3a1 1 0 10-2 0v1H7V3a1 1 0 00-1-1zm0 5a1 1 0 000 2h8a1 1 0 100-2H6z" clipRule="evenodd" />
          </svg>
          {t('users.tabSchedule')}
        </TabButton>
      </div>

      <ErrorBoundary label={activeTab}>
        {activeTab === 'staff' ? <StaffTab /> : activeTab === 'roles' ? <RolesTab user={authUser} /> : activeTab === 'timecards' ? <TimecardsTab /> : <ScheduleTab />}
      </ErrorBoundary>
    </div>
  )
}

function TabButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      className={`flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium transition-all duration-200 ${
        active ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'
      }`}
    >
      {children}
    </button>
  )
}

// ═════════════════════════════════════════════════════════════════
// Staff Tab — Employee list with dynamic role assignment
// ═════════════════════════════════════════════════════════════════

function StaffTab() {
  const { t } = useTranslation()
  const [users, setUsers] = useState<PosUser[]>([])
  const [roles, setRoles] = useState<Role[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<PosUser | null>(null)
  const [pinReset, setPinReset] = useState<{ name: string; pin: string } | null>(null)

  const reload = useCallback(async () => {
    setLoading(true)
    setError('')
    // Fetch users and roles independently so one failure doesn't block the other
    const results = await Promise.allSettled([
      userApi.list(),
      roleApi.list(),
    ])
    // Process user result
    if (results[0].status === 'fulfilled') {
      setUsers(results[0].value)
    } else {
      const err = results[0].reason
      const status = err?.response?.status
      if (status === 401) {
        setError(t('users.sessionExpired'))
        localStorage.removeItem('pos_admin_token')
        localStorage.removeItem('pos_admin_user')
        setTimeout(() => { window.location.href = '/login' }, 1500)
      } else if (status === 403) {
        setError(t('users.noPermissionStaff'))
      } else {
        setError(t('users.loadStaffFailed'))
      }
    }
    // Process role result (non-critical, just log warning)
    if (results[1].status === 'fulfilled') {
      setRoles(results[1].value)
    } else {
      // Role list failure is non-critical for the staff tab
      console.warn('角色列表加载失败，角色选择将使用默认值:', results[1].reason)
    }
    setLoading(false)
  }, [])

  useEffect(() => { reload() }, [reload])

  async function toggleActive(user: PosUser) {
    await userApi.update(user.id, { isActive: !user.isActive })
    reload()
  }

  async function handleResetPin(user: PosUser) {
    const pin = generatePin()
    await userApi.update(user.id, { pin })
    setPinReset({ name: user.displayName, pin })
  }

  return (
    <>
      {error && (
        <div className="mb-4 px-4 py-3 rounded-xl bg-red-50 text-red-700 text-sm border border-red-200 flex items-center gap-2">
          <span>❌</span> {error}
          <button className="ml-auto underline text-red-600 hover:text-red-800" onClick={reload}>重试</button>
        </div>
      )}

      <div className="flex items-center justify-end mb-4">
        <button className="btn-primary flex items-center gap-1.5" onClick={() => setCreating(true)}>
          <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor">
            <path fillRule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clipRule="evenodd" />
          </svg>
          {t('users.addUser')}
        </button>
      </div>

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-100 bg-gray-50/50">
              <th className="px-4 py-3 text-start font-medium text-gray-500">{t('users.colName')}</th>
              <th className="px-4 py-3 text-start font-medium text-gray-500">{t('users.colRole')}</th>
              <th className="px-4 py-3 text-start font-medium text-gray-500">{t('users.colCreated')}</th>
              <th className="px-4 py-3 text-center font-medium text-gray-500">{t('users.colStatus')}</th>
              <th className="px-4 py-3 text-end font-medium text-gray-500">{t('users.colActions')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {loading && (
              <tr><td colSpan={5} className="px-4 py-12 text-center text-gray-400">
                <div className="flex flex-col items-center gap-2">
                  <div className="w-5 h-5 border-2 border-gray-300 border-t-brand-500 rounded-full animate-spin" />
                  {t('common.loading')}
                </div>
              </td></tr>
            )}
            {!loading && users.length === 0 && (
              <tr><td colSpan={5} className="px-4 py-8 text-center text-gray-400">{t('users.noUsers')}</td></tr>
            )}
            {users.map(user => {
              const s = roleStyle(user.role)
              return (
                <tr key={user.id} className={`hover:bg-gray-50/50 transition-colors ${!user.isActive ? 'opacity-50' : ''}`}>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-3">
                      <div className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 text-sm ${s.bg} ${s.text}`}>
                        {roleIcon(user.role)}
                      </div>
                      <span className="font-medium text-gray-900">{user.displayName}</span>
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium border ${s.bg} ${s.text} ${s.border}`}>
                      {roleIcon(user.role)} {user.roleDisplayName || user.role}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-gray-500">{fmtDate(user.createdAt)}</td>
                  <td className="px-4 py-3 text-center">
                    <button
                      onClick={() => toggleActive(user)}
                      className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium transition-colors cursor-pointer ${
                        user.isActive
                          ? 'bg-green-50 text-green-700 border border-green-200 hover:bg-green-100'
                          : 'bg-gray-50 text-gray-500 border border-gray-200 hover:bg-gray-100'
                      }`}
                    >
                      <span className={`w-1.5 h-1.5 rounded-full ${user.isActive ? 'bg-green-500' : 'bg-gray-400'}`} />
                      {user.isActive ? t('users.statusActive') : t('users.statusInactive')}
                    </button>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex gap-3 justify-end">
                      <button className="text-xs text-gray-500 hover:text-brand-600 transition-colors" onClick={() => setEditing(user)}>
                        {t('menu.edit')}
                      </button>
                      <button className="text-xs text-gray-500 hover:text-amber-600 transition-colors" onClick={() => handleResetPin(user)}>
                        {t('users.resetPin')}
                      </button>
                    </div>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {(creating || editing) && (
        <UserFormModal
          user={editing ?? undefined}
          roles={roles}
          onClose={() => { setCreating(false); setEditing(null) }}
          onSaved={(savedPin, savedName) => {
            setCreating(false); setEditing(null); reload()
            if (savedPin && savedName) setPinReset({ name: savedName, pin: savedPin })
          }}
        />
      )}

      {pinReset && (
        <PinResetDialog name={pinReset.name} pin={pinReset.pin} onClose={() => setPinReset(null)} />
      )}
    </>
  )
}

// ═════════════════════════════════════════════════════════════════
// Roles Tab — Role management + permission matrix
// ═════════════════════════════════════════════════════════════════

function RolesTab({ user }: { user: { role?: string } | null }) {
  const { t } = useTranslation()
  const [roles, setRoles] = useState<Role[]>([])
  const [permissions, setPermissions] = useState<Record<string, string[]>>({})
  const [selectedRole, setSelectedRole] = useState('admin')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)
  const [hasChanges, setHasChanges] = useState(false)
  const [showCreateRole, setShowCreateRole] = useState(false)
  const [editingRoleName, setEditingRoleName] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setMessage(null)
    try {
      const rolesData = await roleApi.list()
      setRoles(rolesData)
      const permMap: Record<string, string[]> = {}
      for (const r of rolesData) {
        try {
          const res = await roleApi.getPermissions(r.id)
          permMap[r.id] = res.permissions
        } catch { permMap[r.id] = [] }
      }
      setPermissions(permMap)
      if (!rolesData.find(r => r.id === selectedRole)) {
        setSelectedRole(rolesData[0]?.id ?? 'admin')
      }
    } catch (e: any) {
      const status = e?.response?.status
      if (status === 401) {
        setMessage({ type: 'error', text: t('users.sessionExpired') })
        localStorage.removeItem('pos_admin_token')
        localStorage.removeItem('pos_admin_user')
        setTimeout(() => { window.location.href = '/login' }, 1500)
      } else if (status === 403) {
        setMessage({ type: 'error', text: '当前账号无权限访问角色管理，请联系管理员' })
      } else {
        setMessage({ type: 'error', text: '加载权限数据失败，请检查服务器' })
      }
    } finally { setLoading(false) }
  }, [])

  useEffect(() => { load() }, [load])

  // Auto-clear message
  useEffect(() => {
    if (message) { const t = setTimeout(() => setMessage(null), 4000); return () => clearTimeout(t) }
  }, [message])

  const currentPerms = permissions[selectedRole] ?? []
  const enabledCount = currentPerms.length
  const totalCount = ALL_PERM_KEYS.length
  const percentage = Math.round((enabledCount / totalCount) * 100)
  const selRole = roles.find(r => r.id === selectedRole)

  const toggle = (key: string) => {
    setPermissions(prev => {
      const cur = [...(prev[selectedRole] ?? [])]
      const idx = cur.indexOf(key)
      if (idx >= 0) cur.splice(idx, 1)
      else cur.push(key)
      return { ...prev, [selectedRole]: cur }
    })
    setHasChanges(true)
  }

  const toggleDomain = (domain: DomainGroup) => {
    const keys = domain.permissions.map(p => p.key)
    const allOn = keys.every(k => currentPerms.includes(k))
    setPermissions(prev => {
      const cur = [...(prev[selectedRole] ?? [])]
      const next = allOn ? cur.filter(k => !keys.includes(k)) : [...new Set([...cur, ...keys])]
      return { ...prev, [selectedRole]: next }
    })
    setHasChanges(true)
  }

  const save = async () => {
    const perms = permissions[selectedRole] ?? []
    if (user?.role?.toLowerCase() === selectedRole && !perms.includes('staff.roles')) {
      setMessage({ type: 'error', text: '无法删除自己的「管理角色权限」，操作已阻止' })
      return
    }
    setSaving(true)
    try {
      await roleApi.updatePermissions(selectedRole, perms)
      // Reload roles to update userCount
      const updatedRoles = await roleApi.list()
      setRoles(updatedRoles)
      setMessage({ type: 'success', text: `「${selRole?.displayName ?? selectedRole}」权限已保存，POS 同步后生效` })
      setHasChanges(false)
    } catch {
      setMessage({ type: 'error', text: '保存失败，请重试' })
    } finally { setSaving(false) }
  }

  const resetDefaults = () => {
    const defaults = DEFAULT_MATRIX[selectedRole]
    if (defaults) {
      setPermissions(prev => ({ ...prev, [selectedRole]: [...defaults] }))
      setHasChanges(true)
      setMessage({ type: 'success', text: `已恢复「${selectedRole}」的默认权限（未保存）` })
    }
  }

  const handleCreateRole = async (id: string, displayName: string) => {
    try {
      await roleApi.create(id, displayName)
      setShowCreateRole(false)
      await load()
      setSelectedRole(id)
      setMessage({ type: 'success', text: `角色「${displayName}」已创建，现在可以配置权限` })
    } catch (e: any) {
      setMessage({ type: 'error', text: e?.response?.data?.message ?? '创建角色失败' })
    }
  }

  const handleUpdateRole = async (roleId: string, displayName: string) => {
    try {
      await roleApi.update(roleId, displayName)
      setEditingRoleName(null)
      await load()
      setMessage({ type: 'success', text: `角色名称已更新` })
    } catch {
      setMessage({ type: 'error', text: '更新角色名称失败' })
    }
  }

  const handleDeleteRole = async (role: Role) => {
    if (role.userCount > 0) {
      setMessage({ type: 'error', text: `该角色下还有 ${role.userCount} 名员工，请先切换后再删除` })
      return
    }
    if (!confirm(`确定要删除角色「${role.displayName}」吗？此操作不可恢复。`)) return
    try {
      await roleApi.delete(role.id)
      await load()
      if (selectedRole === role.id) setSelectedRole('admin')
      setMessage({ type: 'success', text: `角色「${role.displayName}」已删除` })
    } catch (e: any) {
      setMessage({ type: 'error', text: e?.response?.data?.message ?? '删除角色失败' })
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="flex flex-col items-center gap-3">
          <div className="w-8 h-8 border-2 border-brand-500 border-t-transparent rounded-full animate-spin" />
          <span className="text-sm text-gray-400">加载角色数据...</span>
        </div>
      </div>
    )
  }

  return (
    <div>
      {/* Flash message */}
      {message && (
        <div className={`mb-4 px-4 py-3 rounded-xl text-sm font-medium flex items-center gap-2 ${
          message.type === 'success'
            ? 'bg-green-50 text-green-700 border border-green-200'
            : 'bg-red-50 text-red-700 border border-red-200'
        }`}>
          <span>{message.type === 'success' ? '✅' : '❌'}</span>
          {message.text}
        </div>
      )}

      {/* ── Role cards with employee counts, create/edit/delete ── */}
      <div className="mb-6">
        <div className="flex items-center justify-between mb-3">
          <span className="text-sm font-medium text-gray-500">选择角色</span>
          <button
            onClick={() => setShowCreateRole(true)}
            className="flex items-center gap-1 text-xs font-medium text-brand-600 hover:text-brand-700 px-2 py-1 rounded-lg hover:bg-brand-50 transition-colors"
          >
            <svg className="w-3.5 h-3.5" viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clipRule="evenodd" />
            </svg>
            新建角色
          </button>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-3">
          {roles.map(role => {
            const s = roleStyle(role.id)
            const isActive = role.id === selectedRole
            const count = (permissions[role.id] ?? []).length
            return (
              <button
                key={role.id}
                onClick={() => { setSelectedRole(role.id); setHasChanges(false) }}
                className={`relative p-4 rounded-xl border-2 text-left transition-all duration-200 ${
                  isActive
                    ? `${s.border} ${s.bg} shadow-sm scale-[1.02]`
                    : 'border-gray-200 bg-white hover:border-gray-300 hover:bg-gray-50'
                }`}
              >
                {isActive && (
                  <div className="absolute -top-2 -right-2 w-5 h-5 bg-brand-500 rounded-full flex items-center justify-center shadow-sm">
                    <svg className="w-3 h-3 text-white" viewBox="0 0 20 20" fill="currentColor">
                      <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                    </svg>
                  </div>
                )}
                <div className="flex items-center gap-2 mb-2">
                  <span className="text-xl">{roleIcon(role.id)}</span>
                  <div>
                    <span className={`text-sm font-semibold ${isActive ? s.text : 'text-gray-800'}`}>
                      {role.displayName}
                    </span>
                    {!role.isBuiltin && (
                      <span className="ml-1 text-[10px] text-gray-400 bg-gray-100 px-1.5 py-0.5 rounded">自定义</span>
                    )}
                  </div>
                </div>

                {/* Employee count */}
                <div className="flex items-center gap-1.5 mb-1.5 text-[11px] text-gray-400">
                  <svg className="w-3 h-3" viewBox="0 0 20 20" fill="currentColor">
                    <path d="M9 6a3 3 0 11-6 0 3 3 0 016 0zM17 6a3 3 0 11-6 0 3 3 0 016 0zM12.93 17c.046-.327.07-.66.07-1a6.97 6.97 0 00-1.5-4.33A5 5 0 0119 16v1h-6.07zM6 11a5 5 0 015 5v1H1v-1a5 5 0 015-5z" />
                  </svg>
                  {role.userCount} 名员工
                </div>

                {/* Permission progress */}
                <div className="flex items-center gap-2">
                  <div className="flex-1 h-1.5 bg-gray-200 rounded-full overflow-hidden">
                    <div
                      className={`h-full rounded-full transition-all duration-500 ${isActive ? 'bg-brand-500' : 'bg-gray-400'}`}
                      style={{ width: `${Math.round((count / totalCount) * 100)}%` }}
                    />
                  </div>
                  <span className={`text-[11px] font-semibold ${isActive ? 'text-brand-600' : 'text-gray-400'}`}>
                    {count}/{totalCount}
                  </span>
                </div>

                {/* Edit/Delete for non-builtin roles */}
                {!role.isBuiltin && isActive && (
                  <div className="flex gap-1 mt-2 pt-2 border-t border-gray-100">
                    <button
                      onClick={(e) => { e.stopPropagation(); setEditingRoleName(role.id) }}
                      className="text-[10px] text-gray-400 hover:text-brand-600 px-2 py-0.5 rounded hover:bg-brand-50 transition-colors"
                    >
                      编辑名称
                    </button>
                    <button
                      onClick={(e) => { e.stopPropagation(); handleDeleteRole(role) }}
                      className="text-[10px] text-gray-400 hover:text-red-600 px-2 py-0.5 rounded hover:bg-red-50 transition-colors"
                    >
                      删除角色
                    </button>
                  </div>
                )}
              </button>
            )
          })}
        </div>
      </div>

      {/* ── Selected role header ── */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-3">
          <div className={`w-10 h-10 rounded-xl flex items-center justify-center text-lg border ${roleStyle(selectedRole).bg} ${roleStyle(selectedRole).border}`}>
            {roleIcon(selectedRole)}
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h3 className="text-base font-semibold text-gray-900">
                {selRole?.displayName ?? selectedRole}
              </h3>
              <span className={`badge text-[11px] ${roleStyle(selectedRole).bg} ${roleStyle(selectedRole).text}`}>
                {enabledCount}/{totalCount} 项已启用
              </span>
              {selRole && selRole.userCount > 0 && (
                <span className="text-[11px] text-gray-400">({selRole.userCount} 人使用此角色)</span>
              )}
            </div>
            <div className="w-48 h-1.5 bg-gray-100 rounded-full mt-1.5 overflow-hidden">
              <div className="h-full bg-brand-500 rounded-full transition-all duration-500" style={{ width: `${percentage}%` }} />
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button onClick={resetDefaults} className="px-3 py-1.5 text-xs font-medium text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-lg transition-colors">
            恢复默认
          </button>
          <button
            onClick={save}
            disabled={saving || !hasChanges}
            className={`px-4 py-1.5 text-xs font-semibold rounded-lg transition-all ${
              hasChanges ? 'bg-brand-500 text-white hover:bg-brand-600 shadow-sm' : 'bg-gray-100 text-gray-400 cursor-not-allowed'
            }`}
          >
            {saving ? '保存中...' : hasChanges ? '💾 保存权限' : '已是最新'}
          </button>
        </div>
      </div>

      {/* ── Permission domain cards ── */}
      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">
        {DOMAINS.map(domain => {
          const domainKeys = domain.permissions.map(p => p.key)
          const domainEnabled = domainKeys.filter(k => currentPerms.includes(k)).length
          const domainTotal = domainKeys.length
          const allOn = domainEnabled === domainTotal
          const someOn = domainEnabled > 0 && domainEnabled < domainTotal

          return (
            <div key={domain.id} className={`rounded-xl border transition-all duration-200 ${
              allOn ? 'border-green-200 bg-green-50/30' : someOn ? 'border-amber-200 bg-amber-50/20' : 'border-gray-200 bg-white'
            }`}>
              <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100">
                <div className="flex items-center gap-2">
                  <span className={`w-2 h-2 rounded-full ${allOn ? 'bg-green-500' : someOn ? 'bg-amber-500' : 'bg-gray-300'}`} />
                  <span className="text-sm font-semibold text-gray-800">{domain.label}</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-[11px] text-gray-400">{domainEnabled}/{domainTotal}</span>
                  <button
                    onClick={() => toggleDomain(domain)}
                    className={`text-[11px] font-medium px-2 py-0.5 rounded-md transition-colors ${
                      allOn ? 'text-green-600 hover:bg-green-100' : 'text-gray-400 hover:text-brand-500 hover:bg-brand-50'
                    }`}
                  >
                    {allOn ? '全清' : '全选'}
                  </button>
                </div>
              </div>
              <div className="p-3 space-y-1.5">
                {domain.permissions.map(perm => {
                  const enabled = currentPerms.includes(perm.key)
                  return (
                    <label key={perm.key} className={`flex items-center gap-2.5 px-2 py-1.5 rounded-lg cursor-pointer transition-all duration-150 ${
                      enabled ? 'bg-white shadow-sm' : 'hover:bg-gray-50'
                    }`}>
                      <div className="relative flex-shrink-0">
                        <input type="checkbox" checked={enabled} onChange={() => toggle(perm.key)} className="sr-only" />
                        <div className={`w-9 h-5 rounded-full transition-colors duration-200 ${enabled ? 'bg-brand-500' : 'bg-gray-300'}`}>
                          <div className={`absolute top-0.5 w-4 h-4 rounded-full bg-white shadow transition-transform duration-200 ${
                            enabled ? 'left-[18px]' : 'left-[2px]'
                          }`} />
                        </div>
                      </div>
                      <div className="flex-1 min-w-0">
                        <span className={`text-sm ${enabled ? 'text-gray-900 font-medium' : 'text-gray-500'}`}>{perm.label}</span>
                      </div>
                      {enabled && (
                        <svg className="w-4 h-4 text-green-500 flex-shrink-0" viewBox="0 0 20 20" fill="currentColor">
                          <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                        </svg>
                      )}
                    </label>
                  )
                })}
              </div>
            </div>
          )
        })}
      </div>

      {/* Legend */}
      <div className="flex items-center gap-4 mt-4 text-[11px] text-gray-400">
        <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-green-500" /> 全部启用</span>
        <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-amber-500" /> 部分启用</span>
        <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-gray-300" /> 未启用</span>
      </div>

      {/* ── Create Role Modal ── */}
      {showCreateRole && (
        <CreateRoleModal
          existingIds={roles.map(r => r.id)}
          onSave={handleCreateRole}
          onClose={() => setShowCreateRole(false)}
        />
      )}

      {/* ── Edit Role Name Modal ── */}
      {editingRoleName && (
        <EditRoleNameModal
          roleId={editingRoleName}
          currentName={selRole?.displayName ?? editingRoleName}
          onSave={handleUpdateRole}
          onClose={() => setEditingRoleName(null)}
        />
      )}
    </div>
  )
}

// ═════════════════════════════════════════════════════════════════
// Create Role Modal
// ═════════════════════════════════════════════════════════════════

function CreateRoleModal({ existingIds, onSave, onClose }: {
  existingIds: string[]; onSave: (id: string, displayName: string) => void; onClose: () => void
}) {
  const [id, setId] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  async function handleSave() {
    const trimmedId = id.trim().toLowerCase()
    if (!trimmedId || !/^[a-z][a-z0-9_-]*$/.test(trimmedId)) {
      setError('角色 ID 只能包含小写字母、数字、下划线和连字符，且必须以字母开头')
      return
    }
    if (existingIds.includes(trimmedId)) {
      setError(`角色 ID「${trimmedId}」已存在`)
      return
    }
    if (!displayName.trim()) {
      setError('角色名称不能为空')
      return
    }
    setSaving(true)
    await onSave(trimmedId, displayName.trim())
    setSaving(false)
  }

  return (
    <Modal title="新建角色" onClose={onClose}>
      <div className="space-y-4">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">角色 ID（英文标识）</label>
          <input className="input font-mono" value={id} onChange={e => setId(e.target.value)}
            placeholder="例如：chef, supervisor" autoFocus />
          <p className="text-[11px] text-gray-400 mt-1">创建后不可修改，建议用小写英文单词</p>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">角色名称</label>
          <input className="input" value={displayName} onChange={e => setDisplayName(e.target.value)}
            placeholder="例如：厨师长、主管" />
        </div>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex gap-3 pt-2">
          <button className="btn-secondary flex-1" onClick={onClose}>取消</button>
          <button className="btn-primary flex-1" onClick={handleSave} disabled={saving}>
            {saving ? '创建中...' : '创建角色'}
          </button>
        </div>
      </div>
    </Modal>
  )
}

// ═════════════════════════════════════════════════════════════════
// Edit Role Name Modal
// ═════════════════════════════════════════════════════════════════

function EditRoleNameModal({ roleId, currentName, onSave, onClose }: {
  roleId: string; currentName: string; onSave: (id: string, name: string) => void; onClose: () => void
}) {
  const [name, setName] = useState(currentName)
  const [saving, setSaving] = useState(false)

  return (
    <Modal title={`编辑角色「${roleId}」`} onClose={onClose}>
      <div className="space-y-4">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">角色名称</label>
          <input className="input" value={name} onChange={e => setName(e.target.value)} autoFocus />
        </div>
        <div className="flex gap-3 pt-2">
          <button className="btn-secondary flex-1" onClick={onClose}>取消</button>
          <button className="btn-primary flex-1" onClick={async () => { setSaving(true); await onSave(roleId, name.trim()); setSaving(false) }} disabled={saving || !name.trim()}>
            {saving ? '保存中...' : '保存'}
          </button>
        </div>
      </div>
    </Modal>
  )
}

// ═════════════════════════════════════════════════════════════════
// User Form Modal — Dynamic role dropdown from API
// ═════════════════════════════════════════════════════════════════

function UserFormModal({ user, roles, onClose, onSaved }: {
  user?: PosUser; roles: Role[]; onClose: () => void; onSaved: (pin?: string, name?: string) => void
}) {
  const { t } = useTranslation()
  const [displayName, setDisplayName] = useState(user?.displayName ?? '')
  const [role, setRole] = useState(user?.role?.toLowerCase() ?? 'cashier')
  const [pin, setPin] = useState('')
  const [showPin, setShowPin] = useState(true)
  const [hourlyWage, setHourlyWage] = useState(() => ((user?.hourlyWageMinorUnit ?? 0) / 100).toString())
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const isNew = !user

  async function handleSave() {
    if (!displayName.trim()) { setError(t('users.validationName')); return }
    if (isNew && pin.length < 4) { setError(t('users.pinDigits', { n: 4 })); return }
    if (pin && !/^\d{4,6}$/.test(pin)) { setError(t('users.validationPin', { n: 4 })); return }
    const wageMinorUnit = Math.round((parseFloat(hourlyWage) || 0) * 100)
    setSaving(true); setError('')
    try {
      if (isNew) {
        await userApi.create({ id: newId(), displayName: displayName.trim(), role, pin, hourlyWageMinorUnit: wageMinorUnit })
        onSaved(pin, displayName.trim())
      } else {
        await userApi.update(user.id, { displayName: displayName.trim(), role, hourlyWageMinorUnit: wageMinorUnit, ...(pin ? { pin } : {}) })
        onSaved(pin || undefined, displayName.trim())
      }
    } catch { setError(t('users.saveFailed')) } finally { setSaving(false) }
  }

  return (
    <Modal title={isNew ? t('users.addUserTitle') : t('users.editUser')} onClose={onClose}>
      <div className="space-y-4">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">{t('users.fieldName')} *</label>
          <input className="input" value={displayName} onChange={e => setDisplayName(e.target.value)}
            placeholder={t('users.fieldNamePlaceholder')} autoFocus />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">{t('users.fieldRole')}</label>
          <div className="grid grid-cols-2 gap-2 max-h-48 overflow-y-auto">
            {roles.map(r => {
              const s = roleStyle(r.id)
              const isActive = role === r.id
              return (
                <button key={r.id} type="button" onClick={() => setRole(r.id)}
                  className={`flex items-center gap-2 px-3 py-2.5 rounded-lg border text-sm transition-colors text-left ${
                    isActive
                      ? 'border-brand-300 bg-brand-50 text-brand-700'
                      : 'border-gray-200 bg-white text-gray-600 hover:border-gray-300'
                  }`}>
                  <span className="text-base flex-shrink-0">{roleIcon(r.id)}</span>
                  <div className="min-w-0">
                    <div className={`text-xs font-medium ${isActive ? s.text : 'text-gray-700'}`}>{r.displayName}</div>
                    {r.userCount > 0 && <div className="text-[10px] text-gray-400">{r.userCount} 人</div>}
                  </div>
                </button>
              )
            })}
          </div>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">
            {isNew ? t('users.fieldPin') : t('users.fieldPinPlaceholder')}
          </label>
          <div className="flex gap-2">
            <div className="relative flex-1">
              <input className="input font-mono w-full text-center tracking-[0.3em] pr-9"
                type={showPin ? 'text' : 'password'} inputMode="numeric" pattern="[0-9]*" maxLength={6}
                value={pin} onChange={e => setPin(e.target.value.replace(/\D/g, ''))}
                placeholder={isNew ? t('users.fieldPin') : t('users.fieldPinPlaceholder')} />
              <button type="button" tabIndex={-1} className="absolute inset-y-0 end-0 px-2.5 text-gray-400 hover:text-gray-600"
                onClick={() => setShowPin(v => !v)}>
                {showPin ? (
                  <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor"><path d="M10 12a2 2 0 100-4 2 2 0 000 4z" /><path fillRule="evenodd" d="M.458 10C1.732 5.943 5.522 3 10 3s8.268 2.943 9.542 7c-1.274 4.057-5.064 7-9.542 7S1.732 14.057.458 10zM14 10a4 4 0 11-8 0 4 4 0 018 0z" clipRule="evenodd" /></svg>
                ) : (
                  <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M3.707 2.293a1 1 0 00-1.414 1.414l14 14a1 1 0 001.414-1.414l-1.473-1.473A10.014 10.014 0 0019.542 10C18.268 5.943 14.478 3 10 3a9.958 9.958 0 00-4.512 1.074l-1.78-1.781zm4.261 4.26l1.514 1.515a2.003 2.003 0 012.45 2.45l1.514 1.514a4 4 0 00-5.478-5.478z" clipRule="evenodd" /><path d="M12.454 16.697L9.75 13.992a4 4 0 01-3.742-3.741L2.335 6.578A9.98 9.98 0 00.458 10c1.274 4.057 5.064 7 9.542 7 .847 0 1.669-.105 2.454-.303z" /></svg>
                )}
              </button>
            </div>
            <button type="button" className="btn-secondary text-xs px-3 flex-shrink-0" onClick={() => { setPin(generatePin()); setShowPin(true) }}
              title={t('users.fieldPinRandom')}>{t('users.fieldPinRandom')}</button>
          </div>
          {pin && <p className="text-xs text-amber-600 mt-1">⚠ {t('users.pinOnce')}</p>}
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">{t('users.fieldHourlyWage')}</label>
          <input className="input" type="number" min="0" step="0.01" value={hourlyWage}
            onChange={e => setHourlyWage(e.target.value)} placeholder="0.00" />
        </div>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex gap-3 pt-2">
          <button className="btn-secondary flex-1" onClick={onClose}>{t('users.cancel')}</button>
          <button className="btn-primary flex-1" onClick={handleSave} disabled={saving}>
            {saving ? t('users.saving') : t('users.save')}
          </button>
        </div>
      </div>
    </Modal>
  )
}

// ═════════════════════════════════════════════════════════════════
// PIN Reset Dialog
// ═════════════════════════════════════════════════════════════════

function PinResetDialog({ name, pin, onClose }: { name: string; pin: string; onClose: () => void }) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)
  function copyPin() { navigator.clipboard.writeText(pin); setCopied(true); setTimeout(() => setCopied(false), 2000) }
  return (
    <Modal title={t('users.pinResetSuccess', { name })} onClose={onClose}>
      <div className="space-y-4 text-center">
        <div className="w-14 h-14 rounded-full bg-green-100 flex items-center justify-center mx-auto">
          <svg className="w-7 h-7 text-green-600" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" /></svg>
        </div>
        <div><p className="text-sm text-gray-600 mb-1">{t('users.pinResetSuccess', { name })}</p><p className="text-xs text-gray-400">{t('users.pinNotify')}</p></div>
        <div className="text-4xl font-mono font-bold tracking-[0.4em] text-brand-600 bg-brand-50 rounded-xl py-5 cursor-pointer select-all"
          onClick={copyPin} title={t('users.pinCopy')}>{pin}</div>
        <div className="flex gap-3">
          <button className="btn-secondary flex-1" onClick={copyPin}>{copied ? `✓ ${t('users.pinCopied')}` : t('users.pinCopy')}</button>
          <button className="btn-primary flex-1" onClick={onClose}>{t('users.pinKnow')}</button>
        </div>
      </div>
    </Modal>
  )
}

// ═════════════════════════════════════════════════════════════════
// TimecardsTab
// ═════════════════════════════════════════════════════════════════

function fmtMinutes(minutes: number): string {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return `${h}h ${m.toString().padStart(2, '0')}m`
}

function fmtDatetimeLocal(ms: number): string {
  const d = new Date(ms)
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function parseDatetimeLocal(s: string): number {
  return new Date(s).getTime()
}

function TimecardsTab() {
  const { t } = useTranslation()
  const [data, setData] = useState<TimecardListResponse>({ timecards: [], total: 0 })
  const [loading, setLoading] = useState(false)
  const [operatorId, setOperatorId] = useState('')
  const [from, setFrom] = useState(() => {
    const d = new Date(); d.setDate(d.getDate() - 30); d.setHours(0, 0, 0, 0); return d.getTime()
  })
  const [to, setTo] = useState(() => {
    const d = new Date(); d.setHours(23, 59, 59, 999); return d.getTime()
  })
  const [editCard, setEditCard] = useState<TimecardDto | null>(null)
  const [showModal, setShowModal] = useState(false)
  const [users, setUsers] = useState<PosUser[]>([])

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await timecardApi.list({ from, to, operatorId: operatorId || undefined, pageSize: 200 })
      setData(res)
    } finally {
      setLoading(false)
    }
  }, [from, to, operatorId])

  useEffect(() => { load() }, [load])
  useEffect(() => { userApi.list().then(setUsers) }, [])

  async function handleDelete(id: string) {
    if (!window.confirm(t('users.deleteTimecardConfirm'))) return
    await timecardApi.delete(id)
    load()
  }

  function exportCsv() {
    const rows = [
      [t('users.colClockIn'), t('users.colClockOut'), t('users.timecardEmployee'), t('users.colTerminal'), t('users.colDuration')],
      ...data.timecards.map(c => [
        new Date(c.clockInAt).toLocaleString(),
        c.clockOutAt ? new Date(c.clockOutAt).toLocaleString() : t('users.timecardOpen'),
        c.operatorName,
        c.terminalId,
        c.durationMinutes != null ? fmtMinutes(c.durationMinutes) : '',
      ])
    ]
    const csv = rows.map(r => r.map(v => `"${String(v).replace(/"/g, '""')}"`).join(',')).join('\n')
    const a = document.createElement('a')
    a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }))
    a.download = 'timecards.csv'
    a.click()
  }

  return (
    <div>
      {/* Filters */}
      <div className="flex flex-wrap gap-3 mb-4 items-end">
        <div>
          <label className="block text-xs text-gray-500 mb-1">{t('reports.rangeFrom')}</label>
          <input type="date" className="border border-gray-200 rounded-lg px-3 py-1.5 text-sm"
            value={new Date(from).toISOString().slice(0, 10)}
            onChange={e => setFrom(new Date(e.target.value).getTime())} />
        </div>
        <div>
          <label className="block text-xs text-gray-500 mb-1">{t('reports.rangeTo')}</label>
          <input type="date" className="border border-gray-200 rounded-lg px-3 py-1.5 text-sm"
            value={new Date(to).toISOString().slice(0, 10)}
            onChange={e => { const d = new Date(e.target.value); d.setHours(23,59,59,999); setTo(d.getTime()) }} />
        </div>
        <div>
          <label className="block text-xs text-gray-500 mb-1">{t('users.timecardEmployee')}</label>
          <select className="border border-gray-200 rounded-lg px-3 py-1.5 text-sm"
            value={operatorId} onChange={e => setOperatorId(e.target.value)}>
            <option value="">— {t('users.timecardEmployee')}</option>
            {users.map(u => <option key={u.id} value={u.id}>{u.displayName}</option>)}
          </select>
        </div>
        <button onClick={() => { setEditCard(null); setShowModal(true) }}
          className="ml-auto px-4 py-2 bg-orange-500 text-white rounded-xl text-sm font-medium hover:bg-orange-600">
          {t('users.addTimecard')}
        </button>
        <button onClick={exportCsv}
          className="px-4 py-2 border border-gray-200 rounded-xl text-sm font-medium hover:bg-gray-50">
          {t('users.exportTimecards')}
        </button>
      </div>

      {/* Table */}
      <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
            <tr>
              <th className="px-4 py-3 text-left">{t('users.timecardEmployee')}</th>
              <th className="px-4 py-3 text-left">{t('users.colClockIn')}</th>
              <th className="px-4 py-3 text-left">{t('users.colClockOut')}</th>
              <th className="px-4 py-3 text-left">{t('users.colDuration')}</th>
              <th className="px-4 py-3 text-left">{t('users.colTerminal')}</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {loading && (
              <tr><td colSpan={6} className="text-center py-8 text-gray-400">…</td></tr>
            )}
            {!loading && data.timecards.length === 0 && (
              <tr><td colSpan={6} className="text-center py-8 text-gray-400">{t('users.timecardStillOpen')}</td></tr>
            )}
            {data.timecards.map(card => (
              <tr key={card.id} className={card.clockOutAt == null ? 'bg-orange-50' : ''}>
                <td className="px-4 py-3 font-medium">{card.operatorName}</td>
                <td className="px-4 py-3">{new Date(card.clockInAt).toLocaleString()}</td>
                <td className="px-4 py-3">
                  {card.clockOutAt
                    ? new Date(card.clockOutAt).toLocaleString()
                    : <span className="text-orange-500 font-medium">{t('users.timecardOpen')}</span>}
                </td>
                <td className="px-4 py-3">
                  {card.durationMinutes != null ? fmtMinutes(card.durationMinutes) : '—'}
                </td>
                <td className="px-4 py-3 text-gray-400">{card.terminalId || '—'}</td>
                <td className="px-4 py-3 flex gap-2 justify-end">
                  <button onClick={() => { setEditCard(card); setShowModal(true) }}
                    className="text-xs text-blue-600 hover:underline">{t('users.editTimecard')}</button>
                  <button onClick={() => handleDelete(card.id)}
                    className="text-xs text-red-500 hover:underline">{t('users.deleteTimecard')}</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {showModal && (
        <TimecardModal
          card={editCard}
          users={users}
          onClose={() => setShowModal(false)}
          onSaved={() => { setShowModal(false); load() }}
        />
      )}
    </div>
  )
}

function TimecardModal({ card, users, onClose, onSaved }: {
  card: TimecardDto | null
  users: PosUser[]
  onClose: () => void
  onSaved: () => void
}) {
  const { t } = useTranslation()
  const [operatorId, setOperatorId] = useState(card?.operatorId ?? '')
  const [clockIn, setClockIn] = useState(card ? fmtDatetimeLocal(card.clockInAt) : fmtDatetimeLocal(Date.now() - 3600000))
  const [clockOut, setClockOut] = useState(card?.clockOutAt ? fmtDatetimeLocal(card.clockOutAt) : '')
  const [clockInNote, setClockInNote] = useState(card?.clockInNote ?? '')
  const [clockOutNote] = useState(card?.clockOutNote ?? '')
  const [saving, setSaving] = useState(false)

  const duration = clockOut
    ? Math.round((parseDatetimeLocal(clockOut) - parseDatetimeLocal(clockIn)) / 60000)
    : null

  async function save() {
    if (!operatorId) return
    setSaving(true)
    try {
      const clockInMs = parseDatetimeLocal(clockIn)
      const clockOutMs = clockOut ? parseDatetimeLocal(clockOut) : null
      if (card) {
        await timecardApi.update(card.id, { clockInAt: clockInMs, clockOutAt: clockOutMs, clockInNote, clockOutNote })
      } else {
        const opName = users.find(u => u.id === operatorId)?.displayName ?? ''
        await timecardApi.create({ operatorId, operatorName: opName, clockInAt: clockInMs, clockOutAt: clockOutMs, clockInNote, clockOutNote })
      }
      onSaved()
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h2 className="text-base font-semibold">{card ? t('users.editTimecard') : t('users.newTimecard')}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 p-1 rounded-lg hover:bg-gray-100">
            <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" /></svg>
          </button>
        </div>
        <div className="px-6 py-5 space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">{t('users.timecardEmployee')}</label>
            <select className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm"
              value={operatorId} onChange={e => setOperatorId(e.target.value)}>
              <option value="">—</option>
              {users.map(u => <option key={u.id} value={u.id}>{u.displayName}</option>)}
            </select>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-gray-500 mb-1">{t('users.timecardClockInDate')} / {t('users.timecardClockInTime')}</label>
              <input type="datetime-local" className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm"
                value={clockIn} onChange={e => setClockIn(e.target.value)} />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">{t('users.timecardClockOutDate')} / {t('users.timecardClockOutTime')}</label>
              <input type="datetime-local" className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm"
                value={clockOut} onChange={e => setClockOut(e.target.value)} />
            </div>
          </div>
          {duration != null && duration >= 0 && (
            <p className="text-sm text-gray-600">{t('users.timecardDuration')}: <strong>{fmtMinutes(duration)}</strong></p>
          )}
          <div>
            <label className="block text-xs text-gray-500 mb-1">{t('users.timecardNote')}</label>
            <input type="text" className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm"
              value={clockInNote} onChange={e => setClockInNote(e.target.value)} placeholder={t('users.timecardClockInTime')} />
          </div>
        </div>
        <div className="px-6 pb-5 flex gap-3 justify-end">
          <button onClick={onClose} className="px-4 py-2 border border-gray-200 rounded-xl text-sm hover:bg-gray-50">{t('users.cancel')}</button>
          <button onClick={save} disabled={!operatorId || saving}
            className="px-4 py-2 bg-orange-500 text-white rounded-xl text-sm font-medium hover:bg-orange-600 disabled:opacity-50">
            {saving ? t('users.saving') : t('users.save')}
          </button>
        </div>
      </div>
    </div>
  )
}

// ═════════════════════════════════════════════════════════════════
// ScheduleTab — 排班 + 工时成本估算
// ═════════════════════════════════════════════════════════════════

function ScheduleTab() {
  const { t } = useTranslation()
  const [schedules, setSchedules] = useState<ShiftSchedule[]>([])
  const [users, setUsers] = useState<PosUser[]>([])
  const [laborCost, setLaborCost] = useState<LaborCostReportResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [operatorId, setOperatorId] = useState('')
  const [from, setFrom] = useState(() => {
    const d = new Date(); d.setDate(d.getDate() - d.getDay()); return d.toISOString().slice(0, 10)
  })
  const [to, setTo] = useState(() => {
    const d = new Date(); d.setDate(d.getDate() - d.getDay() + 6); return d.toISOString().slice(0, 10)
  })
  const [editing, setEditing] = useState<ShiftSchedule | null>(null)
  const [showModal, setShowModal] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [list, cost] = await Promise.all([
        scheduleApi.list({ from, to, operatorId: operatorId || undefined }),
        scheduleApi.laborCost(from, to),
      ])
      setSchedules(list)
      setLaborCost(cost)
    } finally {
      setLoading(false)
    }
  }, [from, to, operatorId])

  useEffect(() => { load() }, [load])
  useEffect(() => { userApi.list().then(setUsers) }, [])

  async function handleDelete(id: string) {
    if (!window.confirm(t('users.deleteScheduleConfirm'))) return
    await scheduleApi.delete(id)
    load()
  }

  return (
    <div>
      {/* Filters */}
      <div className="flex flex-wrap gap-3 mb-4 items-end">
        <div>
          <label className="block text-xs text-gray-500 mb-1">{t('reports.rangeFrom')}</label>
          <input type="date" className="border border-gray-200 rounded-lg px-3 py-1.5 text-sm"
            value={from} onChange={e => setFrom(e.target.value)} />
        </div>
        <div>
          <label className="block text-xs text-gray-500 mb-1">{t('reports.rangeTo')}</label>
          <input type="date" className="border border-gray-200 rounded-lg px-3 py-1.5 text-sm"
            value={to} onChange={e => setTo(e.target.value)} />
        </div>
        <div>
          <label className="block text-xs text-gray-500 mb-1">{t('users.scheduleEmployee')}</label>
          <select className="border border-gray-200 rounded-lg px-3 py-1.5 text-sm"
            value={operatorId} onChange={e => setOperatorId(e.target.value)}>
            <option value="">— {t('users.scheduleEmployee')}</option>
            {users.map(u => <option key={u.id} value={u.id}>{u.displayName}</option>)}
          </select>
        </div>
        <button onClick={() => { setEditing(null); setShowModal(true) }}
          className="ml-auto px-4 py-2 bg-orange-500 text-white rounded-xl text-sm font-medium hover:bg-orange-600">
          {t('users.scheduleAdd')}
        </button>
      </div>

      {/* Schedule table */}
      <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden mb-6">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
            <tr>
              <th className="px-4 py-3 text-left">{t('users.scheduleEmployee')}</th>
              <th className="px-4 py-3 text-left">{t('users.scheduleDate')}</th>
              <th className="px-4 py-3 text-left">{t('users.scheduleStart')}</th>
              <th className="px-4 py-3 text-left">{t('users.scheduleEnd')}</th>
              <th className="px-4 py-3 text-left">{t('users.scheduleNote')}</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {loading && (
              <tr><td colSpan={6} className="text-center py-8 text-gray-400">…</td></tr>
            )}
            {!loading && schedules.length === 0 && (
              <tr><td colSpan={6} className="text-center py-8 text-gray-400">{t('users.scheduleNone')}</td></tr>
            )}
            {schedules.map(s => (
              <tr key={s.id}>
                <td className="px-4 py-3 font-medium">{s.operatorName}</td>
                <td className="px-4 py-3">{s.date}</td>
                <td className="px-4 py-3">{s.startTime}</td>
                <td className="px-4 py-3">{s.endTime}</td>
                <td className="px-4 py-3 text-gray-400">{s.notes || '—'}</td>
                <td className="px-4 py-3 flex gap-2 justify-end">
                  <button onClick={() => { setEditing(s); setShowModal(true) }}
                    className="text-xs text-blue-600 hover:underline">{t('users.editSchedule')}</button>
                  <button onClick={() => handleDelete(s.id)}
                    className="text-xs text-red-500 hover:underline">{t('users.scheduleDelete')}</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Labor cost report */}
      <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
        <div className="px-4 py-3 border-b border-gray-100 font-medium text-gray-700">{t('users.laborCostTitle')}</div>
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
            <tr>
              <th className="px-4 py-3 text-left">{t('users.scheduleEmployee')}</th>
              <th className="px-4 py-3 text-right">{t('users.laborCostScheduledHours')}</th>
              <th className="px-4 py-3 text-right">{t('users.laborCostActualHours')}</th>
              <th className="px-4 py-3 text-right">{t('users.laborCostScheduledCost')}</th>
              <th className="px-4 py-3 text-right">{t('users.laborCostActualCost')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {(!laborCost || laborCost.summaries.length === 0) && (
              <tr><td colSpan={5} className="text-center py-8 text-gray-400">{t('users.scheduleNone')}</td></tr>
            )}
            {laborCost?.summaries.map(s => (
              <tr key={s.operatorId}>
                <td className="px-4 py-3 font-medium">{s.operatorName}</td>
                <td className="px-4 py-3 text-right">{fmtMinutes(s.scheduledMinutes)}</td>
                <td className="px-4 py-3 text-right">{fmtMinutes(s.actualMinutes)}</td>
                <td className="px-4 py-3 text-right">{fmtMoney(s.scheduledCostMinorUnit)}</td>
                <td className="px-4 py-3 text-right">{fmtMoney(s.actualCostMinorUnit)}</td>
              </tr>
            ))}
          </tbody>
          {laborCost && laborCost.summaries.length > 0 && (
            <tfoot className="bg-gray-50 font-medium">
              <tr>
                <td className="px-4 py-3">{t('users.laborCostTotal')}</td>
                <td className="px-4 py-3" />
                <td className="px-4 py-3" />
                <td className="px-4 py-3 text-right">{fmtMoney(laborCost.totalScheduledCostMinorUnit)}</td>
                <td className="px-4 py-3 text-right">{fmtMoney(laborCost.totalActualCostMinorUnit)}</td>
              </tr>
            </tfoot>
          )}
        </table>
      </div>

      {showModal && (
        <ScheduleModal
          schedule={editing}
          users={users}
          onClose={() => setShowModal(false)}
          onSaved={() => { setShowModal(false); load() }}
        />
      )}
    </div>
  )
}

function ScheduleModal({ schedule, users, onClose, onSaved }: {
  schedule: ShiftSchedule | null
  users: PosUser[]
  onClose: () => void
  onSaved: () => void
}) {
  const { t } = useTranslation()
  const [operatorId, setOperatorId] = useState(schedule?.operatorId ?? '')
  const [date, setDate] = useState(schedule?.date ?? new Date().toISOString().slice(0, 10))
  const [startTime, setStartTime] = useState(schedule?.startTime ?? '09:00')
  const [endTime, setEndTime] = useState(schedule?.endTime ?? '17:00')
  const [notes, setNotes] = useState(schedule?.notes ?? '')
  const [saving, setSaving] = useState(false)

  async function save() {
    if (!operatorId || !date) return
    setSaving(true)
    try {
      if (schedule) {
        await scheduleApi.update(schedule.id, { date, startTime, endTime, notes })
      } else {
        await scheduleApi.create({ operatorId, date, startTime, endTime, notes })
      }
      onSaved()
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={schedule ? t('users.scheduleEditTitle') : t('users.scheduleAddTitle')} onClose={onClose}>
      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">{t('users.scheduleEmployee')}</label>
          <select className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm"
            value={operatorId} onChange={e => setOperatorId(e.target.value)} disabled={!!schedule}>
            <option value="">—</option>
            {users.map(u => <option key={u.id} value={u.id}>{u.displayName}</option>)}
          </select>
        </div>
        <div>
          <label className="block text-xs text-gray-500 mb-1">{t('users.scheduleDate')}</label>
          <input type="date" className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm"
            value={date} onChange={e => setDate(e.target.value)} />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs text-gray-500 mb-1">{t('users.scheduleStart')}</label>
            <input type="time" className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm"
              value={startTime} onChange={e => setStartTime(e.target.value)} />
          </div>
          <div>
            <label className="block text-xs text-gray-500 mb-1">{t('users.scheduleEnd')}</label>
            <input type="time" className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm"
              value={endTime} onChange={e => setEndTime(e.target.value)} />
          </div>
        </div>
        <div>
          <label className="block text-xs text-gray-500 mb-1">{t('users.scheduleNote')}</label>
          <input type="text" className="w-full border border-gray-200 rounded-xl px-3 py-2 text-sm"
            value={notes} onChange={e => setNotes(e.target.value)} />
        </div>
        <div className="flex gap-3 pt-2">
          <button className="btn-secondary flex-1" onClick={onClose}>{t('users.cancel')}</button>
          <button className="btn-primary flex-1" onClick={save} disabled={!operatorId || !date || saving}>
            {saving ? t('users.saving') : t('users.save')}
          </button>
        </div>
      </div>
    </Modal>
  )
}

// ═════════════════════════════════════════════════════════════════
// Shared Modal
// ═════════════════════════════════════════════════════════════════

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/30 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-md animate-in zoom-in-95">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h2 className="text-base font-semibold text-gray-900">{title}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 p-1 rounded-lg hover:bg-gray-100">
            <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" /></svg>
          </button>
        </div>
        <div className="px-6 py-5">{children}</div>
      </div>
    </div>
  )
}
