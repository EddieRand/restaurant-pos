import { useEffect, useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import {
  menuApi, comboApi, categoryApi, modifierGroupApi, menuProfileApi, channelApi,
  parseName, fmtPrice, newId,
  type MenuItem, type Combo, type MenuCategory, type ModifierGroup, type ModifierOption, type MenuProfile, type Channel,
} from '../api/menu'
import { settingsApi } from '../api/admin'
import type { TaxRate } from '../api/admin'

// ── Tab type ─────────────────────────────────────────────────────────────────
type Tab = 'items' | 'combos' | 'categories' | 'modifiers' | 'channels' | 'schedules'

const ALLERGEN_KEYS = ['peanut', 'milk', 'egg', 'wheat', 'soy', 'fish', 'shellfish', 'nuts'] as const

const SUPPORTED_LOCALES: { code: string; label: string; placeholder: string }[] = [
  { code: 'zh-CN', label: '中文（简体）', placeholder: '如：红烧肉' },
  { code: 'en-US', label: 'English',      placeholder: 'e.g. Braised Pork' },
  { code: 'zh-TW', label: '中文（繁體）', placeholder: '如：紅燒肉' },
  { code: 'ja-JP', label: '日本語',        placeholder: '例：豚の角煮' },
  { code: 'ko-KR', label: '한국어',        placeholder: '예: 홍소육' },
  { code: 'ar-SA', label: 'العربية',      placeholder: 'مثال: لحم مشوي' },
  { code: 'ms-MY', label: 'Bahasa Malaysia', placeholder: 'cth: Daging Braised' },
  { code: 'th-TH', label: 'ภาษาไทย',    placeholder: 'เช่น: หมูตุ๋น' },
  { code: 'vi-VN', label: 'Tiếng Việt',  placeholder: 'vd: Thịt kho tàu' },
]

const CATEGORIES = [
  { id: 'cat-starters', key: 'starters' },
  { id: 'cat-mains', key: 'mains' },
  { id: 'cat-desserts', key: 'desserts' },
  { id: 'cat-drinks', key: 'drinks' },
  { id: 'cat-other', key: 'other' },
]

// ══════════════════════════════════════════════════════════════════════════════
// Main Page
// ══════════════════════════════════════════════════════════════════════════════

export default function MenuPage() {
  const { t } = useTranslation()
  const [tab, setTab] = useState<Tab>('items')

  return (
    <div className="p-8 w-full">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-gray-900">{t('menu.title')}</h1>
        <p className="mt-1 text-sm text-gray-500">{t('menu.subtitle')}</p>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 mb-6 bg-gray-100 p-1 rounded-lg w-fit">
        {([['items', t('menu.tabItems')], ['combos', t('menu.tabCombos')], ['categories', t('menu.tabCategories')], ['modifiers', t('menu.tabModifiers')], ['channels', t('menu.tabChannels')], ['schedules', t('menu.tabSchedules')]] as [Tab, string][]).map(
          ([key, label]) => (
            <button
              key={key}
              onClick={() => setTab(key)}
              className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${
                tab === key
                  ? 'bg-white text-gray-900 shadow-sm'
                  : 'text-gray-500 hover:text-gray-700'
              }`}
            >
              {label}
            </button>
          )
        )}
      </div>

      {tab === 'items' && <ItemsTab />}
      {tab === 'combos' && <CombosTab />}
      {tab === 'categories' && <CategoriesTab />}
      {tab === 'modifiers' && <ModifiersTab />}
      {tab === 'channels' && <ChannelsTab />}
      {tab === 'schedules' && <SchedulesTab />}
    </div>
  )
}

// ══════════════════════════════════════════════════════════════════════════════
// Items Tab
// ══════════════════════════════════════════════════════════════════════════════

// ── Shared image placeholder ──────────────────────────────────────────────────
function ImgPlaceholder({ className = '' }: { className?: string }) {
  return (
    <div className={`bg-gray-100 flex items-center justify-center text-gray-300 ${className}`}>
      <svg width="32" height="32" viewBox="0 0 24 24" fill="currentColor">
        <path d="M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z"/>
      </svg>
    </div>
  )
}

// ── Bulk add modal (spreadsheet-style inline editing) ─────────────────────────
interface BulkRow { id: string; nameZh: string; nameEn: string; price: string; categoryId: string; desc: string }

function emptyRow(defaultCatId: string): BulkRow {
  return { id: crypto.randomUUID(), nameZh: '', nameEn: '', price: '', categoryId: defaultCatId, desc: '' }
}

function BulkAddModal({ categories, onClose, onSaved }: {
  categories: MenuCategory[]
  onClose: () => void
  onSaved: () => void
}) {
  const defaultCat = categories[0]?.id ?? ''
  const [rows, setRows] = useState<BulkRow[]>([emptyRow(defaultCat), emptyRow(defaultCat), emptyRow(defaultCat)])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  function setField(id: string, field: keyof BulkRow, value: string) {
    setRows(prev => prev.map(r => r.id === id ? { ...r, [field]: value } : r))
  }

  function addRow() {
    setRows(prev => [...prev, emptyRow(defaultCat)])
  }

  function removeRow(id: string) {
    setRows(prev => prev.filter(r => r.id !== id))
  }

  // Tab to next cell / add row at end
  function handleKeyDown(e: React.KeyboardEvent, rowIdx: number, field: keyof BulkRow) {
    if (e.key === 'Enter' || (e.key === 'Tab' && !e.shiftKey && field === 'desc')) {
      e.preventDefault()
      if (rowIdx === rows.length - 1) addRow()
      // focus next row's first input
      setTimeout(() => {
        const allInputs = document.querySelectorAll<HTMLElement>('[data-bulk-row]')
        const next = allInputs[(rowIdx + 1) * 5]
        next?.focus()
      }, 0)
    }
  }

  async function handleSave() {
    const valid = rows.filter(r => r.nameZh.trim())
    if (valid.length === 0) { setError('至少填写一行中文名称'); return }
    setSaving(true)
    setError('')
    try {
      for (const row of valid) {
        const priceVal = Math.round(parseFloat(row.price) * 100)
        await menuApi.create({
          id: newId(),
          names: JSON.stringify({ 'zh-CN': row.nameZh.trim(), 'en-US': row.nameEn.trim() }),
          description: row.desc.trim() ? JSON.stringify({ 'zh-CN': row.desc.trim(), 'en-US': '' }) : undefined,
          priceMinorUnit: isNaN(priceVal) ? 0 : priceVal,
          categoryId: row.categoryId || defaultCat || 'other',
          course: 1,
          allergens: '',
        })
      }
      onSaved()
    } catch {
      setError('保存失败，请重试')
    } finally {
      setSaving(false)
    }
  }

  const filled = rows.filter(r => r.nameZh.trim()).length

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-4xl mx-4 flex flex-col max-h-[85vh]">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 flex-shrink-0">
          <div>
            <h2 className="text-base font-semibold text-gray-900">批量新增菜品</h2>
            <p className="text-xs text-gray-400 mt-0.5">按 Enter 或 Tab 跳转下一行，留空行会自动跳过</p>
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl leading-none">×</button>
        </div>

        {/* Table */}
        <div className="overflow-auto flex-1">
          <table className="w-full text-sm border-collapse">
            <thead className="sticky top-0 bg-gray-50 z-10">
              <tr>
                <th className="px-3 py-2.5 text-start text-xs font-medium text-gray-500 w-6">#</th>
                <th className="px-3 py-2.5 text-start text-xs font-medium text-gray-500">中文名称 *</th>
                <th className="px-3 py-2.5 text-start text-xs font-medium text-gray-500">英文名称</th>
                <th className="px-3 py-2.5 text-start text-xs font-medium text-gray-500 w-28">价格（元）</th>
                <th className="px-3 py-2.5 text-start text-xs font-medium text-gray-500 w-32">分类</th>
                <th className="px-3 py-2.5 text-start text-xs font-medium text-gray-500">描述</th>
                <th className="w-8" />
              </tr>
            </thead>
            <tbody>
              {rows.map((row, idx) => (
                <tr key={row.id} className={`border-t border-gray-50 ${row.nameZh.trim() ? 'bg-white' : 'bg-gray-50/30'}`}>
                  <td className="px-3 py-1.5 text-xs text-gray-300 text-center select-none">{idx + 1}</td>
                  <td className="px-1 py-1">
                    <input
                      data-bulk-row
                      className="w-full px-2 py-1.5 text-sm rounded border border-transparent hover:border-gray-200 focus:border-brand-400 focus:outline-none focus:ring-1 focus:ring-brand-200 bg-transparent"
                      placeholder="如：红烧肉"
                      value={row.nameZh}
                      onChange={e => setField(row.id, 'nameZh', e.target.value)}
                      onKeyDown={e => e.key === 'Enter' && handleKeyDown(e, idx, 'nameZh')}
                    />
                  </td>
                  <td className="px-1 py-1">
                    <input
                      data-bulk-row
                      className="w-full px-2 py-1.5 text-sm rounded border border-transparent hover:border-gray-200 focus:border-brand-400 focus:outline-none focus:ring-1 focus:ring-brand-200 bg-transparent"
                      placeholder="Braised Pork"
                      value={row.nameEn}
                      onChange={e => setField(row.id, 'nameEn', e.target.value)}
                      onKeyDown={e => e.key === 'Enter' && handleKeyDown(e, idx, 'nameEn')}
                    />
                  </td>
                  <td className="px-1 py-1">
                    <input
                      data-bulk-row
                      className="w-full px-2 py-1.5 text-sm rounded border border-transparent hover:border-gray-200 focus:border-brand-400 focus:outline-none focus:ring-1 focus:ring-brand-200 bg-transparent font-mono"
                      placeholder="68.00"
                      type="number"
                      min="0"
                      step="0.01"
                      value={row.price}
                      onChange={e => setField(row.id, 'price', e.target.value)}
                      onKeyDown={e => e.key === 'Enter' && handleKeyDown(e, idx, 'price')}
                    />
                  </td>
                  <td className="px-1 py-1">
                    <select
                      data-bulk-row
                      className="w-full px-2 py-1.5 text-sm rounded border border-transparent hover:border-gray-200 focus:border-brand-400 focus:outline-none focus:ring-1 focus:ring-brand-200 bg-transparent"
                      value={row.categoryId}
                      onChange={e => setField(row.id, 'categoryId', e.target.value)}
                    >
                      {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                    </select>
                  </td>
                  <td className="px-1 py-1">
                    <input
                      data-bulk-row
                      className="w-full px-2 py-1.5 text-sm rounded border border-transparent hover:border-gray-200 focus:border-brand-400 focus:outline-none focus:ring-1 focus:ring-brand-200 bg-transparent"
                      placeholder="选填，菜品简介"
                      value={row.desc}
                      onChange={e => setField(row.id, 'desc', e.target.value)}
                      onKeyDown={e => handleKeyDown(e, idx, 'desc')}
                    />
                  </td>
                  <td className="px-2 py-1 text-center">
                    <button
                      onClick={() => removeRow(row.id)}
                      className="text-gray-200 hover:text-red-400 transition-colors text-base leading-none"
                      title="删除此行"
                    >×</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between px-6 py-4 border-t border-gray-100 flex-shrink-0">
          <button
            onClick={addRow}
            className="text-sm text-brand-600 hover:text-brand-800 font-medium flex items-center gap-1"
          >
            <span className="text-lg leading-none">+</span> 新增一行
          </button>
          <div className="flex items-center gap-4">
            {error && <p className="text-xs text-red-600">{error}</p>}
            {filled > 0 && <span className="text-xs text-gray-400">{filled} 条待保存</span>}
            <button className="btn-secondary" onClick={onClose}>取消</button>
            <button className="btn-primary" onClick={handleSave} disabled={saving || filled === 0}>
              {saving ? '保存中…' : `保存 ${filled} 条`}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

// ── Items Tab ─────────────────────────────────────────────────────────────────

function ItemsTab() {
  const { t } = useTranslation()
  const [items, setItems] = useState<MenuItem[]>([])
  const [categories, setCategories] = useState<MenuCategory[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [catFilter, setCatFilter] = useState('all')
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid')
  const [editing, setEditing] = useState<MenuItem | null>(null)
  const [creating, setCreating] = useState(false)
  const [importing, setImporting] = useState(false)
  const [selected, setSelected] = useState<Set<string>>(new Set())

  const reload = useCallback(() => {
    setLoading(true)
    Promise.all([menuApi.list(), categoryApi.list()]).then(([menuList, catList]) => {
      setItems(menuList)
      setCategories(catList.sort((a, b) => a.sortOrder - b.sortOrder))
    }).finally(() => setLoading(false))
  }, [])

  useEffect(() => { reload() }, [reload])

  const filtered = items.filter(item => {
    const name = parseName(item.names).toLowerCase()
    const matchSearch = !search || name.includes(search.toLowerCase())
    const matchCat = catFilter === 'all' || item.categoryId === catFilter
    return matchSearch && matchCat
  })

  const catMap = new Map(categories.map(c => [c.id, c]))
  const grouped: { cat: MenuCategory | null; catLabel: string; items: MenuItem[] }[] = []
  if (catFilter !== 'all') {
    const cat = catMap.get(catFilter) ?? null
    grouped.push({ cat, catLabel: cat?.name ?? catFilter, items: [...filtered].sort((a, b) => a.course - b.course) })
  } else {
    const seen = new Set<string>()
    for (const c of categories) {
      const catItems = filtered.filter(i => i.categoryId === c.id).sort((a, b) => a.course - b.course)
      if (catItems.length > 0) { grouped.push({ cat: c, catLabel: c.name, items: catItems }); seen.add(c.id) }
    }
    const rest = filtered.filter(i => !seen.has(i.categoryId)).sort((a, b) => a.course - b.course)
    if (rest.length > 0) grouped.push({ cat: null, catLabel: '(未分类)', items: rest })
  }

  async function handleDelete(id: string) {
    if (!confirm(t('menu.confirmDelete'))) return
    await menuApi.delete(id)
    reload()
  }

  async function toggleSoldOut(item: MenuItem) {
    await menuApi.update(item.id, { isSoldOut: !item.isSoldOut })
    reload()
  }

  async function bulkSoldOut(soldOut: boolean) {
    if (selected.size === 0) return
    await menuApi.bulkAvailability(Array.from(selected), soldOut)
    setSelected(new Set())
    reload()
  }

  function toggleSelect(id: string) {
    setSelected(prev => { const n = new Set(prev); n.has(id) ? n.delete(id) : n.add(id); return n })
  }

  async function moveCourse(item: MenuItem, groupItems: MenuItem[], direction: -1 | 1) {
    const sorted = [...groupItems].sort((a, b) => a.course - b.course)
    const idx = sorted.findIndex(i => i.id === item.id)
    const swapIdx = idx + direction
    if (swapIdx < 0 || swapIdx >= sorted.length) return
    const other = sorted[swapIdx]
    const newMyCourse = other.course === item.course ? item.course + direction : other.course
    const newOtherCourse = other.course === item.course ? item.course : item.course
    await Promise.all([
      menuApi.update(item.id, { course: newMyCourse }),
      menuApi.update(other.id, { course: newOtherCourse }),
    ])
    reload()
  }

  return (
    <>
      {/* Toolbar */}
      <div className="flex items-center gap-3 mb-4 flex-wrap">
        <input className="input w-56" placeholder={t('menu.searchPlaceholder')} value={search} onChange={e => setSearch(e.target.value)} />
        <select className="input w-36" value={catFilter} onChange={e => setCatFilter(e.target.value)}>
          <option value="all">{t('menu.allCategories')}</option>
          {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>

        {/* View toggle */}
        <div className="flex rounded-lg border border-gray-200 overflow-hidden">
          <button
            onClick={() => setViewMode('grid')}
            className={`px-3 py-1.5 text-xs transition-colors ${viewMode === 'grid' ? 'bg-gray-800 text-white' : 'bg-white text-gray-500 hover:bg-gray-50'}`}
            title="网格视图"
          >⊞</button>
          <button
            onClick={() => setViewMode('list')}
            className={`px-3 py-1.5 text-xs transition-colors ${viewMode === 'list' ? 'bg-gray-800 text-white' : 'bg-white text-gray-500 hover:bg-gray-50'}`}
            title="列表视图"
          >☰</button>
        </div>

        <div className="ml-auto flex gap-2">
          {selected.size > 0 && (
            <>
              <button className="btn-secondary text-xs" onClick={() => bulkSoldOut(false)}>{t('menu.bulkAvailable')} ({selected.size})</button>
              <button className="btn-secondary text-xs text-amber-600 border-amber-200 hover:bg-amber-50" onClick={() => bulkSoldOut(true)}>{t('menu.bulkSoldOut')} ({selected.size})</button>
            </>
          )}
          <button className="btn-secondary text-sm" onClick={() => setImporting(true)}>批量新增</button>
          <button className="btn-primary" onClick={() => setCreating(true)}>{t('menu.addItem')}</button>
        </div>
      </div>

      {loading ? (
        <div className="card px-4 py-8 text-center text-gray-400 text-sm">{t('common.loading')}</div>
      ) : filtered.length === 0 ? (
        <div className="card px-4 py-8 text-center text-gray-400 text-sm">{t('menu.noItems')}</div>
      ) : (
        <div className="space-y-5">
          {grouped.map(({ cat, catLabel, items: groupItems }) => (
            <div key={cat?.id ?? '__uncategorised'}>
              <div className="flex items-center gap-2 mb-2">
                <span className="text-xs font-semibold text-gray-500 uppercase tracking-wide">{catLabel}</span>
                <span className="text-xs text-gray-400">({groupItems.length})</span>
                <div className="flex-1 h-px bg-gray-100" />
              </div>

              {viewMode === 'grid' ? (
                /* ── Grid View ── */
                <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-3">
                  {groupItems.map((item, idx) => (
                    <div
                      key={item.id}
                      className={`relative rounded-xl border bg-white overflow-hidden cursor-pointer group transition-shadow hover:shadow-md ${
                        selected.has(item.id) ? 'ring-2 ring-brand-500 border-brand-300' : 'border-gray-100'
                      } ${item.isSoldOut ? 'opacity-60' : ''}`}
                      onClick={() => toggleSelect(item.id)}
                    >
                      {/* Image */}
                      {item.imageUrl
                        ? <img src={item.imageUrl} alt="" className="w-full aspect-square object-cover" />
                        : <ImgPlaceholder className="w-full aspect-square" />
                      }
                      {/* Sold-out overlay */}
                      {item.isSoldOut && (
                        <div className="absolute inset-0 bg-white/60 flex items-center justify-center">
                          <span className="text-xs font-bold text-red-500 bg-white/90 px-2 py-0.5 rounded-full border border-red-200">售罄</span>
                        </div>
                      )}
                      {/* Sort arrows overlay */}
                      <div className="absolute top-1 left-1 flex flex-col gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
                        <button disabled={idx === 0} onClick={e => { e.stopPropagation(); moveCourse(item, groupItems, -1) }}
                          className="w-5 h-5 rounded bg-white/90 text-gray-600 disabled:opacity-20 text-xs leading-none shadow hover:bg-white">▲</button>
                        <button disabled={idx === groupItems.length - 1} onClick={e => { e.stopPropagation(); moveCourse(item, groupItems, 1) }}
                          className="w-5 h-5 rounded bg-white/90 text-gray-600 disabled:opacity-20 text-xs leading-none shadow hover:bg-white">▼</button>
                      </div>
                      {/* Info */}
                      <div className="p-2">
                        <div className="text-xs font-semibold text-gray-900 truncate">{parseName(item.names, 'zh-CN')}</div>
                        <div className="text-xs text-gray-400 truncate">{parseName(item.names, 'en-US')}</div>
                        <div className="mt-1 flex items-center justify-between">
                          <span className="text-sm font-bold text-brand-600">{fmtPrice(item.priceMinorUnit)}</span>
                          <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                            <button onClick={e => { e.stopPropagation(); toggleSoldOut(item) }}
                              className={`text-xs px-1.5 py-0.5 rounded-full border ${item.isSoldOut ? 'text-green-600 border-green-200 hover:bg-green-50' : 'text-amber-600 border-amber-200 hover:bg-amber-50'}`}>
                              {item.isSoldOut ? '上架' : '售罄'}
                            </button>
                            <button onClick={e => { e.stopPropagation(); setEditing(item) }}
                              className="text-xs px-1.5 py-0.5 rounded-full border border-gray-200 text-gray-500 hover:bg-gray-50">编辑</button>
                          </div>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                /* ── List View ── */
                <div className="card overflow-hidden">
                  <table className="w-full text-sm">
                    <tbody className="divide-y divide-gray-50">
                      {groupItems.map((item, idx) => (
                        <tr key={item.id} className="hover:bg-gray-50/50 transition-colors">
                          <td className="pl-3 pr-1 py-3 w-10">
                            <div className="flex flex-col gap-0.5">
                              <button disabled={idx === 0} onClick={() => moveCourse(item, groupItems, -1)}
                                className="text-gray-300 hover:text-gray-600 disabled:opacity-20 disabled:cursor-not-allowed leading-none text-xs">▲</button>
                              <button disabled={idx === groupItems.length - 1} onClick={() => moveCourse(item, groupItems, 1)}
                                className="text-gray-300 hover:text-gray-600 disabled:opacity-20 disabled:cursor-not-allowed leading-none text-xs">▼</button>
                            </div>
                          </td>
                          <td className="px-2 py-3 w-8">
                            <input type="checkbox" className="rounded" checked={selected.has(item.id)} onChange={() => toggleSelect(item.id)} />
                          </td>
                          {/* Thumbnail */}
                          <td className="px-2 py-2 w-12">
                            {item.imageUrl
                              ? <img src={item.imageUrl} alt="" className="w-10 h-10 rounded-lg object-cover" />
                              : <ImgPlaceholder className="w-10 h-10 rounded-lg" />
                            }
                          </td>
                          <td className="px-4 py-3">
                            <div className="font-medium text-gray-900">{parseName(item.names, 'zh-CN')}</div>
                            <div className="text-xs text-gray-400">{parseName(item.names, 'en-US')}</div>
                          </td>
                          <td className="px-4 py-3 text-end font-mono font-medium text-gray-900">{fmtPrice(item.priceMinorUnit)}</td>
                          <td className="px-4 py-3 text-center">
                            <button onClick={() => toggleSoldOut(item)}
                              className={`badge cursor-pointer ${item.isSoldOut ? 'bg-red-100 text-red-700 hover:bg-red-200' : 'bg-green-100 text-green-700 hover:bg-green-200'}`}>
                              {item.isSoldOut ? t('menu.statusSoldOut') : t('menu.statusAvailable')}
                            </button>
                          </td>
                          <td className="px-4 py-3 text-end">
                            <div className="flex gap-2 justify-end">
                              <button className="text-xs text-gray-500 hover:text-brand-600 transition-colors" onClick={() => setEditing(item)}>{t('menu.edit')}</button>
                              <button className="text-xs text-gray-500 hover:text-red-600 transition-colors" onClick={() => handleDelete(item.id)}>{t('menu.delete')}</button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {(creating || editing) && (
        <ItemFormModal
          item={editing ?? undefined}
          onClose={() => { setCreating(false); setEditing(null) }}
          onSaved={() => { setCreating(false); setEditing(null); reload() }}
        />
      )}
      {importing && (
        <BulkAddModal
          categories={categories}
          onClose={() => setImporting(false)}
          onSaved={() => { setImporting(false); reload() }}
        />
      )}
    </>
  )
}

// ── Shared: multi-locale name editor ─────────────────────────────────────────

function parseNamesObj(raw: string): Record<string, string> {
  try { return JSON.parse(raw) } catch { return { 'zh-CN': raw } }
}

interface NamesEditorProps {
  value: Record<string, string>
  onChange: (v: Record<string, string>) => void
  required?: boolean   // marks zh-CN as required
}

function NamesEditor({ value, onChange, required = true }: NamesEditorProps) {
  const [expanded, setExpanded] = useState(false)
  const primary = SUPPORTED_LOCALES.slice(0, 2)   // zh-CN, en-US always shown
  const extra   = SUPPORTED_LOCALES.slice(2)        // rest collapsible

  function setLocale(code: string, text: string) {
    onChange({ ...value, [code]: text })
  }

  const filledExtra = extra.filter(l => value[l.code]?.trim()).length

  return (
    <div className="space-y-2">
      {primary.map(loc => (
        <div key={loc.code} className="flex items-center gap-2">
          <span className="text-xs text-gray-400 w-28 flex-shrink-0 text-right">{loc.label}{loc.code === 'zh-CN' && required ? ' *' : ''}</span>
          <input
            className="input flex-1"
            placeholder={loc.placeholder}
            value={value[loc.code] ?? ''}
            onChange={e => setLocale(loc.code, e.target.value)}
            autoFocus={loc.code === 'zh-CN'}
          />
        </div>
      ))}
      {/* Expand toggle */}
      <button
        type="button"
        onClick={() => setExpanded(v => !v)}
        className="text-xs text-brand-600 hover:text-brand-800 flex items-center gap-1 pl-30"
      >
        <span className={`transition-transform ${expanded ? 'rotate-90' : ''}`}>▶</span>
        {expanded ? '收起其他语言' : `其他语言${filledExtra > 0 ? `（已填 ${filledExtra}）` : ''}`}
      </button>
      {expanded && extra.map(loc => (
        <div key={loc.code} className="flex items-center gap-2">
          <span className="text-xs text-gray-400 w-28 flex-shrink-0 text-right">{loc.label}</span>
          <input
            className="input flex-1"
            placeholder={loc.placeholder}
            value={value[loc.code] ?? ''}
            onChange={e => setLocale(loc.code, e.target.value)}
          />
        </div>
      ))}
    </div>
  )
}

// ── Item Form Modal ───────────────────────────────────────────────────────────

function parseDesc(json: string | undefined, lang: string): string {
  if (!json) return ''
  try {
    const obj = JSON.parse(json)
    return obj[lang] ?? obj['zh-CN'] ?? obj['zh'] ?? ''
  } catch { return '' }
}

interface ItemFormProps {
  item?: MenuItem
  onClose: () => void
  onSaved: () => void
}

function ItemFormModal({ item, onClose, onSaved }: ItemFormProps) {
  const { t } = useTranslation()
  const [names, setNames] = useState<Record<string, string>>(
    item ? parseNamesObj(item.names) : { 'zh-CN': '' }
  )
  const [descZh, setDescZh] = useState(item ? parseDesc(item.description, 'zh-CN') : '')
  const [descEn, setDescEn] = useState(item ? parseDesc(item.description, 'en-US') : '')
  const [price, setPrice] = useState(item ? String(item.priceMinorUnit / 100) : '')
  const [categoryId, setCategoryId] = useState(item?.categoryId ?? '')
  const [taxRateId, setTaxRateId] = useState(item?.taxRateId ?? '')
  const [course, setCourse] = useState(item?.course ?? 1)
  const [allergens, setAllergens] = useState<string[]>(
    item?.allergens ? item.allergens.split('|').filter(Boolean) : []
  )
  const [modifierGroups, setModifierGroups] = useState<ModifierGroup[]>(
    item?.modifierGroups ?? []
  )
  const [imageUrl, setImageUrl] = useState(item?.imageUrl ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [categories, setCategories] = useState<MenuCategory[]>([])
  const [taxRates, setTaxRates] = useState<TaxRate[]>([])
  const [templateGroups, setTemplateGroups] = useState<ModifierGroup[]>([])
  const [showTemplates, setShowTemplates] = useState(false)
  const [availableChannels, setAvailableChannels] = useState<string[]>(item?.availableChannels ?? [])
  const [allChannels, setAllChannels] = useState<Channel[]>([])
  const [allProfiles, setAllProfiles] = useState<MenuProfile[]>([])
  const [stockCount, setStockCount] = useState<string>(
    item?.stockCount != null ? String(item.stockCount) : ''
  )

  useEffect(() => {
    categoryApi.list().then(list => {
      setCategories(list)
      if (!item?.categoryId && list.length > 0) setCategoryId(list[0].id)
    })
    settingsApi.getRegionConfig().then(cfg => setTaxRates(cfg.availableTaxRates))
    modifierGroupApi.list().then(setTemplateGroups)
    menuProfileApi.list().then(setAllProfiles)
    channelApi.list().then(chs => setAllChannels(chs.filter(c => c.enabled)))
  }, [])

  async function handleSave() {
    if (!names['zh-CN']?.trim()) { setError(t('menu.validationNameZh')); return }
    const priceVal = Math.round(parseFloat(price) * 100)
    if (isNaN(priceVal) || priceVal < 0) { setError(t('menu.validationPrice')); return }

    setSaving(true)
    setError('')
    try {
      const description = (descZh || descEn)
        ? JSON.stringify({ 'zh-CN': descZh.trim(), 'en-US': descEn.trim() })
        : undefined
      const cleanNames = Object.fromEntries(Object.entries(names).filter(([, v]) => v.trim()))
      const payload = {
        names: JSON.stringify(cleanNames),
        description,
        priceMinorUnit: priceVal,
        categoryId: categoryId || 'other',
        taxRateId: taxRateId || undefined,
        course,
        allergens: allergens.join('|'),
        modifierGroups,
        availableChannels,
        stockCount: stockCount.trim() !== '' ? Math.floor(Number(stockCount)) : undefined,
        imageUrl: imageUrl.trim() || undefined,
      }
      if (item) {
        await menuApi.update(item.id, payload)
      } else {
        await menuApi.create({ id: newId(), ...payload })
      }
      onSaved()
    } catch {
      setError(t('menu.saveFailed'))
    } finally {
      setSaving(false)
    }
  }

  function toggleAllergen(key: string) {
    const zhVal = t(`menu.allergens.${key}`)
    setAllergens(prev => prev.includes(zhVal) ? prev.filter(x => x !== zhVal) : [...prev, zhVal])
  }

  return (
    <Modal title={item ? t('menu.editItemTitle') : t('menu.addItemTitle')} onClose={onClose}>
      <div className="space-y-4">
        {/* Multi-locale names */}
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-2">{t('menu.fieldNameZh').replace('中文', '')} 名称 *</label>
          <NamesEditor value={names} onChange={setNames} required />
        </div>

        {/* Image */}
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">菜品图片</label>
          <div className="flex gap-3 items-start">
            <div className="flex-shrink-0">
              {imageUrl
                ? <img src={imageUrl} alt="" className="w-20 h-20 rounded-xl object-cover border border-gray-100" onError={() => setImageUrl('')} />
                : <ImgPlaceholder className="w-20 h-20 rounded-xl" />
              }
            </div>
            <div className="flex-1 space-y-1.5">
              <input
                className="input text-sm"
                placeholder="粘贴图片 URL…"
                value={imageUrl}
                onChange={e => setImageUrl(e.target.value)}
              />
              <label className="block">
                <span className="btn-secondary text-xs cursor-pointer inline-block">📁 从本地上传</span>
                <input
                  type="file"
                  accept="image/*"
                  className="hidden"
                  onChange={e => {
                    const file = e.target.files?.[0]
                    if (!file) return
                    const reader = new FileReader()
                    reader.onload = ev => setImageUrl(ev.target?.result as string)
                    reader.readAsDataURL(file)
                  }}
                />
              </label>
              {imageUrl && (
                <button type="button" onClick={() => setImageUrl('')} className="text-xs text-red-400 hover:text-red-600">移除图片</button>
              )}
            </div>
          </div>
        </div>

        {/* Description */}
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">{t('menu.fieldDescription')}</label>
          <input className="input mb-1.5" value={descZh} onChange={e => setDescZh(e.target.value)}
            placeholder={`${t('menu.fieldDescriptionZh')} — ${t('menu.fieldDescriptionPlaceholder')}`} />
          <input className="input" value={descEn} onChange={e => setDescEn(e.target.value)}
            placeholder={`${t('menu.fieldDescriptionEn')} — optional`} />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{t('menu.fieldPrice')} *</label>
            <input className="input font-mono" type="number" min="0" step="0.01" value={price} onChange={e => setPrice(e.target.value)} placeholder="12.00" />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">库存数量</label>
            <input className="input font-mono" type="number" min="0" step="1" value={stockCount}
              onChange={e => setStockCount(e.target.value)} placeholder="留空 = 无限制" />
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{t('menu.fieldCategory')}</label>
            <select className="input" value={categoryId} onChange={e => setCategoryId(e.target.value)}>
              {categories.length === 0
                ? CATEGORIES.map(c => <option key={c.id} value={c.id}>{t(`menu.categories.${c.key}`)}</option>)
                : categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)
              }
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{t('menu.fieldTaxRate')}</label>
            <select className="input" value={taxRateId} onChange={e => setTaxRateId(e.target.value)}>
              <option value="">{t('menu.fieldTaxRateNone')}</option>
              {taxRates.map(r => (
                <option key={r.id} value={r.id}>
                  {r.name} ({(r.ratePermille / 10).toFixed(1)}%)
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{t('menu.fieldSortOrder')}</label>
            <input className="input font-mono" type="number" min="1" max="99" value={course} onChange={e => setCourse(Number(e.target.value))} />
          </div>
        </div>

        <div>
          <label className="block text-xs font-medium text-gray-600 mb-2">{t('menu.fieldAllergens')}</label>
          <div className="flex flex-wrap gap-2">
            {ALLERGEN_KEYS.map(key => {
              const zhVal = t(`menu.allergens.${key}`)
              return (
                <button
                  key={key}
                  type="button"
                  onClick={() => toggleAllergen(key)}
                  className={`px-2.5 py-1 rounded-full text-xs border transition-colors ${
                    allergens.includes(zhVal) || allergens.includes(key)
                      ? 'bg-amber-100 border-amber-300 text-amber-800'
                      : 'bg-gray-50 border-gray-200 text-gray-500 hover:border-gray-300'
                  }`}
                >
                  {zhVal}
                </button>
              )
            })}
          </div>
        </div>

        {/* Channel availability */}
        {allChannels.length > 0 && (
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{t('menu.itemChannels')}</label>
            <p className="text-xs text-gray-400 mb-2">{t('menu.itemChannelsHint')}</p>
            <div className="flex flex-wrap gap-2">
              {allChannels.map(ch => {
                const checked = availableChannels.includes(ch.id)
                return (
                  <label key={ch.id} className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs border cursor-pointer transition-colors ${
                    checked ? 'bg-brand-50 border-brand-300 text-brand-800' : 'bg-gray-50 border-gray-200 text-gray-600 hover:border-gray-300'
                  }`}>
                    <input type="checkbox" className="rounded" checked={checked} onChange={() =>
                      setAvailableChannels(prev => checked ? prev.filter(c => c !== ch.id) : [...prev, ch.id])
                    } />
                    {ch.name}
                  </label>
                )
              })}
            </div>
            {availableChannels.length === 0 && (
              <p className="text-xs text-gray-400 mt-1">不选 = 全渠道可见</p>
            )}
          </div>
        )}

        {/* Daypart profiles */}
        {allProfiles.length > 0 && (
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{t('menu.itemSchedules')}</label>
            <p className="text-xs text-gray-400 mb-2">{t('menu.itemSchedulesHint')}</p>
            <div className="flex flex-wrap gap-2">
              {allProfiles.map(p => {
                const timing = p.startTime && p.endTime ? `${p.startTime}–${p.endTime}` : t('menu.scheduleAllDay')
                return (
                  <span key={p.id} className="px-2.5 py-1 rounded-lg text-xs border bg-gray-50 border-gray-200 text-gray-500">
                    <span className="font-medium">{p.name}</span>
                    <span className="ml-1 opacity-70">{timing}</span>
                  </span>
                )
              })}
            </div>
          </div>
        )}

        {/* Modifier Groups — per-item embedded */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <label className="block text-xs font-medium text-gray-600">{t('menu.itemModifierGroups')}</label>
            <div className="flex gap-1.5">
              {templateGroups.length > 0 && (
                <button type="button" onClick={() => setShowTemplates(v => !v)}
                  className="text-xs text-brand-600 hover:text-brand-700 underline-offset-2 hover:underline">
                  从模板导入
                </button>
              )}
              <button type="button"
                onClick={() => setModifierGroups(prev => [...prev, {
                  id: newId(), name: JSON.stringify({ 'zh-CN': '', 'en-US': '' }),
                  selectionType: 'SINGLE', required: false, minSelect: 0, maxSelect: 1, options: [],
                }])}
                className="text-xs px-2 py-0.5 rounded bg-gray-100 hover:bg-gray-200 text-gray-600">
                + 添加修改项组
              </button>
            </div>
          </div>

          {showTemplates && (
            <div className="mb-3 p-2 border border-dashed border-gray-300 rounded-lg bg-gray-50">
              <p className="text-xs text-gray-500 mb-1.5">选择模板（将复制到此菜品）：</p>
              <div className="flex flex-wrap gap-1.5">
                {templateGroups.map(tg => {
                  const alreadyAdded = modifierGroups.some(g => parseName(g.name, 'zh-CN') === parseName(tg.name, 'zh-CN'))
                  return (
                    <button key={tg.id} type="button" disabled={alreadyAdded}
                      onClick={() => {
                        setModifierGroups(prev => [...prev, { ...tg, id: newId() }])
                        setShowTemplates(false)
                      }}
                      className={`px-2.5 py-1 rounded-full text-xs border transition-colors ${
                        alreadyAdded
                          ? 'bg-gray-100 border-gray-200 text-gray-400 cursor-default'
                          : 'bg-white border-brand-300 text-brand-700 hover:bg-brand-50'
                      }`}>
                      {parseName(tg.name, 'zh-CN')}
                    </button>
                  )
                })}
              </div>
            </div>
          )}

          {modifierGroups.length === 0 && (
            <p className="text-xs text-gray-400 py-2">{t('menu.itemModifierGroupsHint')}</p>
          )}

          <div className="space-y-3">
            {modifierGroups.map((grp, gi) => {
              const grpName = (() => { try { return JSON.parse(grp.name) } catch { return { 'zh-CN': grp.name } } })()
              function updateGrp(patch: Partial<ModifierGroup>) {
                setModifierGroups(prev => prev.map((g, i) => i === gi ? { ...g, ...patch } : g))
              }
              function updateGrpName(locale: string, val: string) {
                updateGrp({ name: JSON.stringify({ ...grpName, [locale]: val }) })
              }
              function addOpt() {
                updateGrp({ options: [...grp.options, { id: newId(), name: JSON.stringify({ 'zh-CN': '', 'en-US': '' }), priceAdjustMinorUnit: 0, isDefault: false, sortOrder: grp.options.length + 1 }] })
              }
              function removeOpt(oi: number) {
                updateGrp({ options: grp.options.filter((_, i) => i !== oi) })
              }
              function updateOpt(oi: number, field: string, value: string | number | boolean) {
                const newOpts = grp.options.map((o, i) => {
                  if (i !== oi) return o
                  if (field === 'nameZh' || field === 'nameEn') {
                    const parsed = (() => { try { return JSON.parse(o.name) } catch { return {} } })()
                    const key = field === 'nameZh' ? 'zh-CN' : 'en-US'
                    return { ...o, name: JSON.stringify({ ...parsed, [key]: value }) }
                  }
                  if (field === 'isDefault') return { ...o, isDefault: value as boolean }
                  return { ...o, [field]: value }
                })
                updateGrp({ options: newOpts })
              }

              return (
                <div key={grp.id} className="border border-gray-200 rounded-lg p-3 bg-gray-50/50">
                  <div className="flex items-start gap-2 mb-2">
                    <div className="flex-1 grid grid-cols-2 gap-2">
                      <input className="input-field text-sm py-1" placeholder="组名（中文）"
                        value={grpName['zh-CN'] ?? ''} onChange={e => updateGrpName('zh-CN', e.target.value)} />
                      <input className="input-field text-sm py-1" placeholder="Group name (EN)"
                        value={grpName['en-US'] ?? ''} onChange={e => updateGrpName('en-US', e.target.value)} />
                    </div>
                    <button type="button" onClick={() => setModifierGroups(prev => prev.filter((_, i) => i !== gi))}
                      className="text-gray-400 hover:text-red-500 text-lg leading-none mt-0.5">×</button>
                  </div>

                  <div className="flex items-center gap-3 mb-2 text-xs">
                    <label className="flex items-center gap-1 cursor-pointer">
                      <input type="radio" checked={grp.selectionType === 'SINGLE'}
                        onChange={() => updateGrp({ selectionType: 'SINGLE' })} />
                      单选
                    </label>
                    <label className="flex items-center gap-1 cursor-pointer">
                      <input type="radio" checked={grp.selectionType === 'MULTIPLE'}
                        onChange={() => updateGrp({ selectionType: 'MULTIPLE' })} />
                      多选
                    </label>
                    <label className="flex items-center gap-1 cursor-pointer ml-2">
                      <input type="checkbox" checked={grp.required}
                        onChange={e => updateGrp({ required: e.target.checked })} />
                      必选
                    </label>
                  </div>

                  <div className="space-y-1 mb-2">
                    {grp.options.map((opt, oi) => {
                      const optName = (() => { try { return JSON.parse(opt.name) } catch { return { 'zh-CN': opt.name } } })()
                      return (
                        <div key={opt.id} className="flex items-center gap-1.5">
                          <input className="input-field text-xs py-0.5 flex-1" placeholder="选项名（中文）"
                            value={optName['zh-CN'] ?? ''} onChange={e => updateOpt(oi, 'nameZh', e.target.value)} />
                          <input className="input-field text-xs py-0.5 w-20" placeholder="+加价"
                            type="number" min={0} step={0.01}
                            value={opt.priceAdjustMinorUnit ? (opt.priceAdjustMinorUnit / 100).toFixed(2) : ''}
                            onChange={e => updateOpt(oi, 'priceAdjustMinorUnit', Math.round((parseFloat(e.target.value) || 0) * 100))} />
                          <label className="flex items-center gap-0.5 text-xs text-gray-500 cursor-pointer">
                            <input type="checkbox" checked={opt.isDefault} onChange={e => updateOpt(oi, 'isDefault', e.target.checked)} />
                            默认
                          </label>
                          <button type="button" onClick={() => removeOpt(oi)} className="text-gray-400 hover:text-red-500 text-base">×</button>
                        </div>
                      )
                    })}
                  </div>

                  <button type="button" onClick={addOpt}
                    className="text-xs text-brand-600 hover:text-brand-700">+ 添加选项</button>
                </div>
              )
            })}
          </div>
        </div>

        {error && <p className="text-sm text-red-600">{error}</p>}

        <div className="flex gap-3 pt-2">
          <button className="btn-secondary flex-1" onClick={onClose}>{t('menu.cancel')}</button>
          <button className="btn-primary flex-1" onClick={handleSave} disabled={saving}>
            {saving ? t('menu.saving') : t('menu.save')}
          </button>
        </div>
      </div>
    </Modal>
  )
}

