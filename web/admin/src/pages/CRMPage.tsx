import { useEffect, useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { couponApi, fmtPrice, newId, type Coupon } from '../api/menu'
import {
  customerApi, loyaltyApi, campaignApi, giftCardApi,
  type Customer, type MembershipTier, type LoyaltyTransaction, type Campaign, type GiftCard, type GiftCardTransaction,
} from '../api/crm'

type Tab = 'customers' | 'loyalty' | 'campaigns' | 'coupons' | 'giftcards'

// ── helpers ───────────────────────────────────────────────────────────────────

function fmtDate(ms: number) {
  return new Date(ms).toLocaleDateString('zh-CN')
}

const TIER_COLORS: Record<string, string> = {
  gray: 'bg-gray-100 text-gray-700',
  blue: 'bg-blue-100 text-blue-700',
  amber: 'bg-amber-100 text-amber-800',
  purple: 'bg-purple-100 text-purple-700',
}

// ── Modal wrapper ─────────────────────────────────────────────────────────────

function Modal({ title, onClose, children, wide = false }: {
  title: string; onClose: () => void; children: React.ReactNode; wide?: boolean
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/30 backdrop-blur-sm" onClick={onClose} />
      <div className={`relative bg-white rounded-2xl shadow-2xl w-full ${wide ? 'max-w-2xl' : 'max-w-md'} max-h-[90vh] overflow-y-auto`}>
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h2 className="text-base font-semibold text-gray-900">{title}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 p-1 rounded-lg hover:bg-gray-100">
            <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
            </svg>
          </button>
        </div>
        <div className="px-6 py-5">{children}</div>
      </div>
    </div>
  )
}

// ══════════════════════════════════════════════════════════════════════════════
// Main page
// ══════════════════════════════════════════════════════════════════════════════

export default function CRMPage() {
  const { t } = useTranslation()
  const [tab, setTab] = useState<Tab>('customers')

  const TABS: [Tab, string][] = [
    ['customers', t('crm.tabCustomers')],
    ['loyalty', t('crm.tabLoyalty')],
    ['campaigns', t('crm.tabCampaigns')],
    ['coupons', t('crm.tabCoupons')],
    ['giftcards', t('crm.tabGiftCards')],
  ]

  return (
    <div className="p-8 w-full">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-gray-900">{t('crm.title')}</h1>
        <p className="mt-1 text-sm text-gray-500">{t('crm.subtitle')}</p>
      </div>

      <div className="flex gap-1 mb-6 bg-gray-100 p-1 rounded-lg w-fit flex-wrap">
        {TABS.map(([key, label]) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${
              tab === key ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === 'customers' && <CustomersTab />}
      {tab === 'loyalty' && <LoyaltyTab />}
      {tab === 'campaigns' && <CampaignsTab />}
      {tab === 'coupons' && <CouponsTab />}
      {tab === 'giftcards' && <GiftCardsTab />}
    </div>
  )
}

// ══════════════════════════════════════════════════════════════════════════════
// Customers Tab
// ══════════════════════════════════════════════════════════════════════════════

function CustomersTab() {
  const { t } = useTranslation()
  const [customers, setCustomers] = useState<Customer[]>([])
  const [tiers, setTiers] = useState<MembershipTier[]>([])
  const [loading, setLoading] = useState(true)
  const [q, setQ] = useState('')
  const [filterTier, setFilterTier] = useState('')
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<Customer | null>(null)
  const [detail, setDetail] = useState<Customer | null>(null)

  const reload = useCallback(() => {
    setLoading(true)
    Promise.all([
      customerApi.list({ q: q || undefined, tierId: filterTier || undefined }),
      loyaltyApi.getTiers(),
    ]).then(([cs, ts]) => { setCustomers(cs); setTiers(ts) }).finally(() => setLoading(false))
  }, [q, filterTier])

  useEffect(() => { reload() }, [reload])

  function tierName(id?: string) {
    return tiers.find(t => t.id === id)?.name ?? '—'
  }
  function tierColor(id?: string) {
    const color = tiers.find(t => t.id === id)?.color ?? 'gray'
    return TIER_COLORS[color] ?? TIER_COLORS.gray
  }

  async function handleDelete(c: Customer) {
    if (!confirm(t('crm.customerConfirmDelete'))) return
    await customerApi.delete(c.id)
    reload()
  }

  return (
    <>
      <div className="flex items-center gap-3 mb-4 flex-wrap">
        <input
          className="input flex-1 min-w-48 max-w-xs"
          placeholder={t('crm.customerSearch')}
          value={q} onChange={e => setQ(e.target.value)}
        />
        <select className="input w-36" value={filterTier} onChange={e => setFilterTier(e.target.value)}>
          <option value="">{t('crm.customerFilterTier')}</option>
          {tiers.map(tier => <option key={tier.id} value={tier.id}>{tier.name}</option>)}
        </select>
        <button className="btn-primary" onClick={() => setCreating(true)}>+ {t('crm.customerAdd')}</button>
      </div>

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-100 bg-gray-50/50">
              <th className="px-4 py-3 text-start font-medium text-gray-500">{t('crm.customerColName')}</th>
              <th className="px-4 py-3 text-start font-medium text-gray-500">{t('crm.customerColPhone')}</th>
              <th className="px-4 py-3 text-end font-medium text-gray-500">{t('crm.customerColVisits')}</th>
              <th className="px-4 py-3 text-end font-medium text-gray-500">{t('crm.customerColSpend')}</th>
              <th className="px-4 py-3 text-end font-medium text-gray-500">{t('crm.customerColPoints')}</th>
              <th className="px-4 py-3 text-start font-medium text-gray-500">{t('crm.customerColTier')}</th>
              <th className="px-4 py-3 text-start font-medium text-gray-500">{t('crm.customerColRegDate')}</th>
              <th className="px-4 py-3 text-end font-medium text-gray-500">{t('menu.colActions')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {loading && <tr><td colSpan={8} className="px-4 py-8 text-center text-gray-400">{t('common.loading')}</td></tr>}
            {!loading && customers.length === 0 && <tr><td colSpan={8} className="px-4 py-8 text-center text-gray-400">{t('crm.customerEmpty')}</td></tr>}
            {customers.map(c => (
              <tr key={c.id} className="hover:bg-gray-50/50 cursor-pointer" onClick={() => setDetail(c)}>
                <td className="px-4 py-3 font-medium text-gray-900">
                  {c.name}
                  {(c.tags?.length ?? 0) > 0 && (
                    <span className="ml-2 text-xs text-gray-400">{(c.tags ?? []).slice(0, 2).join(' · ')}</span>
                  )}
                </td>
                <td className="px-4 py-3 font-mono text-gray-600">{c.phone}</td>
                <td className="px-4 py-3 text-right text-gray-600">{c.totalVisits}</td>
                <td className="px-4 py-3 text-right font-mono text-gray-700">{fmtPrice(c.totalSpendMinorUnit)}</td>
                <td className="px-4 py-3 text-right font-mono text-brand-700">{c.loyaltyPoints}</td>
                <td className="px-4 py-3">
                  <span className={`badge ${tierColor(c.membershipTierId)}`}>{tierName(c.membershipTierId)}</span>
                </td>
                <td className="px-4 py-3 text-gray-500">{fmtDate(c.registeredAt)}</td>
                <td className="px-4 py-3 text-right" onClick={e => e.stopPropagation()}>
                  <div className="flex justify-end gap-2">
                    <button className="text-xs text-gray-500 hover:text-brand-600" onClick={() => setEditing(c)}>{t('menu.edit')}</button>
                    <button className="text-xs text-red-400 hover:text-red-600" onClick={() => handleDelete(c)}>{t('menu.delete')}</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {(creating || editing) && (
        <CustomerFormModal
          customer={editing ?? undefined}
          tiers={tiers}
          onClose={() => { setCreating(false); setEditing(null) }}
          onSaved={() => { setCreating(false); setEditing(null); reload() }}
        />
      )}
      {detail && (
        <CustomerDetailDrawer
          customer={detail}
          tiers={tiers}
          onClose={() => setDetail(null)}
          onUpdated={reload}
        />
      )}
    </>
  )
}

function CustomerFormModal({ customer, tiers, onClose, onSaved }: {
  customer?: Customer; tiers: MembershipTier[]; onClose: () => void; onSaved: () => void
}) {
  const { t } = useTranslation()
  const [name, setName] = useState(customer?.name ?? '')
  const [phone, setPhone] = useState(customer?.phone ?? '')
  const [email, setEmail] = useState(customer?.email ?? '')
  const [gender, setGender] = useState(customer?.gender ?? '')
  const [birthday, setBirthday] = useState(customer?.birthday ?? '')
  const [tierId, setTierId] = useState(customer?.membershipTierId ?? (tiers[0]?.id ?? ''))
  const [tagInput, setTagInput] = useState(customer?.tags?.join(', ') ?? '')
  const [notes, setNotes] = useState(customer?.notes ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  async function handleSave() {
    if (!name.trim()) { setError(t('crm.customerValidationName')); return }
    if (!phone.trim()) { setError(t('crm.customerValidationPhone')); return }
    setSaving(true); setError('')
    try {
      const tags = tagInput.split(',').map(s => s.trim()).filter(Boolean)
      const payload = { name: name.trim(), phone: phone.trim(), email: email.trim() || undefined, gender: (gender || undefined) as Customer['gender'], birthday: birthday || undefined, membershipTierId: tierId || undefined, tags, notes: notes.trim() || undefined }
      if (customer) await customerApi.update(customer.id, payload)
      else await customerApi.create(payload)
      onSaved()
    } catch { setError(t('menu.saveFailed')) }
    finally { setSaving(false) }
  }

  return (
    <Modal title={customer ? t('crm.customerEditTitle') : t('crm.customerAddTitle')} onClose={onClose}>
      <div className="space-y-3">
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{t('crm.customerFieldName')} *</label>
            <input className="input w-full" value={name} onChange={e => setName(e.target.value)} autoFocus />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{t('crm.customerFieldPhone')} *</label>
            <input className="input w-full font-mono" value={phone} onChange={e => setPhone(e.target.value)} />
          </div>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">{t('crm.customerFieldEmail')}</label>
          <input className="input w-full" type="email" value={email} onChange={e => setEmail(e.target.value)} />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{t('crm.customerFieldGender')}</label>
            <select className="input w-full" value={gender} onChange={e => setGender(e.target.value)}>
              <option value="">—</option>
              <option value="M">{t('crm.customerGenderM')}</option>
              <option value="F">{t('crm.customerGenderF')}</option>
              <option value="OTHER">{t('crm.customerGenderOther')}</option>
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{t('crm.customerFieldBirthday')}</label>
            <input className="input w-full font-mono" value={birthday} onChange={e => setBirthday(e.target.value)} placeholder="MM-DD" />
          </div>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">{t('crm.customerColTier')}</label>
          <select className="input w-full" value={tierId} onChange={e => setTierId(e.target.value)}>
            {tiers.map(tier => <option key={tier.id} value={tier.id}>{tier.name}</option>)}
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">{t('crm.customerFieldTags')}</label>
          <input className="input w-full" value={tagInput} onChange={e => setTagInput(e.target.value)} placeholder="VIP, 素食（逗号分隔）" />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">{t('crm.customerFieldNotes')}</label>
          <textarea className="input w-full resize-none" rows={2} value={notes} onChange={e => setNotes(e.target.value)} />
        </div>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex gap-3 pt-2">
          <button className="btn-secondary flex-1" onClick={onClose}>{t('menu.cancel')}</button>
          <button className="btn-primary flex-1" onClick={handleSave} disabled={saving}>{saving ? t('menu.saving') : t('menu.save')}</button>
        </div>
      </div>
    </Modal>
  )
}

function CustomerDetailDrawer({ customer, tiers, onClose, onUpdated }: {
  customer: Customer; tiers: MembershipTier[]; onClose: () => void; onUpdated: () => void
}) {
  const { t } = useTranslation()
  const [txs, setTxs] = useState<LoyaltyTransaction[]>([])
  const [adjustDelta, setAdjustDelta] = useState('')
  const [adjustReason, setAdjustReason] = useState('')
  const [adjusting, setAdjusting] = useState(false)

  useEffect(() => {
    loyaltyApi.getTransactions(customer.id).then(setTxs)
  }, [customer.id])

  const tier = tiers.find(t => t.id === customer.membershipTierId)

  async function handleAdjust() {
    const delta = parseInt(adjustDelta)
    if (isNaN(delta) || delta === 0) return
    setAdjusting(true)
    await loyaltyApi.adjustPoints(customer.id, delta, adjustReason)
    setAdjustDelta(''); setAdjustReason('')
    loyaltyApi.getTransactions(customer.id).then(setTxs)
    onUpdated()
    setAdjusting(false)
  }

  const TX_TYPE_LABELS: Record<string, string> = { EARN: '获得', REDEEM: '兑换', ADJUST: '调整', EXPIRE: '过期' }
  const TX_TYPE_COLORS: Record<string, string> = { EARN: 'text-emerald-600', REDEEM: 'text-amber-600', ADJUST: 'text-blue-600', EXPIRE: 'text-gray-400' }

  return (
    <div className="fixed inset-0 z-50 flex">
      <div className="flex-1 bg-black/20" onClick={onClose} />
      <div className="w-full max-w-md bg-white shadow-2xl overflow-y-auto flex flex-col">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h2 className="text-base font-semibold text-gray-900">{t('crm.customerDetailTitle')}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 p-1 rounded-lg hover:bg-gray-100">
            <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" /></svg>
          </button>
        </div>
        <div className="flex-1 px-6 py-5 space-y-6">
          {/* Profile */}
          <div className="flex items-start gap-4">
            <div className="w-12 h-12 rounded-full bg-brand-100 flex items-center justify-center text-brand-700 font-bold text-lg flex-shrink-0">
              {customer.name[0]}
            </div>
            <div className="flex-1 min-w-0">
              <p className="font-semibold text-gray-900">{customer.name}</p>
              <p className="text-sm text-gray-500 font-mono">{customer.phone}</p>
              {tier && <span className={`badge mt-1 ${TIER_COLORS[tier.color] ?? TIER_COLORS.gray}`}>{tier.name}</span>}
              {(customer.tags?.length ?? 0) > 0 && (
                <div className="flex flex-wrap gap-1 mt-1">
                  {(customer.tags ?? []).map(tag => (
                    <span key={tag} className="text-xs px-1.5 py-0.5 bg-gray-100 text-gray-600 rounded">{tag}</span>
                  ))}
                </div>
              )}
              {customer.notes && <p className="text-xs text-gray-400 mt-1">{customer.notes}</p>}
            </div>
          </div>

          {/* Stats */}
          <div className="grid grid-cols-3 gap-3">
            {[
              [t('crm.customerStatVisits'), String(customer.totalVisits)],
              [t('crm.customerStatSpend'), fmtPrice(customer.totalSpendMinorUnit)],
              [t('crm.customerStatPoints'), String(customer.loyaltyPoints)],
            ].map(([label, val]) => (
              <div key={label} className="bg-gray-50 rounded-xl p-3 text-center">
                <p className="text-lg font-bold text-gray-900">{val}</p>
                <p className="text-xs text-gray-400 mt-0.5">{label}</p>
              </div>
            ))}
          </div>

          {/* Adjust points */}
          <div className="border border-gray-200 rounded-xl p-4 space-y-2">
            <p className="text-xs font-medium text-gray-600">{t('crm.customerAdjustPoints')}</p>
            <div className="flex gap-2">
              <input className="input flex-1 font-mono text-sm" type="number" placeholder="+100 或 -50"
                value={adjustDelta} onChange={e => setAdjustDelta(e.target.value)} />
              <input className="input flex-1 text-sm" placeholder={t('crm.customerAdjustReason')}
                value={adjustReason} onChange={e => setAdjustReason(e.target.value)} />
              <button className="btn-primary text-xs px-3" onClick={handleAdjust} disabled={adjusting || !adjustDelta}>
                确认
              </button>
            </div>
          </div>

          {/* Loyalty transactions */}
          <div>
            <p className="text-xs font-medium text-gray-600 mb-2">{t('crm.customerLoyaltyHistory')}</p>
            {txs.length === 0
              ? <p className="text-xs text-gray-400">暂无记录</p>
              : (
                <div className="space-y-1.5">
                  {txs.map(tx => (
                    <div key={tx.id} className="flex items-center justify-between text-xs">
                      <span className={`font-medium ${TX_TYPE_COLORS[tx.type] ?? ''}`}>
                        {TX_TYPE_LABELS[tx.type] ?? tx.type}
                      </span>
                      <span className="text-gray-600 flex-1 px-2 truncate">{tx.description}</span>
                      <span className={`font-mono font-semibold ${tx.points > 0 ? 'text-emerald-600' : 'text-red-500'}`}>
                        {tx.points > 0 ? '+' : ''}{tx.points}
                      </span>
                      <span className="text-gray-400 ml-2">{fmtDate(tx.createdAt)}</span>
                    </div>
                  ))}
                </div>
              )}
          </div>
        </div>
      </div>
    </div>
  )
}

// ══════════════════════════════════════════════════════════════════════════════
// Loyalty Tab
// ══════════════════════════════════════════════════════════════════════════════

function LoyaltyTab() {
  const { t } = useTranslation()
  const [tiers, setTiers] = useState<MembershipTier[]>([])
  const [loading, setLoading] = useState(true)
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<MembershipTier | null>(null)

  const reload = useCallback(() => {
    setLoading(true)
    loyaltyApi.getTiers().then(setTiers).finally(() => setLoading(false))
  }, [])

  useEffect(() => { reload() }, [reload])

  async function handleDelete(tier: MembershipTier) {
    if (!confirm(t('crm.tierConfirmDelete'))) return
    await loyaltyApi.deleteTier(tier.id)
    reload()
  }

  const sortedTiers = [...tiers].sort((a, b) => a.minPoints - b.minPoints)
  const maxPoints = sortedTiers.length ? sortedTiers[sortedTiers.length - 1].minPoints : 5000

  return (
    <>
      <div className="flex justify-end mb-4">
        <button className="btn-primary" onClick={() => setCreating(true)}>+ {t('crm.tierAdd')}</button>
      </div>

      {loading && <p className="text-sm text-gray-400">{t('common.loading')}</p>}

      {/* Tier progression bar */}
      {!loading && sortedTiers.length > 0 && (
        <div className="card p-5 mb-6">
          <p className="text-xs font-medium text-gray-500 mb-3">积分晋升轨道</p>
          <div className="relative h-3 bg-gray-100 rounded-full mb-4">
            {sortedTiers.map((tier, i) => {
              const next = sortedTiers[i + 1]
              const left = `${(tier.minPoints / (maxPoints * 1.1)) * 100}%`
              const width = next ? `${((next.minPoints - tier.minPoints) / (maxPoints * 1.1)) * 100}%` : `${100 - (tier.minPoints / (maxPoints * 1.1)) * 100}%`
              const gradients: Record<string, string> = { gray: 'bg-gray-300', blue: 'bg-blue-400', amber: 'bg-amber-400', purple: 'bg-purple-500' }
              return <div key={tier.id} className={`absolute h-full rounded-full ${gradients[tier.color] ?? 'bg-gray-300'}`} style={{ left, width }} />
            })}
          </div>
          <div className="flex justify-between text-xs text-gray-400">
            {sortedTiers.map(tier => (
              <div key={tier.id} className="text-center">
                <span className={`badge ${TIER_COLORS[tier.color] ?? TIER_COLORS.gray} mb-1`}>{tier.name}</span>
                <p>{tier.minPoints}分</p>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {sortedTiers.map(tier => (
          <div key={tier.id} className="card p-4 space-y-3">
            <div className="flex items-center justify-between">
              <span className={`badge ${TIER_COLORS[tier.color] ?? TIER_COLORS.gray}`}>{tier.name}</span>
              <div className="flex gap-1.5">
                <button className="text-xs text-gray-400 hover:text-brand-600" onClick={() => setEditing(tier)}>{t('menu.edit')}</button>
                <button className="text-xs text-gray-400 hover:text-red-500" onClick={() => handleDelete(tier)}>{t('menu.delete')}</button>
              </div>
            </div>
            <div className="space-y-1 text-xs text-gray-600">
              <p>门槛：<span className="font-mono font-semibold">{tier.minPoints}</span> 积分</p>
              <p>折扣：{tier.discountPermille ? `${(tier.discountPermille / 10).toFixed(1)}%` : '无'}</p>
              <p>积分倍率：{((tier.pointsMultiplier ?? 100) / 100).toFixed(1)}x</p>
            </div>
            {(tier.benefits?.length ?? 0) > 0 && (
              <ul className="space-y-0.5">
                {(tier.benefits ?? []).map((b, i) => (
                  <li key={i} className="text-xs text-gray-500 flex items-start gap-1">
                    <span className="text-emerald-500 mt-0.5">✓</span>{b}
                  </li>
                ))}
              </ul>
            )}
          </div>
        ))}
      </div>

      {(creating || editing) && (
        <TierFormModal
          tier={editing ?? undefined}
          onClose={() => { setCreating(false); setEditing(null) }}
          onSaved={() => { setCreating(false); setEditing(null); reload() }}
        />
      )}
    </>
  )
}

function TierFormModal({ tier, onClose, onSaved }: { tier?: MembershipTier; onClose: () => void; onSaved: () => void }) {
  const { t } = useTranslation()
  const [name, setName] = useState(tier?.name ?? '')
  const [minPoints, setMinPoints] = useState(String(tier?.minPoints ?? 0))
  const [discount, setDiscount] = useState(String(tier?.discountPermille ?? 0))
  const [multiplier, setMultiplier] = useState(String(tier?.pointsMultiplier ?? 100))  // server omits when 100 (default)
  const [benefitInput, setBenefitInput] = useState(tier?.benefits?.join('\n') ?? '')
  const [color, setColor] = useState(tier?.color ?? 'gray')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const COLORS = [
    { value: 'gray', label: '灰（普通）' },
    { value: 'blue', label: '蓝（银卡）' },
    { value: 'amber', label: '金（金卡）' },
    { value: 'purple', label: '紫（钻石）' },
  ]

  async function handleSave() {
    if (!name.trim()) { setError('请输入等级名称'); return }
    setSaving(true); setError('')
    try {
      const payload = {
        name: name.trim(), minPoints: parseInt(minPoints) || 0,
        discountPermille: parseInt(discount) || 0,
        pointsMultiplier: parseInt(multiplier) || 100,
        benefits: benefitInput.split('\n').map(s => s.trim()).filter(Boolean),
        color,
      }
      if (tier) await loyaltyApi.updateTier(tier.id, payload)
      else await loyaltyApi.createTier(payload)
      onSaved()
    } catch { setError(t('menu.saveFailed')) }
    finally { setSaving(false) }
  }

  return (
    <Modal title={tier ? t('crm.tierEditTitle') : t('crm.tierAddTitle')} onClose={onClose}>
      <div className="space-y-3">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">{t('crm.tierFieldName')}</label>
          <input className="input w-full" value={name} onChange={e => setName(e.target.value)} autoFocus />
        </div>
        <div className="grid grid-cols-3 gap-3">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{t('crm.tierFieldMinPoints')}</label>
            <input className="input w-full font-mono" type="number" min={0} value={minPoints} onChange={e => setMinPoints(e.target.value)} />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{t('crm.tierFieldDiscount')}</label>
            <input className="input w-full font-mono" type="number" min={0} max={500} value={discount} onChange={e => setDiscount(e.target.value)} />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{t('crm.tierFieldMultiplier')}</label>
            <input className="input w-full font-mono" type="number" min={100} step={10} value={multiplier} onChange={e => setMultiplier(e.target.value)} />
          </div>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">{t('crm.tierFieldColor')}</label>
          <div className="flex gap-2">
            {COLORS.map(c => (
              <label key={c.value} className="flex items-center gap-1 text-xs cursor-pointer">
                <input type="radio" name="color" value={c.value} checked={color === c.value} onChange={() => setColor(c.value)} />
                <span className={`badge ${TIER_COLORS[c.value]}`}>{c.label}</span>
              </label>
            ))}
          </div>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">{t('crm.tierFieldBenefits')}</label>
          <textarea className="input w-full font-mono text-sm resize-none" rows={3} value={benefitInput}
            onChange={e => setBenefitInput(e.target.value)} placeholder="每行一条权益说明" />
        </div>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex gap-3 pt-2">
          <button className="btn-secondary flex-1" onClick={onClose}>{t('menu.cancel')}</button>
          <button className="btn-primary flex-1" onClick={handleSave} disabled={saving}>{saving ? t('menu.saving') : t('menu.save')}</button>
        </div>
      </div>
    </Modal>
  )
}

// ══════════════════════════════════════════════════════════════════════════════
// Campaigns Tab
// ══════════════════════════════════════════════════════════════════════════════

function CampaignsTab() {
  const { t } = useTranslation()
  const [campaigns, setCampaigns] = useState<Campaign[]>([])
  const [coupons, setCoupons] = useState<Coupon[]>([])
  const [tiers, setTiers] = useState<MembershipTier[]>([])
  const [loading, setLoading] = useState(true)
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<Campaign | null>(null)

  const reload = useCallback(() => {
    setLoading(true)
    Promise.all([campaignApi.list(), couponApi.list(), loyaltyApi.getTiers()])
      .then(([cs, cps, ts]) => { setCampaigns(cs); setCoupons(cps); setTiers(ts) })
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => { reload() }, [reload])

  async function handleDelete(c: Campaign) {
    if (!confirm(t('crm.campaignConfirmDelete'))) return
    await campaignApi.delete(c.id)
    reload()
  }

  async function handleSend(c: Campaign) {
    if (!confirm('确认发送？')) return
    await campaignApi.send(c.id)
    reload()
  }

  const STATUS_COLORS: Record<string, string> = {
    DRAFT: 'bg-gray-100 text-gray-600',
    SCHEDULED: 'bg-blue-100 text-blue-700',
    SENT: 'bg-emerald-100 text-emerald-700',
  }
  const SEGMENT_LABELS: Record<string, string> = {
    ALL: t('crm.campaignSegmentAll'), TIER: t('crm.campaignSegmentTier'),
    BIRTHDAY_MONTH: t('crm.campaignSegmentBirthday'),
    INACTIVE_30D: t('crm.campaignSegmentInactive30'), INACTIVE_60D: t('crm.campaignSegmentInactive60'),
  }

  return (
    <>
      <div className="flex justify-end mb-4">
        <button className="btn-primary" onClick={() => setCreating(true)}>+ {t('crm.campaignAdd')}</button>
      </div>

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-100 bg-gray-50/50">
              <th className="px-4 py-3 text-start font-medium text-gray-500">{t('crm.campaignColName')}</th>
              <th className="px-4 py-3 text-start font-medium text-gray-500">{t('crm.campaignColType')}</th>
              <th className="px-4 py-3 text-start font-medium text-gray-500">{t('crm.campaignColSegment')}</th>
              <th className="px-4 py-3 text-center font-medium text-gray-500">{t('crm.campaignColStatus')}</th>
              <th className="px-4 py-3 text-end font-medium text-gray-500">{t('crm.campaignColSent')}</th>
              <th className="px-4 py-3 text-end font-medium text-gray-500">{t('menu.colActions')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {loading && <tr><td colSpan={6} className="px-4 py-8 text-center text-gray-400">{t('common.loading')}</td></tr>}
            {!loading && campaigns.length === 0 && <tr><td colSpan={6} className="px-4 py-8 text-center text-gray-400">{t('crm.campaignEmpty')}</td></tr>}
            {campaigns.map(c => (
              <tr key={c.id} className="hover:bg-gray-50/50">
                <td className="px-4 py-3 font-medium text-gray-900">{c.name}</td>
                <td className="px-4 py-3 text-gray-500">{c.type === 'COUPON_PUSH' ? t('crm.campaignTypeCouponPush') : t('crm.campaignTypeInAppNotice')}</td>
                <td className="px-4 py-3 text-gray-500">
                  {SEGMENT_LABELS[c.targetSegment] ?? c.targetSegment}
                  {c.targetSegment === 'TIER' && c.targetTierId && (
                    <span className="ml-1 text-xs">({tiers.find(t => t.id === c.targetTierId)?.name})</span>
                  )}
                </td>
                <td className="px-4 py-3 text-center">
                  <span className={`badge ${STATUS_COLORS[c.status] ?? ''}`}>
                    {c.status === 'DRAFT' ? t('crm.campaignStatusDraft') : c.status === 'SCHEDULED' ? t('crm.campaignStatusScheduled') : t('crm.campaignStatusSent')}
                  </span>
                </td>
                <td className="px-4 py-3 text-right font-mono text-gray-600">{c.sentCount}</td>
                <td className="px-4 py-3 text-right">
                  <div className="flex justify-end gap-2">
                    {c.status !== 'SENT' && <button className="text-xs text-brand-600 hover:text-brand-800" onClick={() => handleSend(c)}>{t('crm.campaignSend')}</button>}
                    {c.status === 'DRAFT' && <button className="text-xs text-gray-500 hover:text-brand-600" onClick={() => setEditing(c)}>{t('menu.edit')}</button>}
                    {c.status !== 'SENT' && <button className="text-xs text-red-400 hover:text-red-600" onClick={() => handleDelete(c)}>{t('menu.delete')}</button>}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {(creating || editing) && (
        <CampaignFormModal
          campaign={editing ?? undefined}
          coupons={coupons}
          tiers={tiers}
          onClose={() => { setCreating(false); setEditing(null) }}
          onSaved={() => { setCreating(false); setEditing(null); reload() }}
        />
      )}
    </>
  )
}

function CampaignFormModal({ campaign, coupons, tiers, onClose, onSaved }: {
  campaign?: Campaign; coupons: Coupon[]; tiers: MembershipTier[]; onClose: () => void; onSaved: () => void
}) {
  const { t } = useTranslation()
  const [name, setName] = useState(campaign?.name ?? '')
  const [type, setType] = useState<Campaign['type']>(campaign?.type ?? 'COUPON_PUSH')
  const [segment, setSegment] = useState<Campaign['targetSegment']>(campaign?.targetSegment ?? 'ALL')
  const [targetTierId, setTargetTierId] = useState(campaign?.targetTierId ?? '')
  const [couponId, setCouponId] = useState(campaign?.couponId ?? '')
  const [message, setMessage] = useState(campaign?.message ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  async function handleSave() {
    if (!name.trim()) { setError('请输入活动名称'); return }
    setSaving(true); setError('')
    try {
      const payload = { name: name.trim(), type, targetSegment: segment, targetTierId: targetTierId || undefined, couponId: couponId || undefined, message: message.trim(), status: 'DRAFT' as const, scheduledAt: undefined }
      if (campaign) await campaignApi.update(campaign.id, payload)
      else await campaignApi.create(payload)
      onSaved()
    } catch { setError(t('menu.saveFailed')) }
    finally { setSaving(false) }
  }

  const SEGMENTS: [Campaign['targetSegment'], string][] = [
    ['ALL', t('crm.campaignSegmentAll')],
    ['TIER', t('crm.campaignSegmentTier')],
    ['BIRTHDAY_MONTH', t('crm.campaignSegmentBirthday')],
    ['INACTIVE_30D', t('crm.campaignSegmentInactive30')],
    ['INACTIVE_60D', t('crm.campaignSegmentInactive60')],
  ]

  return (
    <Modal title={campaign ? t('crm.campaignEditTitle') : t('crm.campaignAddTitle')} onClose={onClose} wide>
      <div className="space-y-3">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">{t('crm.campaignFieldName')}</label>
          <input className="input w-full" value={name} onChange={e => setName(e.target.value)} autoFocus />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{t('crm.campaignFieldType')}</label>
            <select className="input w-full" value={type} onChange={e => setType(e.target.value as Campaign['type'])}>
              <option value="COUPON_PUSH">{t('crm.campaignTypeCouponPush')}</option>
              <option value="IN_APP_NOTICE">{t('crm.campaignTypeInAppNotice')}</option>
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{t('crm.campaignFieldSegment')}</label>
            <select className="input w-full" value={segment} onChange={e => setSegment(e.target.value as Campaign['targetSegment'])}>
              {SEGMENTS.map(([val, label]) => <option key={val} value={val}>{label}</option>)}
            </select>
          </div>
        </div>
        {segment === 'TIER' && (
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">目标等级</label>
            <select className="input w-full" value={targetTierId} onChange={e => setTargetTierId(e.target.value)}>
              <option value="">— 请选择 —</option>
              {tiers.map(tier => <option key={tier.id} value={tier.id}>{tier.name}</option>)}
            </select>
          </div>
        )}
        {type === 'COUPON_PUSH' && (
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{t('crm.campaignFieldCoupon')}</label>
            <select className="input w-full" value={couponId} onChange={e => setCouponId(e.target.value)}>
              <option value="">— 不附带优惠券 —</option>
              {coupons.filter(c => c.isActive).map(c => <option key={c.id} value={c.id}>{c.code} ({c.type === 'FIXED' ? fmtPrice(c.value) : `${c.value / 10}%`})</option>)}
            </select>
          </div>
        )}
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">{t('crm.campaignFieldMessage')}</label>
          <textarea className="input w-full resize-none text-sm" rows={3} value={message} onChange={e => setMessage(e.target.value)} placeholder="消息正文…" />
        </div>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex gap-3 pt-2">
          <button className="btn-secondary flex-1" onClick={onClose}>{t('menu.cancel')}</button>
          <button className="btn-primary flex-1" onClick={handleSave} disabled={saving}>{saving ? t('menu.saving') : t('menu.save')}</button>
        </div>
      </div>
    </Modal>
  )
}

// ══════════════════════════════════════════════════════════════════════════════
// Coupons Tab
// ══════════════════════════════════════════════════════════════════════════════

function CouponsTab() {
  const { t } = useTranslation()
  const [coupons, setCoupons] = useState<Coupon[]>([])
  const [loading, setLoading] = useState(true)
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<Coupon | null>(null)

  const reload = useCallback(() => {
    setLoading(true)
    couponApi.list().then(setCoupons).finally(() => setLoading(false))
  }, [])

  useEffect(() => { reload() }, [reload])

  async function toggleActive(c: Coupon) {
    await couponApi.update(c.id, { isActive: !c.isActive })
    reload()
  }

  return (
    <>
      <div className="flex justify-end mb-4">
        <button className="btn-primary" onClick={() => setCreating(true)}>{t('menu.addCouponTitle')}</button>
      </div>

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-100 bg-gray-50/50">
              <th className="px-4 py-3 text-start font-medium text-gray-500">{t('menu.couponCode')}</th>
              <th className="px-4 py-3 text-start font-medium text-gray-500">{t('menu.couponType')}</th>
              <th className="px-4 py-3 text-end font-medium text-gray-500">{t('menu.couponValue')}</th>
              <th className="px-4 py-3 text-start font-medium text-gray-500">{t('menu.couponExpiry')}</th>
              <th className="px-4 py-3 text-center font-medium text-gray-500">{t('menu.colStatus')}</th>
              <th className="px-4 py-3 text-end font-medium text-gray-500">{t('menu.colActions')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {loading && <tr><td colSpan={6} className="px-4 py-8 text-center text-gray-400">{t('common.loading')}</td></tr>}
            {!loading && coupons.length === 0 && <tr><td colSpan={6} className="px-4 py-8 text-center text-gray-400">{t('menu.noCoupons')}</td></tr>}
            {coupons.map(coupon => (
              <tr key={coupon.id} className="hover:bg-gray-50/50">
                <td className="px-4 py-3 font-mono font-medium text-brand-600">{coupon.code}</td>
                <td className="px-4 py-3 text-gray-600">{coupon.type === 'FIXED' ? t('menu.couponTypeFixed') : t('menu.couponTypePercent')}</td>
                <td className="px-4 py-3 text-right font-mono">
                  {coupon.type === 'FIXED' ? fmtPrice(coupon.value) : `${(coupon.value / 10).toFixed(1)}%`}
                </td>
                <td className="px-4 py-3 text-gray-500">
                  {coupon.expiresAt > 9e15 ? t('menu.couponPermanent') : new Date(coupon.expiresAt).toLocaleDateString()}
                </td>
                <td className="px-4 py-3 text-center">
                  <button onClick={() => toggleActive(coupon)} className={`badge cursor-pointer ${coupon.isActive ? 'bg-green-100 text-green-700 hover:bg-green-200' : 'bg-gray-100 text-gray-500 hover:bg-gray-200'}`}>
                    {coupon.isActive ? t('menu.couponActive') : t('menu.couponInactive')}
                  </button>
                </td>
                <td className="px-4 py-3 text-right">
                  <button className="text-xs text-gray-500 hover:text-brand-600" onClick={() => setEditing(coupon)}>{t('menu.edit')}</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {(creating || editing) && (
        <CouponFormModal
          coupon={editing ?? undefined}
          onClose={() => { setCreating(false); setEditing(null) }}
          onSaved={() => { setCreating(false); setEditing(null); reload() }}
        />
      )}
    </>
  )
}

function CouponFormModal({ coupon, onClose, onSaved }: { coupon?: Coupon; onClose: () => void; onSaved: () => void }) {
  const { t } = useTranslation()
  const [code, setCode] = useState(coupon?.code ?? '')
  const [type, setType] = useState(coupon?.type ?? 'FIXED')
  const [value, setValue] = useState(coupon ? String(coupon.type === 'FIXED' ? coupon.value / 100 : coupon.value / 10) : '')
  const [expiresAt, setExpiresAt] = useState(
    coupon && coupon.expiresAt < 9e15 ? new Date(coupon.expiresAt).toISOString().split('T')[0] : ''
  )
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  async function handleSave() {
    if (!code.trim()) { setError(t('menu.couponCodePlaceholder')); return }
    const v = parseFloat(value)
    if (isNaN(v)) { setError(t('menu.validationCouponValue')); return }
    const rawValue = type === 'FIXED' ? Math.round(v * 100) : Math.round(v * 10)
    const exp = expiresAt ? new Date(expiresAt).getTime() : Number.MAX_SAFE_INTEGER
    setSaving(true)
    try {
      const payload = { code: code.trim(), type, value: rawValue, expiresAt: exp }
      if (coupon) await couponApi.update(coupon.id, payload)
      else await couponApi.create({ id: newId(), ...payload })
      onSaved()
    } catch { setError(t('menu.saveFailed')) } finally { setSaving(false) }
  }

  return (
    <Modal title={coupon ? t('menu.editCouponTitle') : t('menu.addCouponTitle')} onClose={onClose}>
      <div className="space-y-4">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">{t('menu.couponCode')} *</label>
          <input className="input font-mono uppercase w-full" value={code} onChange={e => setCode(e.target.value.toUpperCase())} placeholder="SAVE20" />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{t('menu.couponType')}</label>
            <select className="input w-full" value={type} onChange={e => setType(e.target.value)}>
              <option value="FIXED">{t('menu.couponTypeFixed')}</option>
              <option value="PERCENT">{t('menu.couponTypePercent')}</option>
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">
              {type === 'FIXED' ? t('menu.couponValue') : t('menu.couponDiscount')}
            </label>
            <input className="input font-mono w-full" type="number" min="0" step={type === 'FIXED' ? '0.01' : '0.1'} value={value} onChange={e => setValue(e.target.value)} />
          </div>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">{t('menu.couponExpiry')}</label>
          <input className="input w-full" type="date" value={expiresAt} onChange={e => setExpiresAt(e.target.value)} />
        </div>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex gap-3 pt-2">
          <button className="btn-secondary flex-1" onClick={onClose}>{t('menu.cancel')}</button>
          <button className="btn-primary flex-1" onClick={handleSave} disabled={saving}>{saving ? t('menu.saving') : t('menu.save')}</button>
        </div>
      </div>
    </Modal>
  )
}

// ══════════════════════════════════════════════════════════════════════════════
// Gift Cards Tab (礼品卡 / 储值卡)
// ══════════════════════════════════════════════════════════════════════════════

function GiftCardsTab() {
  const { t } = useTranslation()
  const [cards, setCards] = useState<GiftCard[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [creating, setCreating] = useState(false)
  const [detail, setDetail] = useState<GiftCard | null>(null)

  const reload = useCallback(() => {
    setLoading(true)
    giftCardApi.list(search.trim() || undefined).then(setCards).finally(() => setLoading(false))
  }, [search])

  useEffect(() => { reload() }, [reload])

  async function toggleActive(c: GiftCard) {
    await giftCardApi.setActive(c.id, !c.isActive)
    reload()
  }

  return (
    <>
      <div className="flex items-center justify-between mb-4 gap-3">
        <input className="input max-w-xs" placeholder={t('crm.giftCardSearchPlaceholder')}
          value={search} onChange={e => setSearch(e.target.value)} />
        <button className="btn-primary" onClick={() => setCreating(true)}>{t('crm.giftCardIssue')}</button>
      </div>

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-100 bg-gray-50/50">
              <th className="px-4 py-3 text-start font-medium text-gray-500">{t('crm.giftCardCode')}</th>
              <th className="px-4 py-3 text-end font-medium text-gray-500">{t('crm.giftCardBalance')}</th>
              <th className="px-4 py-3 text-start font-medium text-gray-500">{t('crm.giftCardCreated')}</th>
              <th className="px-4 py-3 text-center font-medium text-gray-500">{t('menu.colStatus')}</th>
              <th className="px-4 py-3 text-end font-medium text-gray-500">{t('menu.colActions')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {loading && <tr><td colSpan={5} className="px-4 py-8 text-center text-gray-400">{t('common.loading')}</td></tr>}
            {!loading && cards.length === 0 && <tr><td colSpan={5} className="px-4 py-8 text-center text-gray-400">{t('crm.giftCardNone')}</td></tr>}
            {cards.map(c => (
              <tr key={c.id} className="hover:bg-gray-50/50">
                <td className="px-4 py-3 font-mono font-medium text-brand-600">{c.code}</td>
                <td className="px-4 py-3 text-right font-mono">{fmtPrice(c.balanceMinorUnit)}</td>
                <td className="px-4 py-3 text-gray-500">{fmtDate(c.createdAt)}</td>
                <td className="px-4 py-3 text-center">
                  <button onClick={() => toggleActive(c)} className={`badge cursor-pointer ${c.isActive ? 'bg-green-100 text-green-700 hover:bg-green-200' : 'bg-gray-100 text-gray-500 hover:bg-gray-200'}`}>
                    {c.isActive ? t('menu.couponActive') : t('menu.couponInactive')}
                  </button>
                </td>
                <td className="px-4 py-3 text-right">
                  <button className="text-xs text-gray-500 hover:text-brand-600" onClick={() => setDetail(c)}>{t('crm.giftCardDetail')}</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {creating && (
        <GiftCardIssueModal onClose={() => setCreating(false)} onSaved={() => { setCreating(false); reload() }} />
      )}
      {detail && (
        <GiftCardDetailModal card={detail} onClose={() => setDetail(null)} onChanged={() => { reload() }} />
      )}
    </>
  )
}

function GiftCardIssueModal({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const { t } = useTranslation()
  const [code, setCode] = useState('')
  const [amount, setAmount] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  async function handleSave() {
    if (!code.trim()) { setError(t('crm.giftCardCodeRequired')); return }
    setSaving(true); setError('')
    try {
      await giftCardApi.create({ code: code.trim().toUpperCase(), initialBalanceMinorUnit: Math.round((parseFloat(amount) || 0) * 100) })
      onSaved()
    } catch (e: any) {
      setError(e?.response?.data?.error ?? t('menu.saveFailed'))
    } finally { setSaving(false) }
  }

  return (
    <Modal title={t('crm.giftCardIssue')} onClose={onClose}>
      <div className="space-y-4">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">{t('crm.giftCardCode')} *</label>
          <input className="input font-mono uppercase w-full" value={code} onChange={e => setCode(e.target.value.toUpperCase())} placeholder="GC-0001" autoFocus />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">{t('crm.giftCardInitialBalance')}</label>
          <input className="input font-mono w-full" type="number" min="0" step="0.01" value={amount} onChange={e => setAmount(e.target.value)} placeholder="0.00" />
        </div>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex gap-3 pt-2">
          <button className="btn-secondary flex-1" onClick={onClose}>{t('menu.cancel')}</button>
          <button className="btn-primary flex-1" onClick={handleSave} disabled={saving}>{saving ? t('menu.saving') : t('menu.save')}</button>
        </div>
      </div>
    </Modal>
  )
}

function GiftCardDetailModal({ card, onClose, onChanged }: { card: GiftCard; onClose: () => void; onChanged: () => void }) {
  const { t } = useTranslation()
  const [txns, setTxns] = useState<GiftCardTransaction[]>([])
  const [loading, setLoading] = useState(true)
  const [amount, setAmount] = useState('')
  const [note, setNote] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [balance, setBalance] = useState(card.balanceMinorUnit)

  const reload = useCallback(() => {
    setLoading(true)
    giftCardApi.transactions(card.id).then(setTxns).finally(() => setLoading(false))
  }, [card.id])

  useEffect(() => { reload() }, [reload])

  async function handleTopUp(sign: 1 | -1) {
    const v = parseFloat(amount)
    if (isNaN(v) || v <= 0) { setError(t('crm.giftCardAmountInvalid')); return }
    setSaving(true); setError('')
    try {
      await giftCardApi.topUp(card.id, Math.round(v * 100) * sign, note)
      setBalance(b => b + Math.round(v * 100) * sign)
      setAmount(''); setNote('')
      reload()
      onChanged()
    } catch (e: any) {
      setError(e?.response?.data?.error ?? t('menu.saveFailed'))
    } finally { setSaving(false) }
  }

  return (
    <Modal title={`${t('crm.giftCardDetail')} — ${card.code}`} onClose={onClose} wide>
      <div className="space-y-4">
        <div className="flex items-center justify-between bg-gray-50 rounded-xl px-4 py-3">
          <span className="text-sm text-gray-500">{t('crm.giftCardBalance')}</span>
          <span className="text-xl font-mono font-semibold text-brand-600">{fmtPrice(balance)}</span>
        </div>

        <div className="flex gap-2 items-end">
          <div className="flex-1">
            <label className="block text-xs font-medium text-gray-600 mb-1">{t('crm.giftCardAdjustAmount')}</label>
            <input className="input font-mono w-full" type="number" min="0" step="0.01" value={amount} onChange={e => setAmount(e.target.value)} placeholder="0.00" />
          </div>
          <div className="flex-1">
            <label className="block text-xs font-medium text-gray-600 mb-1">{t('crm.giftCardNote')}</label>
            <input className="input w-full" value={note} onChange={e => setNote(e.target.value)} />
          </div>
          <button className="btn-secondary" disabled={saving} onClick={() => handleTopUp(1)}>{t('crm.giftCardTopUp')}</button>
          <button className="btn-secondary" disabled={saving} onClick={() => handleTopUp(-1)}>{t('crm.giftCardDeduct')}</button>
        </div>
        {error && <p className="text-sm text-red-600">{error}</p>}

        <div className="card overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100 bg-gray-50/50">
                <th className="px-3 py-2 text-start font-medium text-gray-500">{t('crm.giftCardTxnType')}</th>
                <th className="px-3 py-2 text-end font-medium text-gray-500">{t('crm.giftCardTxnAmount')}</th>
                <th className="px-3 py-2 text-start font-medium text-gray-500">{t('crm.giftCardNote')}</th>
                <th className="px-3 py-2 text-start font-medium text-gray-500">{t('crm.giftCardCreated')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {loading && <tr><td colSpan={4} className="px-3 py-6 text-center text-gray-400">{t('common.loading')}</td></tr>}
              {!loading && txns.length === 0 && <tr><td colSpan={4} className="px-3 py-6 text-center text-gray-400">{t('crm.giftCardNoTxns')}</td></tr>}
              {txns.map(tx => (
                <tr key={tx.id}>
                  <td className="px-3 py-2 text-gray-600">{tx.type}</td>
                  <td className={`px-3 py-2 text-right font-mono ${tx.amountMinorUnit < 0 ? 'text-red-600' : 'text-green-600'}`}>
                    {tx.amountMinorUnit > 0 ? '+' : ''}{fmtPrice(tx.amountMinorUnit)}
                  </td>
                  <td className="px-3 py-2 text-gray-500">{tx.note || '—'}</td>
                  <td className="px-3 py-2 text-gray-400">{fmtDate(tx.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </Modal>
  )
}