// ══════════════════════════════════════════════════════════════════════════════
// Combos Tab
// ══════════════════════════════════════════════════════════════════════════════

function CombosTab() {
  const { t } = useTranslation()
  const [combos, setCombos] = useState<Combo[]>([])
  const [items, setItems] = useState<MenuItem[]>([])
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState<Combo | null>(null)
  const [creating, setCreating] = useState(false)
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid')

  const reload = useCallback(() => {
    setLoading(true)
    Promise.all([comboApi.list(), menuApi.list()])
      .then(([c, m]) => { setCombos(c); setItems(m) })
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => { reload() }, [reload])

  async function toggleActive(combo: Combo) {
    await comboApi.update(combo.id, { isActive: !combo.isActive })
    reload()
  }

  function comboComponentsLabel(combo: Combo) {
    return combo.components.map(c => {
      const it = items.find(i => i.id === c.menuItemId)
      return it ? `${parseName(it.names, 'zh-CN')} ×${c.quantity}` : c.menuItemId
    }).join('、') || '—'
  }

  return (
    <>
      <div className="flex items-center justify-between mb-4">
        <div className="flex rounded-lg border border-gray-200 overflow-hidden">
          <button onClick={() => setViewMode('grid')} className={`px-3 py-1.5 text-xs transition-colors ${viewMode === 'grid' ? 'bg-gray-800 text-white' : 'bg-white text-gray-500 hover:bg-gray-50'}`} title="网格视图">⊞</button>
          <button onClick={() => setViewMode('list')} className={`px-3 py-1.5 text-xs transition-colors ${viewMode === 'list' ? 'bg-gray-800 text-white' : 'bg-white text-gray-500 hover:bg-gray-50'}`} title="列表视图">☰</button>
        </div>
        <button className="btn-primary" onClick={() => setCreating(true)}>{t('menu.addItem')}</button>
      </div>

      {loading ? (
        <div className="card px-4 py-8 text-center text-gray-400 text-sm">{t('common.loading')}</div>
      ) : combos.length === 0 ? (
        <div className="card px-4 py-8 text-center text-gray-400 text-sm">{t('menu.noComboItems')}</div>
      ) : viewMode === 'grid' ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-3">
          {combos.map(combo => (
            <div key={combo.id}
              className={`relative rounded-xl border bg-white overflow-hidden group transition-shadow hover:shadow-md ${!combo.isActive ? 'opacity-60' : 'border-gray-100'}`}
            >
              {/* Combo image: first component's image or placeholder */}
              {(() => {
                const firstImg = combo.components.map(c => items.find(i => i.id === c.menuItemId)?.imageUrl).find(Boolean)
                return firstImg
                  ? <img src={firstImg} alt="" className="w-full aspect-square object-cover" />
                  : <ImgPlaceholder className="w-full aspect-square" />
              })()}
              {!combo.isActive && (
                <div className="absolute inset-0 bg-white/60 flex items-center justify-center">
                  <span className="text-xs font-bold text-gray-500 bg-white/90 px-2 py-0.5 rounded-full border border-gray-200">下架</span>
                </div>
              )}
              <div className="p-2">
                <div className="text-xs font-semibold text-gray-900 truncate">{parseName(combo.names, 'zh-CN')}</div>
                <div className="text-xs text-gray-400 truncate mt-0.5">{comboComponentsLabel(combo)}</div>
                <div className="mt-1 flex items-center justify-between">
                  <span className="text-sm font-bold text-brand-600">{fmtPrice(combo.comboPriceMinorUnit)}</span>
                  <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                    <button onClick={() => toggleActive(combo)}
                      className={`text-xs px-1.5 py-0.5 rounded-full border ${combo.isActive ? 'text-amber-600 border-amber-200 hover:bg-amber-50' : 'text-green-600 border-green-200 hover:bg-green-50'}`}>
                      {combo.isActive ? '下架' : '上架'}
                    </button>
                    <button onClick={() => setEditing(combo)}
                      className="text-xs px-1.5 py-0.5 rounded-full border border-gray-200 text-gray-500 hover:bg-gray-50">编辑</button>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="card overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100 bg-gray-50/50">
                <th className="px-4 py-3 text-start font-medium text-gray-500">{t('menu.comboName')}</th>
                <th className="px-4 py-3 text-start font-medium text-gray-500">{t('menu.comboComponents')}</th>
                <th className="px-4 py-3 text-end font-medium text-gray-500">{t('menu.comboPrice')}</th>
                <th className="px-4 py-3 text-center font-medium text-gray-500">{t('menu.colStatus')}</th>
                <th className="px-4 py-3 text-end font-medium text-gray-500">{t('menu.colActions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {combos.map(combo => (
                <tr key={combo.id} className="hover:bg-gray-50/50 transition-colors">
                  <td className="px-4 py-3 font-medium text-gray-900">{parseName(combo.names, 'zh-CN')}</td>
                  <td className="px-4 py-3 text-gray-500 text-xs">{comboComponentsLabel(combo)}</td>
                  <td className="px-4 py-3 text-right font-mono font-medium">{fmtPrice(combo.comboPriceMinorUnit)}</td>
                  <td className="px-4 py-3 text-center">
                    <button onClick={() => toggleActive(combo)}
                      className={`badge cursor-pointer ${combo.isActive ? 'bg-green-100 text-green-700 hover:bg-green-200' : 'bg-gray-100 text-gray-500 hover:bg-gray-200'}`}>
                      {combo.isActive ? t('menu.comboActive') : t('menu.comboInactive')}
                    </button>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button className="text-xs text-gray-500 hover:text-brand-600" onClick={() => setEditing(combo)}>{t('menu.edit')}</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {(creating || editing) && (
        <ComboFormModal
          combo={editing ?? undefined}
          menuItems={items}
          onClose={() => { setCreating(false); setEditing(null) }}
          onSaved={() => { setCreating(false); setEditing(null); reload() }}
        />
      )}
    </>
  )
}

function ComboFormModal({ combo, menuItems, onClose, onSaved }: {
  combo?: Combo; menuItems: MenuItem[]
  onClose: () => void; onSaved: () => void
}) {
  const { t } = useTranslation()
  const [names, setNames] = useState<Record<string, string>>(
    combo ? parseNamesObj(combo.names) : { 'zh-CN': '' }
  )
  const [imageUrl, setImageUrl] = useState(combo?.imageUrl ?? '')
  const [price, setPrice] = useState(combo ? String(combo.comboPriceMinorUnit / 100) : '')
  const [components, setComponents] = useState<{ menuItemId: string; quantity: number }[]>(
    combo?.components.map(c => ({ menuItemId: c.menuItemId, quantity: c.quantity })) ?? []
  )
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  function addComponent() {
    setComponents(prev => [...prev, { menuItemId: menuItems[0]?.id ?? '', quantity: 1 }])
  }

  function removeComponent(i: number) {
    setComponents(prev => prev.filter((_, idx) => idx !== i))
  }

  async function handleSave() {
    if (!names['zh-CN']?.trim()) { setError(t('menu.validationComboName')); return }
    const priceVal = Math.round(parseFloat(price) * 100)
    if (isNaN(priceVal)) { setError(t('menu.validationComboPrice')); return }
    setSaving(true)
    try {
      const cleanNames = Object.fromEntries(Object.entries(names).filter(([, v]) => v.trim()))
      const payload = {
        names: JSON.stringify(cleanNames),
        imageUrl: imageUrl.trim() || undefined,
        comboPriceMinorUnit: priceVal,
        components: components.map((c, i) => ({ id: newId(), ...c, sortOrder: i })),
      }
      if (combo) await comboApi.update(combo.id, payload)
      else await comboApi.create({ id: newId(), ...payload })
      onSaved()
    } catch { setError(t('menu.saveFailed')) } finally { setSaving(false) }
  }

  return (
    <Modal title={combo ? t('menu.editComboTitle') : t('menu.addComboTitle')} onClose={onClose}>
      <div className="space-y-4">
        {/* Multi-locale names */}
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-2">套餐名称 *</label>
          <NamesEditor value={names} onChange={setNames} required />
        </div>

        {/* Image */}
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">套餐图片</label>
          <div className="flex gap-3 items-start">
            <div className="flex-shrink-0">
              {imageUrl
                ? <img src={imageUrl} alt="" className="w-20 h-20 rounded-xl object-cover border border-gray-100" onError={() => setImageUrl('')} />
                : <ImgPlaceholder className="w-20 h-20 rounded-xl" />
              }
            </div>
            <div className="flex-1 space-y-1.5">
              <input className="input text-sm" placeholder="粘贴图片 URL…" value={imageUrl} onChange={e => setImageUrl(e.target.value)} />
              <label className="block">
                <span className="btn-secondary text-xs cursor-pointer inline-block">📁 从本地上传</span>
                <input type="file" accept="image/*" className="hidden" onChange={e => {
                  const file = e.target.files?.[0]; if (!file) return
                  const reader = new FileReader()
                  reader.onload = ev => setImageUrl(ev.target?.result as string)
                  reader.readAsDataURL(file)
                }} />
              </label>
              {imageUrl && <button type="button" onClick={() => setImageUrl('')} className="text-xs text-red-400 hover:text-red-600">移除图片</button>}
            </div>
          </div>
        </div>

        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">{t('menu.comboPrice')} *</label>
          <input className="input w-32 font-mono" type="number" min="0" step="0.01" value={price} onChange={e => setPrice(e.target.value)} />
        </div>
        <div>
          <div className="flex items-center justify-between mb-2">
            <label className="text-xs font-medium text-gray-600">{t('menu.comboComponents')}</label>
            <button type="button" className="text-xs text-brand-600 hover:text-brand-700" onClick={addComponent}>{t('menu.comboComponentsAdd')}</button>
          </div>
          <div className="space-y-2">
            {components.map((c, i) => (
              <div key={i} className="flex gap-2 items-center">
                <select className="input flex-1" value={c.menuItemId} onChange={e => setComponents(prev => prev.map((x, idx) => idx === i ? { ...x, menuItemId: e.target.value } : x))}>
                  {menuItems.map(m => <option key={m.id} value={m.id}>{parseName(m.names, 'zh-CN')}</option>)}
                </select>
                <input className="input w-16 font-mono text-center" type="number" min="1" value={c.quantity} onChange={e => setComponents(prev => prev.map((x, idx) => idx === i ? { ...x, quantity: Number(e.target.value) } : x))} />
                <button type="button" className="text-gray-400 hover:text-red-500" onClick={() => removeComponent(i)}>✕</button>
              </div>
            ))}
            {components.length === 0 && <p className="text-xs text-gray-400 py-2">{t('menu.comboNoItems')}</p>}
          </div>
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
// Shared Modal
// ══════════════════════════════════════════════════════════════════════════════

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/30 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-md max-h-[90vh] overflow-y-auto">
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
// ── Categories Tab ────────────────────────────────────────────────────────────

interface CategoryModalProps {
  item?: MenuCategory
  onClose: () => void
  onSaved: () => void
}

const DAY_LABELS_CAT = ['日', '一', '二', '三', '四', '五', '六']

function CategoryModal({ item, onClose, onSaved }: CategoryModalProps) {
  const { t } = useTranslation()
  const [name, setName] = useState(item?.name ?? '')
  const [sortOrder, setSortOrder] = useState(item?.sortOrder ?? 10)
  const [activeFrom, setActiveFrom] = useState(item?.activeFrom ?? '')
  const [activeTo, setActiveTo] = useState(item?.activeTo ?? '')
  const [daysOfWeek, setDaysOfWeek] = useState<number[]>(item?.daysOfWeek ?? [])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  function toggleDay(d: number) {
    setDaysOfWeek(prev => prev.includes(d) ? prev.filter(x => x !== d) : [...prev, d])
  }

  async function handleSave() {
    if (!name.trim()) { setError(t('menu.categoryValidationName')); return }
    setSaving(true)
    setError('')
    try {
      const payload = {
        name: name.trim(),
        sortOrder,
        activeFrom: activeFrom || undefined,
        activeTo: activeTo || undefined,
        daysOfWeek: daysOfWeek.length ? daysOfWeek : undefined,
      }
      if (item) {
        await categoryApi.update(item.id, payload)
      } else {
        await categoryApi.create(payload)
      }
      onSaved()
    } catch {
      setError(t('menu.saveFailed'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={item ? t('menu.categoryEditTitle') : t('menu.categoryAddTitle')} onClose={onClose}>
      <div className="space-y-4">
        <div className="grid grid-cols-[1fr_auto] gap-3 items-end">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{t('menu.categoryFieldName')} *</label>
            <input
              className="input w-full"
              value={name}
              onChange={e => setName(e.target.value)}
              placeholder={t('menu.categoryFieldNamePlaceholder')}
              autoFocus
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{t('menu.categoryFieldSortOrder')}</label>
            <input
              className="input w-20 font-mono"
              type="number"
              min="1"
              max="999"
              value={sortOrder}
              onChange={e => setSortOrder(Number(e.target.value))}
            />
          </div>
        </div>

        {/* Time range */}
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">供应时段</label>
          <p className="text-xs text-gray-400 mb-2">{t('menu.scheduleAllDayHint')}</p>
          <div className="flex items-center gap-3">
            <input type="time" className="input w-32 font-mono text-sm" value={activeFrom} onChange={e => setActiveFrom(e.target.value)} />
            <span className="text-gray-400 text-sm">—</span>
            <input type="time" className="input w-32 font-mono text-sm" value={activeTo} onChange={e => setActiveTo(e.target.value)} />
          </div>
        </div>

        {/* Days of week */}
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">供应日</label>
          <p className="text-xs text-gray-400 mb-2">不选 = 每天</p>
          <div className="flex gap-1.5">
            {DAY_LABELS_CAT.map((label, d) => (
              <button key={d} type="button"
                onClick={() => toggleDay(d)}
                className={`w-8 h-8 rounded-full text-xs font-medium transition-colors ${
                  daysOfWeek.includes(d) ? 'bg-brand-500 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}>
                {label}
              </button>
            ))}
          </div>
        </div>

        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex gap-3 pt-2">
          <button className="btn-secondary flex-1" onClick={onClose}>{t('menu.cancel')}</button>
          <button className="btn-primary flex-1" onClick={handleSave} disabled={saving}>
            {saving ? t('menu.saving') : t('menu.save')}
          </button>
        </div>
      </div>
    </Modal>
  )
}

function CategoriesTab() {
  const { t } = useTranslation()
  const [categories, setCategories] = useState<MenuCategory[]>([])
  const [loading, setLoading] = useState(true)
  const [modal, setModal] = useState<{ mode: 'add' } | { mode: 'edit'; item: MenuCategory } | null>(null)

  function reload() {
    categoryApi.list().then(list => {
      setCategories([...list].sort((a, b) => a.sortOrder - b.sortOrder))
      setLoading(false)
    })
  }
  useEffect(() => { reload() }, [])

  async function handleDelete(id: string) {
    if (!confirm(t('menu.categoryConfirmDelete'))) return
    await categoryApi.delete(id)
    setCategories(prev => prev.filter(c => c.id !== id))
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <span className="text-xs text-gray-400">{categories.length} 个分类</span>
        <button className="btn-primary text-sm" onClick={() => setModal({ mode: 'add' })}>{t('menu.categoryAdd')}</button>
      </div>

      {loading ? (
        <p className="text-sm text-gray-400">{t('common.loading')}</p>
      ) : categories.length === 0 ? (
        <div className="text-center py-12">
          <p className="text-sm text-gray-400 mb-4">{t('menu.categoryEmpty')}</p>
          <button className="btn-primary text-sm" onClick={() => setModal({ mode: 'add' })}>{t('menu.categoryAdd')}</button>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-gray-100">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-100">
              <tr>
                <th className="text-start px-4 py-3 font-medium text-gray-500">{t('menu.categoryColName')}</th>
                <th className="text-start px-4 py-3 font-medium text-gray-500">供应时段</th>
                <th className="text-end px-4 py-3 font-medium text-gray-500 w-28">{t('menu.categoryColSortOrder')}</th>
                <th className="text-end px-4 py-3 font-medium text-gray-500 w-32">{t('menu.categoryColActions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {categories.map(c => {
                const timing = c.activeFrom && c.activeTo ? `${c.activeFrom}–${c.activeTo}` : t('menu.scheduleAllDay')
                const days = c.daysOfWeek?.length ? c.daysOfWeek.map(d => '周' + DAY_LABELS_CAT[d]).join('、') : '每天'
                return (
                <tr key={c.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-3.5 font-medium text-gray-900">{c.name}</td>
                  <td className="px-4 py-3.5 text-xs text-gray-500">{timing} · {days}</td>
                  <td className="px-4 py-3.5 text-end font-mono text-gray-500">{c.sortOrder}</td>
                  <td className="px-4 py-3.5 text-end">
                    <div className="flex justify-end gap-2">
                      <button
                        className="text-xs text-brand-600 hover:text-brand-800 font-medium px-2 py-1 rounded hover:bg-brand-50"
                        onClick={() => setModal({ mode: 'edit', item: c })}
                      >{t('menu.edit')}</button>
                      <button
                        className="text-xs text-red-500 hover:text-red-700 font-medium px-2 py-1 rounded hover:bg-red-50"
                        onClick={() => handleDelete(c.id)}
                      >{t('menu.delete')}</button>
                    </div>
                  </td>
                </tr>
              )
              })}
            </tbody>
          </table>
        </div>
      )}

      {modal !== null && (
        <CategoryModal
          item={modal.mode === 'edit' ? modal.item : undefined}
          onClose={() => setModal(null)}
          onSaved={() => { setModal(null); reload() }}
        />
      )}
    </div>
  )
}

// ── Modifier Groups Tab ───────────────────────────────────────────────────────

function parseModName(name: string): string {
  try {
    const obj = JSON.parse(name)
    return obj['zh'] ?? obj['zh-CN'] ?? obj['en'] ?? name
  } catch { return name }
}

interface ModifierGroupModalProps {
  group?: ModifierGroup
  onClose: () => void
  onSaved: () => void
}

function ModifierGroupModal({ group, onClose, onSaved }: ModifierGroupModalProps) {
  const { t } = useTranslation()
  const [nameZh, setNameZh] = useState(group ? parseModName(group.name) : '')
  const [nameEn, setNameEn] = useState(() => {
    if (!group) return ''
    try { const o = JSON.parse(group.name); return o['en'] ?? '' } catch { return '' }
  })
  const [selType, setSelType] = useState<'SINGLE' | 'MULTIPLE'>(group?.selectionType ?? 'SINGLE')
  const [required, setRequired] = useState(group?.required ?? false)
  const [minSelect, setMinSelect] = useState(group?.minSelect ?? 0)
  const [maxSelect, setMaxSelect] = useState(group?.maxSelect ?? 1)
  const [options, setOptions] = useState<Omit<ModifierOption, 'id'>[]>(
    group?.options.map(o => ({
      name: o.name, priceAdjustMinorUnit: o.priceAdjustMinorUnit, isDefault: o.isDefault, sortOrder: o.sortOrder,
    })) ?? []
  )
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  function addOption() {
    setOptions(prev => [...prev, {
      name: JSON.stringify({ zh: '', en: '' }),
      priceAdjustMinorUnit: 0,
      isDefault: false,
      sortOrder: prev.length + 1,
    }])
  }

  function removeOption(idx: number) {
    setOptions(prev => prev.filter((_, i) => i !== idx))
  }

  function updateOption(idx: number, field: string, value: string | number | boolean) {
    setOptions(prev => prev.map((o, i) => {
      if (i !== idx) return o
      if (field === 'nameZh' || field === 'nameEn') {
        const parsed = (() => { try { return JSON.parse(o.name) } catch { return {} } })()
        if (field === 'nameZh') return { ...o, name: JSON.stringify({ ...parsed, zh: value }) }
        return { ...o, name: JSON.stringify({ ...parsed, en: value }) }
      }
      return { ...o, [field]: value }
    }))
  }

  function setDefaultOption(idx: number) {
    setOptions(prev => prev.map((o, i) => ({ ...o, isDefault: i === idx })))
  }

  async function handleSave() {
    if (!nameZh.trim()) { setError(t('menu.modifierValidationName')); return }
    if (options.length === 0) { setError(t('menu.modifierValidationOptions')); return }

    setSaving(true)
    setError('')
    try {
      const payload = {
        name: JSON.stringify({ zh: nameZh.trim(), en: nameEn.trim() }),
        selectionType: selType,
        required,
        minSelect,
        maxSelect,
        options: options.map((o, i) => ({ ...o, id: `opt-${Date.now()}-${i}`, sortOrder: i + 1 })),
      }
      if (group) {
        await modifierGroupApi.update(group.id, payload)
      } else {
        await modifierGroupApi.create(payload)
      }
      onSaved()
    } catch {
      setError(t('menu.saveFailed'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={group ? t('menu.modifierEditTitle') : t('menu.modifierAddTitle')} onClose={onClose}>
      <div className="space-y-4">
        {/* Name */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{t('menu.modifierFieldNameZh')} *</label>
            <input className="input" value={nameZh} onChange={e => setNameZh(e.target.value)} placeholder="份量" autoFocus />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{t('menu.modifierFieldNameEn')}</label>
            <input className="input" value={nameEn} onChange={e => setNameEn(e.target.value)} placeholder="Size" />
          </div>
        </div>

        {/* Type + Required */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">{t('menu.modifierFieldType')}</label>
            <select className="input" value={selType} onChange={e => setSelType(e.target.value as 'SINGLE' | 'MULTIPLE')}>
              <option value="SINGLE">{t('menu.modifierTypeSingle')}</option>
              <option value="MULTIPLE">{t('menu.modifierTypeMultiple')}</option>
            </select>
          </div>
          <div className="flex items-center gap-2 pt-5">
            <input
              id="mod-required"
              type="checkbox"
              checked={required}
              onChange={e => setRequired(e.target.checked)}
              className="h-4 w-4 rounded border-gray-300 text-brand-600"
            />
            <label htmlFor="mod-required" className="text-sm text-gray-700">{t('menu.modifierFieldRequired')}</label>
          </div>
        </div>

        {/* Min/Max (only for MULTIPLE) */}
        {selType === 'MULTIPLE' && (
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">{t('menu.modifierFieldMin')}</label>
              <input className="input font-mono" type="number" min="0" value={minSelect} onChange={e => setMinSelect(Number(e.target.value))} />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">{t('menu.modifierFieldMax')}</label>
              <input className="input font-mono" type="number" min="0" value={maxSelect} onChange={e => setMaxSelect(Number(e.target.value))} />
            </div>
          </div>
        )}

        {/* Options */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <label className="text-xs font-medium text-gray-600">{t('menu.modifierOptions')}</label>
            <button type="button" className="text-xs text-brand-600 hover:text-brand-800 font-medium" onClick={addOption}>
              + {t('menu.modifierOptionAdd')}
            </button>
          </div>
          <div className="space-y-2">
            {options.map((opt, idx) => {
              const optNameZh = (() => { try { return JSON.parse(opt.name).zh ?? '' } catch { return '' } })()
              const optNameEn = (() => { try { return JSON.parse(opt.name).en ?? '' } catch { return '' } })()
              return (
                <div key={idx} className="flex items-center gap-2 p-2 bg-gray-50 rounded-lg">
                  <input
                    className="input flex-1 text-xs py-1"
                    placeholder={t('menu.modifierOptionName') + ' (中)'}
                    value={optNameZh}
                    onChange={e => updateOption(idx, 'nameZh', e.target.value)}
                  />
                  <input
                    className="input w-24 text-xs py-1"
                    placeholder="En"
                    value={optNameEn}
                    onChange={e => updateOption(idx, 'nameEn', e.target.value)}
                  />
                  <input
                    className="input w-20 font-mono text-xs py-1"
                    type="number"
                    min="0"
                    step="0.01"
                    title={t('menu.modifierOptionPrice')}
                    placeholder="+¥0"
                    value={(opt.priceAdjustMinorUnit / 100).toFixed(2)}
                    onChange={e => updateOption(idx, 'priceAdjustMinorUnit', Math.round(parseFloat(e.target.value || '0') * 100))}
                  />
                  <button
                    type="button"
                    title={t('menu.modifierOptionDefault')}
                    onClick={() => setDefaultOption(idx)}
                    className={`px-2 py-1 text-xs rounded border transition-colors ${opt.isDefault ? 'bg-brand-100 border-brand-300 text-brand-700' : 'bg-white border-gray-200 text-gray-400 hover:border-gray-300'}`}
                  >
                    {t('menu.modifierOptionDefault')}
                  </button>
                  <button type="button" onClick={() => removeOption(idx)} className="text-gray-300 hover:text-red-500 transition-colors text-sm font-bold px-1">×</button>
                </div>
              )
            })}
            {options.length === 0 && (
              <p className="text-xs text-gray-400 text-center py-3 border border-dashed border-gray-200 rounded-lg">
                {t('menu.modifierOptionAdd')} ↑
              </p>
            )}
          </div>
        </div>

        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex gap-3 pt-2">
          <button className="btn-secondary flex-1" onClick={onClose}>{t('menu.cancel')}</button>
          <button className="btn-primary flex-1" onClick={handleSave} disabled={saving}>
            {saving ? t('menu.saving') : t('menu.save')}
          </button>
        </div>
      </div>
    </Modal>
  )
}

function ModifiersTab() {
  const { t } = useTranslation()
  const [groups, setGroups] = useState<ModifierGroup[]>([])
  const [loading, setLoading] = useState(true)
  const [modal, setModal] = useState<{ mode: 'add' } | { mode: 'edit'; group: ModifierGroup } | null>(null)

  function reload() {
    modifierGroupApi.list().then(list => { setGroups(list); setLoading(false) })
  }
  useEffect(() => { reload() }, [])

  async function handleDelete(id: string) {
    if (!confirm(t('menu.modifierConfirmDelete'))) return
    await modifierGroupApi.delete(id)
    setGroups(prev => prev.filter(g => g.id !== id))
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <span className="text-xs text-gray-400">{groups.length} 个修改项组</span>
        <button className="btn-primary text-sm" onClick={() => setModal({ mode: 'add' })}>{t('menu.modifierAdd')}</button>
      </div>

      {loading ? (
        <p className="text-sm text-gray-400">{t('common.loading')}</p>
      ) : groups.length === 0 ? (
        <div className="text-center py-12">
          <p className="text-sm text-gray-400 mb-4">{t('menu.modifierEmpty')}</p>
          <button className="btn-primary text-sm" onClick={() => setModal({ mode: 'add' })}>{t('menu.modifierAdd')}</button>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-gray-100">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-100">
              <tr>
                <th className="text-start px-4 py-3 font-medium text-gray-500">{t('menu.modifierColName')}</th>
                <th className="text-start px-4 py-3 font-medium text-gray-500 w-24">{t('menu.modifierColType')}</th>
                <th className="text-start px-4 py-3 font-medium text-gray-500 w-20">{t('menu.modifierColRequired')}</th>
                <th className="text-end px-4 py-3 font-medium text-gray-500 w-20">{t('menu.modifierColOptions')}</th>
                <th className="text-end px-4 py-3 font-medium text-gray-500 w-32">{t('menu.modifierColActions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {groups.map(g => (
                <tr key={g.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-3.5">
                    <span className="font-medium text-gray-900">{parseModName(g.name)}</span>
                    <div className="mt-0.5 flex flex-wrap gap-1">
                      {g.options.map(o => (
                        <span key={o.id} className="text-xs text-gray-400">
                          {parseModName(o.name)}{o.priceAdjustMinorUnit > 0 ? ` +¥${(o.priceAdjustMinorUnit / 100).toFixed(0)}` : ''}
                        </span>
                      )).reduce((acc: React.ReactNode[], el, i) => i === 0 ? [el] : [...acc, <span key={`sep-${i}`} className="text-gray-300 text-xs">·</span>, el], [])}
                    </div>
                  </td>
                  <td className="px-4 py-3.5">
                    <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${g.selectionType === 'SINGLE' ? 'bg-blue-100 text-blue-700' : 'bg-purple-100 text-purple-700'}`}>
                      {g.selectionType === 'SINGLE' ? t('menu.modifierTypeSingle') : t('menu.modifierTypeMultiple')}
                    </span>
                  </td>
                  <td className="px-4 py-3.5">
                    <span className={`text-xs ${g.required ? 'text-red-600 font-medium' : 'text-gray-400'}`}>
                      {g.required ? '必选' : '可选'}
                    </span>
                  </td>
                  <td className="px-4 py-3.5 text-end font-mono text-gray-500">{g.options.length}</td>
                  <td className="px-4 py-3.5 text-end">
                    <div className="flex justify-end gap-2">
                      <button
                        className="text-xs text-brand-600 hover:text-brand-800 font-medium px-2 py-1 rounded hover:bg-brand-50"
                        onClick={() => setModal({ mode: 'edit', group: g })}
                      >{t('menu.edit')}</button>
                      <button
                        className="text-xs text-red-500 hover:text-red-700 font-medium px-2 py-1 rounded hover:bg-red-50"
                        onClick={() => handleDelete(g.id)}
                      >{t('menu.delete')}</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {modal !== null && (
        <ModifierGroupModal
          group={modal.mode === 'edit' ? modal.group : undefined}
          onClose={() => setModal(null)}
          onSaved={() => { setModal(null); reload() }}
        />
      )}
    </div>
  )
}

// ══════════════════════════════════════════════════════════════════════════════
// ══════════════════════════════════════════════════════════════════════════════
// Channels Tab
// ══════════════════════════════════════════════════════════════════════════════

const CHANNEL_COLOR_OPTIONS = [
  { value: 'blue',    label: '蓝色' },
  { value: 'amber',   label: '金色' },
  { value: 'purple',  label: '紫色' },
  { value: 'emerald', label: '绿色' },
  { value: 'red',     label: '红色' },
  { value: 'gray',    label: '灰色' },
]

const CHANNEL_BADGE_CLASSES: Record<string, string> = {
  blue:    'bg-blue-100 text-blue-700',
  amber:   'bg-amber-100 text-amber-800',
  purple:  'bg-purple-100 text-purple-700',
  emerald: 'bg-emerald-100 text-emerald-700',
  red:     'bg-red-100 text-red-700',
  gray:    'bg-gray-100 text-gray-600',
}

function ChannelFormModal({ channel, onClose, onSaved }: {
  channel?: Channel; onClose: () => void; onSaved: () => void
}) {
  const [id, setId] = useState(channel?.id ?? '')
  const [name, setName] = useState(channel?.name ?? '')
  const [sortOrder, setSortOrder] = useState(channel?.sortOrder ?? 10)
  const [color, setColor] = useState(channel?.color ?? 'gray')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  async function handleSave() {
    if (!name.trim()) { setError('请输入渠道名称'); return }
    if (!channel && !id.trim()) { setError('请输入渠道 ID'); return }
    setSaving(true); setError('')
    try {
      if (channel) {
        await channelApi.update(channel.id, { name: name.trim(), sortOrder, color })
      } else {
        await channelApi.create({ id: id.trim().toUpperCase(), name: name.trim(), sortOrder, color })
      }
      onSaved()
    } catch { setError('保存失败，请重试') }
    finally { setSaving(false) }
  }

  return (
    <Modal title={channel ? '编辑渠道' : '新增渠道'} onClose={onClose}>
      <div className="space-y-4">
        {!channel && (
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">渠道 ID *</label>
            <input className="input w-full font-mono uppercase" value={id}
              onChange={e => setId(e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, ''))}
              placeholder="如：WECHAT_ORDER" autoFocus />
            <p className="text-[10px] text-gray-400 mt-1">仅限大写字母、数字、下划线，创建后不可修改</p>
          </div>
        )}
        <div className="grid grid-cols-[1fr_auto] gap-3 items-end">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">显示名称 *</label>
            <input className="input w-full" value={name} onChange={e => setName(e.target.value)}
              placeholder="如：微信扫码点餐" autoFocus={!!channel} />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">排序</label>
            <input className="input w-20 font-mono" type="number" min={1} value={sortOrder}
              onChange={e => setSortOrder(Number(e.target.value))} />
          </div>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-2">颜色标签</label>
          <div className="flex gap-2 flex-wrap">
            {CHANNEL_COLOR_OPTIONS.map(c => (
              <label key={c.value} className="flex items-center gap-1 text-xs cursor-pointer">
                <input type="radio" name="ch-color" value={c.value} checked={color === c.value}
                  onChange={() => setColor(c.value)} />
                <span className={`badge ${CHANNEL_BADGE_CLASSES[c.value] ?? ''}`}>{c.label}</span>
              </label>
            ))}
          </div>
        </div>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex gap-3 pt-2">
          <button className="btn-secondary flex-1" onClick={onClose}>取消</button>
          <button className="btn-primary flex-1" onClick={handleSave} disabled={saving}>
            {saving ? '保存中…' : '保存'}
          </button>
        </div>
      </div>
    </Modal>
  )
}

function ChannelsTab() {
  const { t } = useTranslation()
  const [channels, setChannels] = useState<Channel[]>([])
  const [loading, setLoading] = useState(true)
  const [modal, setModal] = useState<{ mode: 'add' } | { mode: 'edit'; channel: Channel } | null>(null)

  function reload() {
    setLoading(true)
    channelApi.list().then(list => { setChannels(list); setLoading(false) })
  }
  useEffect(() => { reload() }, [])

  async function toggleEnabled(ch: Channel) {
    await channelApi.update(ch.id, { enabled: !ch.enabled })
    reload()
  }

  async function handleDelete(ch: Channel) {
    if (!confirm(`确认删除渠道「${ch.name}」？已设置此渠道的菜品将恢复为全渠道可见。`)) return
    await channelApi.delete(ch.id)
    reload()
  }

  return (
    <>
      <div className="flex items-center justify-between mb-4">
        <p className="text-xs text-gray-400">在菜品编辑中可以设置每道菜的可见渠道</p>
        <button className="btn-primary text-sm" onClick={() => setModal({ mode: 'add' })}>+ 新增渠道</button>
      </div>

      {loading ? (
        <p className="text-sm text-gray-400">加载中…</p>
      ) : (
        <div className="card overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100 bg-gray-50/50">
                <th className="px-4 py-3 text-start font-medium text-gray-500 w-28">渠道 ID</th>
                <th className="px-4 py-3 text-start font-medium text-gray-500">显示名称</th>
                <th className="px-4 py-3 text-center font-medium text-gray-500 w-24">状态</th>
                <th className="px-4 py-3 text-end font-medium text-gray-500 w-28">排序</th>
                <th className="px-4 py-3 text-end font-medium text-gray-500 w-32">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {channels.length === 0 && (
                <tr><td colSpan={5} className="px-4 py-8 text-center text-gray-400">暂无渠道</td></tr>
              )}
              {channels.map(ch => (
                <tr key={ch.id} className="hover:bg-gray-50/50">
                  <td className="px-4 py-3 font-mono text-xs text-gray-500">{ch.id}</td>
                  <td className="px-4 py-3">
                    <span className={`badge ${CHANNEL_BADGE_CLASSES[ch.color] ?? CHANNEL_BADGE_CLASSES.gray}`}>
                      {ch.name}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-center">
                    <button
                      onClick={() => toggleEnabled(ch)}
                      className={`badge cursor-pointer ${ch.enabled ? 'bg-emerald-100 text-emerald-700 hover:bg-emerald-200' : 'bg-gray-100 text-gray-500 hover:bg-gray-200'}`}
                    >
                      {ch.enabled ? t('common.enable') : t('common.disable')}
                    </button>
                  </td>
                  <td className="px-4 py-3 text-end font-mono text-gray-500">{ch.sortOrder}</td>
                  <td className="px-4 py-3 text-end">
                    <div className="flex justify-end gap-2">
                      <button className="text-xs text-gray-500 hover:text-brand-600"
                        onClick={() => setModal({ mode: 'edit', channel: ch })}>编辑</button>
                      <button className="text-xs text-red-400 hover:text-red-600"
                        onClick={() => handleDelete(ch)}>删除</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {modal !== null && (
        <ChannelFormModal
          channel={modal.mode === 'edit' ? modal.channel : undefined}
          onClose={() => setModal(null)}
          onSaved={() => { setModal(null); reload() }}
        />
      )}
    </>
  )
}

// Schedules Tab — time-based & multi-channel menu profiles
// ══════════════════════════════════════════════════════════════════════════════

const DAY_LABELS = ['日', '一', '二', '三', '四', '五', '六']

interface MenuProfileModalProps {
  profile?: MenuProfile
  itemCount?: number
  onClose: () => void
  onSaved: () => void
}

function MenuProfileModal({ profile, itemCount = 0, onClose, onSaved }: MenuProfileModalProps) {
  const { t } = useTranslation()
  const [name, setName] = useState(profile?.name ?? '')
  const [enabled, setEnabled] = useState(profile?.enabled ?? true)
  const [startTime, setStartTime] = useState(profile?.startTime ?? '')
  const [endTime, setEndTime] = useState(profile?.endTime ?? '')
  const [daysOfWeek, setDaysOfWeek] = useState<number[]>(profile?.daysOfWeek ?? [])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  function toggleDay(d: number) {
    setDaysOfWeek(prev => prev.includes(d) ? prev.filter(x => x !== d) : [...prev, d])
  }

  async function handleSave() {
    if (!name.trim()) { setError(t('menu.scheduleValidationName')); return }
    setSaving(true); setError('')
    try {
      const payload: Omit<MenuProfile, 'id'> = {
        name: name.trim(), enabled,
        startTime: startTime || undefined,
        endTime: endTime || undefined,
        daysOfWeek,
      }
      if (profile) {
        await menuProfileApi.update(profile.id, payload)
      } else {
        await menuProfileApi.create(payload)
      }
      onSaved()
    } catch { setError(t('menu.saveFailed')) }
    finally { setSaving(false) }
  }

  return (
    <Modal title={profile ? t('menu.scheduleEditTitle') : t('menu.scheduleAddTitle')} onClose={onClose}>
      <div className="space-y-4">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">{t('menu.scheduleFieldName')}</label>
          <input className="input-field w-full" value={name} onChange={e => setName(e.target.value)} placeholder="如：早餐、外卖专属" autoFocus />
        </div>

        <label className="flex items-center gap-2 text-sm text-gray-600 cursor-pointer">
          <input type="checkbox" className="rounded" checked={enabled} onChange={e => setEnabled(e.target.checked)} />
          {t('menu.scheduleFieldEnabled')}
        </label>

        {/* Time range */}
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1.5">{t('menu.scheduleFieldStartTime')} — {t('menu.scheduleFieldEndTime')}</label>
          <p className="text-xs text-gray-400 mb-2">留空 = {t('menu.scheduleAllDay')}</p>
          <div className="flex items-center gap-3">
            <input type="time" className="input-field w-36 font-mono text-sm" value={startTime} onChange={e => setStartTime(e.target.value)} />
            <span className="text-gray-400">—</span>
            <input type="time" className="input-field w-36 font-mono text-sm" value={endTime} onChange={e => setEndTime(e.target.value)} />
          </div>
        </div>

        {/* Days of week */}
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1.5">{t('menu.scheduleFieldDays')}</label>
          <p className="text-xs text-gray-400 mb-2">不选 = 每天</p>
          <div className="flex gap-1.5">
            {DAY_LABELS.map((label, d) => (
              <button key={d} type="button"
                onClick={() => toggleDay(d)}
                className={`w-8 h-8 rounded-full text-xs font-medium transition-colors ${
                  daysOfWeek.includes(d) ? 'bg-brand-500 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}>
                {label}
              </button>
            ))}
          </div>
        </div>

        {profile && itemCount > 0 && (
          <p className="text-xs text-gray-400">当前 {itemCount} 个菜品关联此时段档案</p>
        )}

        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex gap-3 pt-2">
          <button className="btn-secondary flex-1" onClick={onClose}>{t('menu.cancel')}</button>
          <button className="btn-primary flex-1" onClick={handleSave} disabled={saving}>{saving ? t('menu.saving') : t('menu.save')}</button>
        </div>
      </div>
    </Modal>
  )
}

function SchedulesTab() {
  const { t } = useTranslation()
  const [profiles, setProfiles] = useState<MenuProfile[]>([])
  const [items, setItems] = useState<MenuItem[]>([])
  const [loading, setLoading] = useState(true)
  const [modal, setModal] = useState<{ mode: 'add' } | { mode: 'edit'; profile: MenuProfile } | null>(null)
  // Profile-centric item assignment panel
  const [expandedProfileId, setExpandedProfileId] = useState<string | null>(null)
  const [assignedItemIds, setAssignedItemIds] = useState<string[]>([])
  const [assignSaving, setAssignSaving] = useState(false)

  function reload() {
    setLoading(true)
    Promise.all([menuProfileApi.list(), menuApi.list()]).then(([ps, ms]) => {
      setProfiles(ps)
      setItems(ms)
      setLoading(false)
    })
  }

  useEffect(() => { reload() }, [])

  function itemCount(profileId: string) {
    return assignedItemIds.length > 0 && expandedProfileId === profileId ? assignedItemIds.length : '—'
  }

  async function handleDelete(id: string) {
    if (!confirm(t('menu.scheduleConfirmDelete'))) return
    await menuProfileApi.delete(id)
    if (expandedProfileId === id) setExpandedProfileId(null)
    reload()
  }

  async function toggleEnabled(p: MenuProfile) {
    await menuProfileApi.update(p.id, { enabled: !p.enabled })
    reload()
  }

  async function openAssignPanel(profileId: string) {
    if (expandedProfileId === profileId) { setExpandedProfileId(null); return }
    const ids = await menuProfileApi.getItems(profileId)
    setAssignedItemIds(ids)
    setExpandedProfileId(profileId)
  }

  async function saveAssignment() {
    if (!expandedProfileId) return
    setAssignSaving(true)
    await menuProfileApi.setItems(expandedProfileId, assignedItemIds)
    setAssignSaving(false)
    reload()
  }

  function toggleItemAssign(itemId: string) {
    setAssignedItemIds(prev =>
      prev.includes(itemId) ? prev.filter(id => id !== itemId) : [...prev, itemId]
    )
  }

  function toggleCategoryAssign(catId: string, allSelected: boolean) {
    const catItemIds = items.filter(it => it.categoryId === catId).map(it => it.id)
    setAssignedItemIds(prev =>
      allSelected
        ? prev.filter(id => !catItemIds.includes(id))
        : [...new Set([...prev, ...catItemIds])]
    )
  }

  // Group items by category for the assignment panel
  const categories = [...new Set(items.map(it => it.categoryId))].filter(Boolean)
  function parseName(names: string, fallback = '未命名'): string {
    try { const obj = JSON.parse(names); return obj['zh-CN'] ?? obj['zh'] ?? obj['en-US'] ?? obj['en'] ?? fallback }
    catch { return names || fallback }
  }

  if (loading) return <div className="text-sm text-gray-400 py-8">{t('common.loading')}</div>

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <p className="text-sm text-gray-500">{t('menu.scheduleEmpty2')}</p>
        <button className="btn-primary text-sm py-1.5 px-4" onClick={() => setModal({ mode: 'add' })}>
          + {t('menu.scheduleAdd')}
        </button>
      </div>

      {profiles.length === 0 ? (
        <div className="text-center py-16 text-gray-400 text-sm">{t('menu.scheduleEmpty')}</div>
      ) : (
        <div className="space-y-3">
          {profiles.map(p => {
            const timing = p.startTime && p.endTime ? `${p.startTime} – ${p.endTime}` : t('menu.scheduleAllDay')
            const days = p.daysOfWeek && p.daysOfWeek.length ? p.daysOfWeek.map(d => '周' + DAY_LABELS[d]).join('、') : '每天'
            const cnt = itemCount(p.id)
            const isExpanded = expandedProfileId === p.id
            return (
              <div key={p.id} className="border border-gray-200 rounded-xl bg-white overflow-hidden">
                <div className="flex items-center gap-4 p-4 hover:border-gray-300 transition-colors">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="font-medium text-sm text-gray-900">{p.name}</span>
                      <span className={`text-[10px] px-1.5 py-0.5 rounded-full font-medium ${p.enabled ? 'bg-emerald-100 text-emerald-700' : 'bg-gray-100 text-gray-500'}`}>
                        {p.enabled ? t('common.enable') : t('common.disable')}
                      </span>
                    </div>
                    <div className="flex flex-wrap gap-x-4 gap-y-0.5 text-xs text-gray-500">
                      <span>🕐 {timing} · {days}</span>
                      <button
                        onClick={() => openAssignPanel(p.id)}
                        className="text-[#FF5C00] hover:underline font-medium"
                      >
                        🍽 {cnt} 道菜品 {isExpanded ? '▲' : '▼'}
                      </button>
                    </div>
                  </div>
                  <div className="flex items-center gap-2 flex-shrink-0">
                    <button onClick={() => toggleEnabled(p)}
                      className="text-xs px-2.5 py-1 rounded-lg border border-gray-200 hover:bg-gray-50 text-gray-600">
                      {p.enabled ? t('common.disable') : t('common.enable')}
                    </button>
                    <button onClick={() => setModal({ mode: 'edit', profile: p })}
                      className="text-xs px-2.5 py-1 rounded-lg border border-gray-200 hover:bg-gray-50 text-gray-600">
                      {t('menu.edit')}
                    </button>
                    <button onClick={() => handleDelete(p.id)}
                      className="text-xs px-2.5 py-1 rounded-lg border border-red-100 hover:bg-red-50 text-red-500">
                      {t('menu.delete')}
                    </button>
                  </div>
                </div>

                {/* Profile-centric item assignment panel */}
                {isExpanded && (
                  <div className="border-t border-gray-100 bg-gray-50 p-4">
                    <p className="text-xs text-gray-500 mb-3">勾选包含在此时段中的菜品（已选 <strong>{assignedItemIds.length}</strong> 道）</p>
                    <div className="space-y-3 max-h-64 overflow-y-auto pr-1">
                      {categories.map(catId => {
                        const catItems = items.filter(it => it.categoryId === catId)
                        const allSelected = catItems.every(it => assignedItemIds.includes(it.id))
                        return (
                          <div key={catId}>
                            <div className="flex items-center gap-2 mb-1.5">
                              <button
                                onClick={() => toggleCategoryAssign(catId, allSelected)}
                                className={`text-[10px] px-2 py-0.5 rounded-full border font-medium transition-colors ${allSelected ? 'bg-[#FF5C00] border-[#FF5C00] text-white' : 'border-gray-300 text-gray-500 hover:border-gray-400'}`}
                              >
                                {allSelected ? '全不选' : '全选'}
                              </button>
                              <span className="text-xs font-medium text-gray-700">{catId}</span>
                            </div>
                            <div className="flex flex-wrap gap-2">
                              {catItems.map(it => {
                                const selected = assignedItemIds.includes(it.id)
                                return (
                                  <button
                                    key={it.id}
                                    onClick={() => toggleItemAssign(it.id)}
                                    className={`text-xs px-2.5 py-1 rounded-lg border transition-colors ${selected ? 'bg-[#FF5C00] border-[#FF5C00] text-white' : 'bg-white border-gray-200 text-gray-600 hover:border-gray-300'}`}
                                  >
                                    {parseName(it.names)}
                                  </button>
                                )
                              })}
                            </div>
                          </div>
                        )
                      })}
                    </div>
                    <div className="flex justify-end gap-2 mt-3 pt-3 border-t border-gray-200">
                      <button onClick={() => setExpandedProfileId(null)}
                        className="text-xs px-3 py-1.5 rounded-lg border border-gray-200 text-gray-600 hover:bg-gray-100">
                        取消
                      </button>
                      <button onClick={saveAssignment} disabled={assignSaving}
                        className="text-xs px-4 py-1.5 rounded-lg bg-[#FF5C00] text-white hover:bg-orange-600 disabled:opacity-50">
                        {assignSaving ? '保存中…' : '保存分配'}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {modal !== null && (
        <MenuProfileModal
          profile={modal.mode === 'edit' ? modal.profile : undefined}
          itemCount={0}
          onClose={() => setModal(null)}
          onSaved={() => { setModal(null); reload() }}
        />
      )}
    </div>
  )
}

