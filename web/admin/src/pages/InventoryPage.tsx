import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  ingredientApi, stockMovementApi, purchaseOrderApi, bomApi, inventoryReportApi, supplierApi, stocktakeApi, outboundApi,
  type Ingredient, type StockMovement, type MovementType,
  type PurchaseOrder, type PurchaseOrderItem, type InventoryReport, type Supplier,
  type StocktakeOrder, type OutboundOrder, type OutboundType,
} from '../api/admin'
import { apiClient } from '../api/client'
import { fmtMoney } from '../api/reports'
const CATEGORIES = ['肉类', '水产', '蔬菜', '粮食', '调料', '饮料', '耗材', '其他']
const UNITS = ['kg', 'g', 'L', 'mL', '个', '罐', '盒', '包', '套', '份', '条', '块']

// ── Helpers ───────────────────────────────────────────────────────────────────

function fmtDateShort(ms: number) {
  return new Date(ms).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
function fmtDateFull(ms: number) {
  return new Date(ms).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

function poTotal(po: PurchaseOrder) {
  return po.items.reduce((s, i) => s + i.qty * i.unitCost, 0)
}

function stockColor(current: number, safety: number) {
  if (current <= 0) return 'text-red-600 font-semibold'
  if (current < safety) return 'text-orange-500 font-semibold'
  return 'text-gray-800'
}
function stockBadge(current: number, safety: number) {
  if (current <= 0) return <span className="ms-1.5 inline-flex px-1.5 py-0.5 rounded text-[10px] font-medium bg-red-100 text-red-600">缺货</span>
  if (current < safety) return <span className="ms-1.5 inline-flex px-1.5 py-0.5 rounded text-[10px] font-medium bg-orange-100 text-orange-600">预警</span>
  return null
}

const TYPE_LABELS: Record<MovementType, string> = { IN: '入库', OUT: '出库', ADJUST: '调整' }
const TYPE_COLORS: Record<MovementType, string> = {
  IN: 'bg-green-100 text-green-700',
  OUT: 'bg-gray-100 text-gray-600',
  ADJUST: 'bg-blue-100 text-blue-700',
}

// ── Ingredient form modal ─────────────────────────────────────────────────────

interface IngFormState { name: string; category: string; unit: string; purchaseUnit: string; purchaseUnitFactor: string; safetyStock: string }
const EMPTY_ING: IngFormState = { name: '', category: '其他', unit: 'kg', purchaseUnit: '', purchaseUnitFactor: '', safetyStock: '0' }

function IngredientModal({ initial, onSave, onClose }: {
  initial?: Ingredient
  onSave: (d: Omit<Ingredient, 'id' | 'createdAt' | 'currentStock'>) => Promise<void>
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [f, setF] = useState<IngFormState>(
    initial
      ? { name: initial.name, category: initial.category, unit: initial.unit,
          purchaseUnit: initial.purchaseUnit ?? '', purchaseUnitFactor: initial.purchaseUnitFactor != null ? String(initial.purchaseUnitFactor) : '',
          safetyStock: String(initial.safetyStock) }
      : EMPTY_ING
  )
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const set = (k: keyof IngFormState, v: string) => { setF(p => ({ ...p, [k]: v })); setError('') }

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!f.name.trim()) { setError(t('inventory.validationName')); return }
    const ss = Number(f.safetyStock)
    if (isNaN(ss) || ss < 0) { setError(t('inventory.validationSafety')); return }
    const hasPurchaseUnit = f.purchaseUnit.trim() !== ''
    const factor = hasPurchaseUnit && f.purchaseUnitFactor ? Number(f.purchaseUnitFactor) : undefined
    if (hasPurchaseUnit && (factor == null || isNaN(factor) || factor <= 0)) {
      setError(t('inventory.fieldPurchaseUnitFactorHint')); return
    }
    setSaving(true)
    try {
      await onSave({
        name: f.name.trim(), category: f.category, unit: f.unit, safetyStock: ss,
        purchaseUnit: hasPurchaseUnit ? f.purchaseUnit.trim() : undefined,
        purchaseUnitFactor: hasPurchaseUnit ? factor : undefined,
      })
      onClose()
    }
    catch { setError(t('inventory.saveFailed')) }
    finally { setSaving(false) }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md mx-4">
        <div className="px-6 py-5 border-b border-gray-100">
          <h2 className="text-base font-semibold text-gray-900">{initial ? t('inventory.editTitle') : t('inventory.addTitle')}</h2>
        </div>
        <form onSubmit={submit} className="px-6 py-5 space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">{t('inventory.fieldName')}</label>
            <input className="input" value={f.name} onChange={e => set('name', e.target.value)} placeholder={t('inventory.fieldNamePlaceholder')} autoFocus />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">{t('inventory.fieldCategory')}</label>
            <select className="input" value={f.category} onChange={e => set('category', e.target.value)}>
              {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">{t('inventory.fieldUnit')}</label>
            <div className="flex gap-2">
              <select className="input flex-1" value={UNITS.includes(f.unit) ? f.unit : '__custom__'}
                onChange={e => { if (e.target.value !== '__custom__') set('unit', e.target.value) }}>
                {UNITS.map(u => <option key={u} value={u}>{u}</option>)}
                <option value="__custom__">{t('inventory.fieldUnitCustom')}</option>
              </select>
              {!UNITS.includes(f.unit) && (
                <input className="input w-28" value={f.unit} onChange={e => set('unit', e.target.value)} placeholder={t('inventory.fieldUnitCustom')} />
              )}
            </div>
          </div>
          {/* Purchase unit (optional) */}
          <div className="rounded-xl border border-gray-100 bg-gray-50 p-3 space-y-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">{t('inventory.fieldPurchaseUnit')}</label>
              <input className="input text-sm" value={f.purchaseUnit} onChange={e => set('purchaseUnit', e.target.value)}
                placeholder={t('inventory.fieldPurchaseUnitPlaceholder')} />
            </div>
            {f.purchaseUnit.trim() && (
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">{t('inventory.fieldPurchaseUnitFactor')}</label>
                <div className="flex items-center gap-2">
                  <span className="text-xs text-gray-500">1 {f.purchaseUnit} =</span>
                  <input className="input w-24 font-mono text-sm" type="number" min={0.001} step="any"
                    value={f.purchaseUnitFactor} onChange={e => set('purchaseUnitFactor', e.target.value)} placeholder="1" />
                  <span className="text-xs text-gray-500">{f.unit}</span>
                </div>
                <p className="text-xs text-gray-400 mt-1">{t('inventory.fieldPurchaseUnitFactorHint')}</p>
              </div>
            )}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">{t('inventory.fieldSafetyStock')}</label>
            <div className="flex items-center gap-2">
              <input className="input w-32 font-mono" type="number" min={0} step="0.1" value={f.safetyStock} onChange={e => set('safetyStock', e.target.value)} />
              <span className="text-sm text-gray-400">{f.unit}</span>
            </div>
            <p className="text-xs text-gray-400 mt-1">{t('inventory.safetyStockHint')}</p>
          </div>
          {error && <p className="text-sm text-red-500">{error}</p>}
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" className="btn-secondary" onClick={onClose}>{t('inventory.cancel')}</button>
            <button type="submit" className="btn-primary" disabled={saving}>{saving ? t('inventory.saving') : t('inventory.save')}</button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ── Adjust stock modal ────────────────────────────────────────────────────────

function AdjustModal({ ingredient, onSave, onClose }: {
  ingredient: Ingredient
  onSave: (type: MovementType, qty: number, note: string) => Promise<void>
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [type, setType] = useState<MovementType>('IN')
  const [qty, setQty] = useState('')
  const [note, setNote] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    const q = Number(qty)
    if (isNaN(q) || q < 0) { setError(t('inventory.adjustValidationQty')); return }
    if (!note.trim()) { setError(t('inventory.adjustValidationNote')); return }
    setSaving(true)
    try { await onSave(type, q, note.trim()); onClose() }
    catch { setError(t('inventory.saveFailed')) }
    finally { setSaving(false) }
  }

  const preview = () => {
    const q = Number(qty) || 0
    if (type === 'IN') return ingredient.currentStock + q
    if (type === 'OUT') return ingredient.currentStock - q
    return q
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-sm mx-4">
        <div className="px-6 py-5 border-b border-gray-100">
          <h2 className="text-base font-semibold text-gray-900">{t('inventory.adjustTitle')}</h2>
          <p className="text-sm text-gray-400 mt-0.5">{ingredient.name}</p>
        </div>
        <form onSubmit={submit} className="px-6 py-5 space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">{t('inventory.adjustType')}</label>
            <div className="flex gap-2">
              {(['IN', 'OUT', 'ADJUST'] as MovementType[]).map(tp => (
                <button key={tp} type="button"
                  className={`flex-1 py-2 rounded-lg border text-sm transition-colors ${type === tp ? 'border-brand-400 bg-brand-50 text-brand-700 font-medium' : 'border-gray-200 text-gray-500 hover:border-gray-300'}`}
                  onClick={() => setType(tp)}>
                  {t(`inventory.type${tp}`)}
                </button>
              ))}
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              {type === 'ADJUST' ? t('inventory.adjustQtyAbsolute') : t('inventory.adjustQty')}
            </label>
            <div className="flex items-center gap-2">
              <input className="input w-36 font-mono" type="number" min={0} step="0.1"
                value={qty} onChange={e => { setQty(e.target.value); setError('') }} autoFocus />
              <span className="text-sm text-gray-400">{ingredient.unit}</span>
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">{t('inventory.adjustNote')}</label>
            <input className="input" value={note} onChange={e => { setNote(e.target.value); setError('') }}
              placeholder={t('inventory.adjustNotePlaceholder')} />
          </div>
          {qty !== '' && (
            <div className="bg-gray-50 rounded-xl px-4 py-3 flex items-center justify-between text-sm">
              <span className="text-gray-500">{t('inventory.adjustPreview')}</span>
              <span className={`font-semibold font-mono ${preview() < ingredient.safetyStock ? 'text-orange-500' : 'text-gray-800'}`}>
                {ingredient.currentStock} → {Math.round(preview() * 100) / 100} {ingredient.unit}
              </span>
            </div>
          )}
          {error && <p className="text-sm text-red-500">{error}</p>}
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" className="btn-secondary" onClick={onClose}>{t('inventory.cancel')}</button>
            <button type="submit" className="btn-primary" disabled={saving}>{saving ? t('inventory.saving') : t('inventory.adjustConfirm')}</button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ── Stock ledger drawer ───────────────────────────────────────────────────────

function LedgerDrawer({ ingredient, onAdjusted, onEditIngredient, onClose }: {
  ingredient: Ingredient
  onAdjusted: (updated: Ingredient) => void
  onEditIngredient: () => void
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [movements, setMovements] = useState<StockMovement[]>([])
  const [loadingMov, setLoadingMov] = useState(true)
  const [showAdjust, setShowAdjust] = useState(false)
  const [ing, setIng] = useState(ingredient)

  useEffect(() => {
    setLoadingMov(true)
    stockMovementApi.list(ingredient.id).then(setMovements).finally(() => setLoadingMov(false))
  }, [ingredient.id])

  async function handleAdjust(type: MovementType, qty: number, note: string) {
    await stockMovementApi.create({ ingredientId: ing.id, type, qty, note })
    const [newMov, ingList] = await Promise.all([stockMovementApi.list(ing.id), ingredientApi.list()])
    setMovements(newMov)
    const updated = ingList.find(i => i.id === ing.id)
    if (updated) { setIng(updated); onAdjusted(updated) }
  }

  const below = ing.currentStock < ing.safetyStock

  return (
    <>
      <div className="fixed inset-0 z-30 bg-black/20" onClick={onClose} />
      <div className="fixed inset-y-0 end-0 z-40 w-96 bg-white shadow-2xl flex flex-col">
        <div className="px-5 py-4 border-b border-gray-100 flex items-start gap-3">
          <div className="flex-1 min-w-0">
            <h2 className="text-base font-semibold text-gray-900 truncate">{ing.name}</h2>
            <span className="text-xs text-gray-400">{ing.category} · {ing.unit}</span>
          </div>
          <button className="text-xs text-brand-600 hover:text-brand-800 font-medium px-2 py-1 rounded hover:bg-brand-50" onClick={onEditIngredient}>{t('inventory.edit')}</button>
          <button className="p-1.5 rounded-lg hover:bg-gray-100 text-gray-400" onClick={onClose}>
            <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" /></svg>
          </button>
        </div>
        <div className={`mx-5 mt-4 rounded-xl px-4 py-4 ${below ? 'bg-orange-50 border border-orange-200' : 'bg-gray-50'}`}>
          <p className="text-xs font-medium text-gray-500 mb-1">{t('inventory.currentStock')}</p>
          <div className="flex items-end gap-2">
            <span className={`text-3xl font-bold font-mono leading-none ${below ? 'text-orange-500' : 'text-gray-900'}`}>{ing.currentStock}</span>
            <span className="text-sm text-gray-400 mb-0.5">{ing.unit}</span>
            {stockBadge(ing.currentStock, ing.safetyStock)}
          </div>
          <p className="text-xs text-gray-400 mt-1.5">{t('inventory.safetyStockLabel')}：{ing.safetyStock} {ing.unit}</p>
        </div>
        <div className="px-5 mt-3">
          <button className="btn-primary w-full text-sm" onClick={() => setShowAdjust(true)}>+ {t('inventory.adjustStock')}</button>
        </div>
        <div className="flex-1 overflow-y-auto mt-4">
          <div className="px-5 pb-2 flex items-center justify-between">
            <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wide">{t('inventory.ledgerTitle')}</h3>
            <span className="text-xs text-gray-400">{movements.length} {t('inventory.ledgerRecords')}</span>
          </div>
          {loadingMov ? (
            <div className="px-5 py-4 text-sm text-gray-400">{t('common.loading')}</div>
          ) : movements.length === 0 ? (
            <div className="px-5 py-8 text-center text-sm text-gray-400">{t('inventory.noMovements')}</div>
          ) : (
            <div className="divide-y divide-gray-50">
              {movements.map(mv => (
                <div key={mv.id} className="px-5 py-3 flex items-start gap-3">
                  <span className={`mt-0.5 inline-flex px-2 py-0.5 rounded text-[10px] font-medium flex-shrink-0 ${TYPE_COLORS[mv.type as MovementType]}`}>
                    {TYPE_LABELS[mv.type as MovementType]}
                  </span>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between gap-2">
                      <span className={`text-sm font-semibold font-mono ${mv.type === 'IN' ? 'text-green-600' : mv.type === 'OUT' ? 'text-gray-700' : 'text-blue-600'}`}>
                        {mv.type === 'IN' ? '+' : mv.type === 'OUT' ? '-' : '='}{mv.qty} {ing.unit}
                      </span>
                      <span className="text-[11px] text-gray-400 flex-shrink-0">{fmtDateShort(mv.createdAt)}</span>
                    </div>
                    <p className="text-xs text-gray-500 mt-0.5 truncate">{mv.note}</p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
      {showAdjust && <AdjustModal ingredient={ing} onSave={handleAdjust} onClose={() => setShowAdjust(false)} />}
    </>
  )
}

// ── Purchase order form drawer ────────────────────────────────────────────────

interface POLineState { ingredientId: string; qty: string; unitCost: string }

function POFormDrawer({ ingredients, suppliers, onSave, onClose }: {
  ingredients: Ingredient[]
  suppliers: Supplier[]
  onSave: (supplier: string, supplierId: string | undefined, note: string, items: Omit<PurchaseOrderItem, 'id' | 'orderId'>[], confirm: boolean) => Promise<void>
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [supplierId, setSupplierId] = useState('')
  const [supplierFreeText, setSupplierFreeText] = useState('')
  const [note, setNote] = useState('')
  const supplier = supplierId ? (suppliers.find(s => s.id === supplierId)?.name ?? '') : supplierFreeText
  const [lines, setLines] = useState<POLineState[]>([{ ingredientId: '', qty: '', unitCost: '' }])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  function setLine(idx: number, patch: Partial<POLineState>) {
    setLines(prev => prev.map((l, i) => i === idx ? { ...l, ...patch } : l))
    setError('')
  }
  function addLine() { setLines(prev => [...prev, { ingredientId: '', qty: '', unitCost: '' }]) }
  function removeLine(idx: number) { setLines(prev => prev.filter((_, i) => i !== idx)) }

  async function handleSave(confirm: boolean) {
    if (lines.length === 0) { setError(t('inventory.poValidationItems')); return }
    for (const l of lines) {
      if (!l.ingredientId) { setError(t('inventory.poValidationIngredient')); return }
      if (!l.qty || Number(l.qty) <= 0) { setError(t('inventory.poValidationQty')); return }
    }
    const items = lines.map(l => ({
      ingredientId: l.ingredientId,
      qty: Number(l.qty),
      unitCost: Math.round(Number(l.unitCost || 0) * 100),
    }))
    setSaving(true)
    try { await onSave(supplier, supplierId || undefined, note, items, confirm); onClose() }
    catch { setError(t('inventory.saveFailed')) }
    finally { setSaving(false) }
  }

  const total = lines.reduce((s, l) => s + (Number(l.qty) || 0) * (Number(l.unitCost) || 0), 0)

  return (
    <>
      <div className="fixed inset-0 z-30 bg-black/20" onClick={onClose} />
      <div className="fixed inset-y-0 end-0 z-40 w-[540px] bg-white shadow-2xl flex flex-col">
        {/* Header */}
        <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between flex-shrink-0">
          <h2 className="text-base font-semibold text-gray-900">{t('inventory.poFormTitle')}</h2>
          <button className="p-1.5 rounded-lg hover:bg-gray-100 text-gray-400" onClick={onClose}>
            <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" /></svg>
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto px-6 py-5 space-y-5">
          {/* Supplier + note */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">{t('inventory.poFieldSupplier')}</label>
              {suppliers.length > 0 ? (
                <select className="input text-sm" value={supplierId} onChange={e => setSupplierId(e.target.value)}>
                  <option value="">{t('inventory.poFieldSupplierPlaceholder')}</option>
                  {suppliers.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
              ) : (
                <input className="input text-sm" value={supplierFreeText} onChange={e => setSupplierFreeText(e.target.value)} placeholder={t('inventory.poFieldSupplierPlaceholder')} />
              )}
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">{t('inventory.poFieldNote')}</label>
              <input className="input text-sm" value={note} onChange={e => setNote(e.target.value)} placeholder={t('inventory.poFieldNotePlaceholder')} />
            </div>
          </div>

          {/* Line items */}
          <div>
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-sm font-semibold text-gray-700">{t('inventory.poItemsTitle')}</h3>
              <button className="text-xs text-brand-600 hover:text-brand-800 font-medium" onClick={addLine}>{t('inventory.poAddItem')}</button>
            </div>

            {/* Header row */}
            <div className="grid grid-cols-[1fr_80px_100px_80px_28px] gap-2 px-1 mb-1">
              <span className="text-xs text-gray-400">{t('inventory.poColIngredient')}</span>
              <span className="text-xs text-gray-400">{t('inventory.poColQty')}</span>
              <span className="text-xs text-gray-400">{t('inventory.poColUnitCost')}</span>
              <span className="text-xs text-gray-400 text-end">{t('inventory.poColSubtotal')}</span>
              <span />
            </div>

            <div className="space-y-2">
              {lines.map((line, idx) => {
                const subtotal = (Number(line.qty) || 0) * (Number(line.unitCost) || 0)
                const ing = ingredients.find(i => i.id === line.ingredientId)
                const qtyUnit = ing?.purchaseUnit ?? ing?.unit ?? ''
                const factor = ing?.purchaseUnitFactor
                return (
                  <div key={idx} className="space-y-0.5">
                    <div className="grid grid-cols-[1fr_80px_100px_80px_28px] gap-2 items-center">
                      <select className="input text-sm py-1.5"
                        value={line.ingredientId} onChange={e => setLine(idx, { ingredientId: e.target.value })}>
                        <option value="">{t('inventory.poSelectIngredient')}</option>
                        {ingredients.map(i => <option key={i.id} value={i.id}>
                          {i.name}{i.purchaseUnit ? ` (${i.purchaseUnit})` : ` (${i.unit})`}
                        </option>)}
                      </select>
                      <div className="flex items-center gap-1">
                        <input className="input text-sm py-1.5 font-mono w-full" type="number" min={0} step="0.1"
                          value={line.qty} onChange={e => setLine(idx, { qty: e.target.value })} placeholder="0" />
                      </div>
                      <input className="input text-sm py-1.5 font-mono" type="number" min={0} step="0.01"
                        value={line.unitCost} onChange={e => setLine(idx, { unitCost: e.target.value })} placeholder="0.00" />
                      <span className="text-sm text-end text-gray-600 font-mono">
                        {subtotal > 0 ? `¥${subtotal.toFixed(2)}` : '—'}
                      </span>
                      <button className="text-gray-300 hover:text-red-400 transition-colors" onClick={() => removeLine(idx)}>
                        <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" /></svg>
                      </button>
                    </div>
                    {qtyUnit && (
                      <p className="text-[10px] text-gray-400 ps-0.5">
                        {t('inventory.poFieldQty')}: {line.qty || '0'} {qtyUnit}
                        {factor && line.qty ? ` → ${(Number(line.qty) * factor).toFixed(3).replace(/\.?0+$/, '')} ${ing?.unit}` : ''}
                      </p>
                    )}
                  </div>
                )
              })}
            </div>

            {lines.length === 0 && (
              <button className="mt-2 w-full border-2 border-dashed border-gray-200 rounded-xl py-4 text-sm text-gray-400 hover:border-brand-300 hover:text-brand-500 transition-colors" onClick={addLine}>
                {t('inventory.poAddItem')}
              </button>
            )}
          </div>

          {/* Total */}
          {total > 0 && (
            <div className="flex items-center justify-between py-3 border-t border-gray-100">
              <span className="text-sm font-medium text-gray-700">{t('inventory.poTotal')}</span>
              <span className="text-lg font-bold text-gray-900">¥{total.toFixed(2)}</span>
            </div>
          )}

          {error && <p className="text-sm text-red-500">{error}</p>}
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-gray-100 flex items-center gap-3 flex-shrink-0">
          <button className="btn-secondary flex-1" onClick={onClose}>{t('inventory.cancel')}</button>
          <button className="btn-secondary flex-1" disabled={saving} onClick={() => handleSave(false)}>{t('inventory.poSaveDraft')}</button>
          <button className="btn-primary flex-1" disabled={saving} onClick={() => handleSave(true)}>{t('inventory.poSaveConfirm')}</button>
        </div>
      </div>
    </>
  )
}

// ── Purchase orders tab ───────────────────────────────────────────────────────

function PurchaseOrdersTab({ ingredients, suppliers }: { ingredients: Ingredient[]; suppliers: Supplier[] }) {
  const { t } = useTranslation()
  const [orders, setOrders] = useState<PurchaseOrder[]>([])
  const [loading, setLoading] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [confirming, setConfirming] = useState<string | null>(null)

  function reload() {
    setLoading(true)
    purchaseOrderApi.list().then(setOrders).finally(() => setLoading(false))
  }
  useEffect(() => { reload() }, [])

  async function handleSave(supplier: string, supplierId: string | undefined, note: string, items: Omit<PurchaseOrderItem, 'id' | 'orderId'>[], confirm: boolean) {
    const po = await purchaseOrderApi.create({ supplier, supplierId, note, items })
    if (confirm && po?.id) await purchaseOrderApi.confirm(po.id)
    reload()
  }

  async function handleConfirm(id: string) {
    setConfirming(id)
    try { await purchaseOrderApi.confirm(id); reload() }
    finally { setConfirming(null) }
  }

  async function handleDelete(id: string) {
    if (!confirm(t('inventory.poDeleteConfirm'))) return
    await purchaseOrderApi.delete(id)
    setOrders(prev => prev.filter(o => o.id !== id))
  }

  const ingName = (id: string) => ingredients.find(i => i.id === id)?.name ?? id
  const ingUnit = (id: string) => ingredients.find(i => i.id === id)?.unit ?? ''

  return (
    <div className="flex flex-col h-full">
      {/* Sub-header */}
      <div className="flex items-center justify-between px-6 py-3 border-b border-gray-50 bg-white flex-shrink-0">
        <span className="text-xs text-gray-400">{orders.length} 张入库单</span>
        <button className="btn-primary text-sm" onClick={() => setShowForm(true)}>+ {t('inventory.poAddBtn')}</button>
      </div>

      {/* List */}
      <div className="flex-1 overflow-auto">
        {loading ? (
          <div className="p-8 text-sm text-gray-400">{t('common.loading')}</div>
        ) : orders.length === 0 ? (
          <div className="p-12 text-center">
            <p className="text-sm text-gray-400 mb-4">{t('inventory.poListEmpty')}</p>
            <button className="btn-primary text-sm" onClick={() => setShowForm(true)}>+ {t('inventory.poAddBtn')}</button>
          </div>
        ) : (
          <div className="divide-y divide-gray-50">
            {orders.map(po => (
              <div key={po.id} className="px-6 py-4 hover:bg-gray-50 transition-colors">
                <div className="flex items-start gap-4">
                  {/* Status badge */}
                  <span className={`mt-0.5 inline-flex px-2 py-0.5 rounded-full text-xs font-medium flex-shrink-0 ${po.status === 'CONFIRMED' ? 'bg-green-100 text-green-700' : 'bg-amber-100 text-amber-700'}`}>
                    {po.status === 'CONFIRMED' ? t('inventory.poStatusConfirmed') : t('inventory.poStatusDraft')}
                  </span>

                  <div className="flex-1 min-w-0">
                    {/* Top row */}
                    <div className="flex items-center gap-3 mb-1">
                      <span className="text-sm font-medium text-gray-900">{po.supplier || '—'}</span>
                      <span className="text-xs text-gray-400">{fmtDateFull(po.createdAt)}</span>
                      {po.note && <span className="text-xs text-gray-400 truncate">· {po.note}</span>}
                    </div>
                    {/* Items summary */}
                    <div className="flex flex-wrap gap-x-4 gap-y-1 mt-1">
                      {po.items.map(item => (
                        <span key={item.id} className="text-xs text-gray-500">
                          {ingName(item.ingredientId)} × {item.qty} {ingUnit(item.ingredientId)}
                          {item.unitCost > 0 && <span className="text-gray-400"> @ ¥{(item.unitCost / 100).toFixed(2)}</span>}
                        </span>
                      ))}
                    </div>
                  </div>

                  {/* Right: total + actions */}
                  <div className="text-end flex-shrink-0">
                    <p className="text-sm font-semibold text-gray-900">¥{(poTotal(po) / 100).toFixed(2)}</p>
                    <p className="text-xs text-gray-400">{po.items.length} {t('inventory.poItems')}</p>
                    <div className="flex items-center gap-2 mt-2 justify-end">
                      {po.status === 'DRAFT' && (
                        <button
                          className="text-xs text-brand-600 hover:text-brand-800 font-medium px-2 py-1 rounded hover:bg-brand-50 transition-colors"
                          disabled={confirming === po.id}
                          onClick={() => handleConfirm(po.id)}
                        >
                          {confirming === po.id ? '…' : t('inventory.poConfirmAction')}
                        </button>
                      )}
                      {po.status === 'DRAFT' && (
                        <button className="text-xs text-red-400 hover:text-red-600 px-2 py-1 rounded hover:bg-red-50"
                          onClick={() => handleDelete(po.id)}>
                          {t('inventory.delete')}
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {showForm && (
        <POFormDrawer
          ingredients={ingredients}
          suppliers={suppliers}
          onSave={handleSave}
          onClose={() => setShowForm(false)}
        />
      )}
    </div>
  )
}

// ── Ingredients tab ───────────────────────────────────────────────────────────

function IngredientsTab() {
  const { t } = useTranslation()
  const [ingredients, setIngredients] = useState<Ingredient[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [filterCategory, setFilterCategory] = useState('')
  const [modal, setModal] = useState<{ mode: 'add' } | { mode: 'edit'; item: Ingredient } | null>(null)
  const [drawer, setDrawer] = useState<Ingredient | null>(null)

  function reload() {
    setLoading(true)
    ingredientApi.list().then(setIngredients).finally(() => setLoading(false))
  }
  useEffect(() => { reload() }, [])

  async function handleSave(data: Omit<Ingredient, 'id' | 'createdAt' | 'currentStock'>) {
    if (!modal) return
    if (modal.mode === 'add') await ingredientApi.create({ ...data, currentStock: 0 } as Omit<Ingredient, 'id' | 'createdAt'>)
    else await ingredientApi.update(modal.item.id, data)
    reload()
    if (modal.mode === 'edit' && drawer?.id === modal.item.id) setDrawer(prev => prev ? { ...prev, ...data } : prev)
  }

  async function handleDelete(id: string) {
    if (!confirm(t('inventory.confirmDelete'))) return
    await ingredientApi.delete(id)
    setIngredients(prev => prev.filter(i => i.id !== id))
    if (drawer?.id === id) setDrawer(null)
  }

  function handleAdjusted(updated: Ingredient) {
    setIngredients(prev => prev.map(i => i.id === updated.id ? updated : i))
    setDrawer(updated)
  }

  const filtered = ingredients.filter(i =>
    (!search || i.name.toLowerCase().includes(search.toLowerCase())) &&
    (!filterCategory || i.category === filterCategory)
  )
  const categories = Array.from(new Set(ingredients.map(i => i.category)))
  const lowCount = ingredients.filter(i => i.currentStock < i.safetyStock).length

  return (
    <div className="flex flex-col h-full">
      {/* Filters */}
      <div className="flex items-center gap-3 px-6 py-3 border-b border-gray-50 bg-white flex-shrink-0">
        <input className="input max-w-xs text-sm" placeholder={t('inventory.searchPlaceholder')}
          value={search} onChange={e => setSearch(e.target.value)} />
        <select className="input max-w-[140px] text-sm" value={filterCategory} onChange={e => setFilterCategory(e.target.value)}>
          <option value="">{t('inventory.allCategories')}</option>
          {categories.map(c => <option key={c} value={c}>{c}</option>)}
        </select>
        {(search || filterCategory) && (
          <button className="text-xs text-gray-400 hover:text-gray-600" onClick={() => { setSearch(''); setFilterCategory('') }}>{t('inventory.clearFilter')}</button>
        )}
        {lowCount > 0 && (
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-orange-100 text-orange-600">
            <svg className="w-3 h-3" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" /></svg>
            {lowCount} {t('inventory.lowStockCount')}
          </span>
        )}
        <span className="ms-auto text-xs text-gray-400">{filtered.length} / {ingredients.length}</span>
        <button className="btn-primary text-sm" onClick={() => setModal({ mode: 'add' })}>+ {t('inventory.addIngredient')}</button>
      </div>

      {/* Table */}
      <div className="flex-1 overflow-auto">
        {loading ? (
          <div className="p-8 text-sm text-gray-400">{t('common.loading')}</div>
        ) : filtered.length === 0 ? (
          <div className="p-12 text-center text-sm text-gray-400">{t('inventory.noItems')}</div>
        ) : (
          <table className="w-full text-sm">
            <thead className="sticky top-0 bg-gray-50 border-b border-gray-100">
              <tr>
                <th className="text-start px-6 py-3 font-medium text-gray-500">{t('inventory.colName')}</th>
                <th className="text-start px-4 py-3 font-medium text-gray-500">{t('inventory.colCategory')}</th>
                <th className="text-start px-4 py-3 font-medium text-gray-500">{t('inventory.colUnit')}</th>
                <th className="text-end px-4 py-3 font-medium text-gray-500">{t('inventory.colCurrentStock')}</th>
                <th className="text-end px-4 py-3 font-medium text-gray-500">{t('inventory.colSafetyStock')}</th>
                <th className="text-end px-6 py-3 font-medium text-gray-500">{t('inventory.colActions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {filtered.map(ing => (
                <tr key={ing.id}
                  className={`hover:bg-gray-50 transition-colors group cursor-pointer ${drawer?.id === ing.id ? 'bg-brand-50' : ''}`}
                  onClick={() => setDrawer(ing)}>
                  <td className="px-6 py-3.5 font-medium text-gray-900">{ing.name}</td>
                  <td className="px-4 py-3.5">
                    <span className="inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-600">{ing.category}</span>
                  </td>
                  <td className="px-4 py-3.5 text-gray-500 font-mono text-xs">
                    {ing.unit}
                    {ing.purchaseUnit && (
                      <span className="ms-1 text-gray-300">/ {ing.purchaseUnit}×{ing.purchaseUnitFactor}</span>
                    )}
                  </td>
                  <td className="px-4 py-3.5 text-end font-mono">
                    <span className={stockColor(ing.currentStock, ing.safetyStock)}>{ing.currentStock}</span>
                    {stockBadge(ing.currentStock, ing.safetyStock)}
                  </td>
                  <td className="px-4 py-3.5 text-end text-gray-400 font-mono text-xs">{ing.safetyStock}</td>
                  <td className="px-6 py-3.5 text-end" onClick={e => e.stopPropagation()}>
                    <div className="flex justify-end gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button className="text-xs text-brand-600 hover:text-brand-800 font-medium px-2 py-1 rounded hover:bg-brand-50"
                        onClick={() => setModal({ mode: 'edit', item: ing })}>{t('inventory.edit')}</button>
                      <button className="text-xs text-red-500 hover:text-red-700 font-medium px-2 py-1 rounded hover:bg-red-50"
                        onClick={() => handleDelete(ing.id)}>{t('inventory.delete')}</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {modal !== null && (
        <IngredientModal initial={modal.mode === 'edit' ? modal.item : undefined} onSave={handleSave} onClose={() => setModal(null)} />
      )}
      {drawer !== null && (
        <LedgerDrawer ingredient={drawer} onAdjusted={handleAdjusted}
          onEditIngredient={() => setModal({ mode: 'edit', item: drawer })} onClose={() => setDrawer(null)} />
      )}
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

// ── Suppliers tab ─────────────────────────────────────────────────────────────

interface SupplierFormState { name: string; contact: string; phone: string; note: string }
const EMPTY_SUPPLIER: SupplierFormState = { name: '', contact: '', phone: '', note: '' }

function SupplierModal({ initial, onSave, onClose }: {
  initial?: Supplier
  onSave: (data: Omit<Supplier, 'id' | 'createdAt'>) => Promise<void>
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [f, setF] = useState<SupplierFormState>(
    initial ? { name: initial.name, contact: initial.contact, phone: initial.phone, note: initial.note } : EMPTY_SUPPLIER
  )
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const set = (k: keyof SupplierFormState, v: string) => { setF(p => ({ ...p, [k]: v })); setError('') }

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!f.name.trim()) { setError(t('inventory.supplierValidationName')); return }
    setSaving(true)
    try { await onSave({ name: f.name.trim(), contact: f.contact.trim(), phone: f.phone.trim(), note: f.note.trim() }); onClose() }
    catch { setError(t('inventory.saveFailed')) }
    finally { setSaving(false) }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md mx-4">
        <div className="px-6 py-5 border-b border-gray-100">
          <h2 className="text-base font-semibold text-gray-900">{initial ? t('inventory.supplierEditTitle') : t('inventory.supplierAddTitle')}</h2>
        </div>
        <form onSubmit={submit} className="px-6 py-5 space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">{t('inventory.supplierFieldName')}</label>
            <input className="input" value={f.name} onChange={e => set('name', e.target.value)} placeholder={t('inventory.supplierFieldNamePlaceholder')} autoFocus />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">{t('inventory.supplierFieldContact')}</label>
              <input className="input" value={f.contact} onChange={e => set('contact', e.target.value)} placeholder={t('inventory.supplierFieldContactPlaceholder')} />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">{t('inventory.supplierFieldPhone')}</label>
              <input className="input" value={f.phone} onChange={e => set('phone', e.target.value)} placeholder={t('inventory.supplierFieldPhonePlaceholder')} />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">{t('inventory.supplierFieldNote')}</label>
            <input className="input" value={f.note} onChange={e => set('note', e.target.value)} placeholder={t('inventory.supplierFieldNotePlaceholder')} />
          </div>
          {error && <p className="text-sm text-red-500">{error}</p>}
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" className="btn-secondary" onClick={onClose}>{t('inventory.cancel')}</button>
            <button type="submit" className="btn-primary" disabled={saving}>{saving ? t('inventory.saving') : t('inventory.save')}</button>
          </div>
        </form>
      </div>
    </div>
  )
}

function SupplierHistoryDrawer({ supplier, orders, ingredients, onClose }: {
  supplier: Supplier
  orders: PurchaseOrder[]
  ingredients: Ingredient[]
  onClose: () => void
}) {
  const { t } = useTranslation()
  const ingName = (id: string) => ingredients.find(i => i.id === id)?.name ?? id
  const ingUnit = (id: string) => ingredients.find(i => i.id === id)?.unit ?? ''
  const poTotal = (po: PurchaseOrder) => po.items.reduce((s, i) => s + i.qty * i.unitCost, 0)
  const totalSpend = orders.reduce((s, po) => s + poTotal(po), 0)

  return (
    <>
      <div className="fixed inset-0 z-30 bg-black/20" onClick={onClose} />
      <div className="fixed inset-y-0 end-0 z-40 w-[420px] bg-white shadow-2xl flex flex-col">
        <div className="px-5 py-4 border-b border-gray-100 flex items-start justify-between">
          <div>
            <h2 className="text-base font-semibold text-gray-900">{supplier.name}</h2>
            <p className="text-xs text-gray-400 mt-0.5">{supplier.contact} {supplier.phone && `· ${supplier.phone}`}</p>
          </div>
          <button className="p-1.5 rounded-lg hover:bg-gray-100 text-gray-400" onClick={onClose}>
            <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" /></svg>
          </button>
        </div>
        {/* Total */}
        <div className="mx-5 mt-4 rounded-xl bg-gray-50 px-4 py-3 flex items-center justify-between">
          <div>
            <p className="text-xs text-gray-500">{t('inventory.supplierHistoryTotal')}</p>
            <p className="text-2xl font-bold text-gray-900 mt-0.5">¥{(totalSpend / 100).toFixed(2)}</p>
          </div>
          <div className="text-end">
            <p className="text-xs text-gray-500">{t('inventory.supplierColOrders')}</p>
            <p className="text-2xl font-bold text-gray-900 mt-0.5">{orders.length}</p>
          </div>
        </div>
        {/* Note */}
        {supplier.note && (
          <p className="mx-5 mt-3 text-xs text-gray-500 bg-amber-50 rounded-lg px-3 py-2">{supplier.note}</p>
        )}
        {/* PO list */}
        <div className="flex-1 overflow-y-auto mt-4">
          <p className="px-5 text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">{t('inventory.supplierHistoryTitle')}</p>
          {orders.length === 0 ? (
            <p className="px-5 py-4 text-sm text-gray-400">{t('inventory.supplierHistoryEmpty')}</p>
          ) : (
            <div className="divide-y divide-gray-50">
              {orders.map(po => (
                <div key={po.id} className="px-5 py-3">
                  <div className="flex items-center justify-between mb-1">
                    <span className={`inline-flex px-2 py-0.5 rounded-full text-[10px] font-medium ${po.status === 'CONFIRMED' ? 'bg-green-100 text-green-700' : 'bg-amber-100 text-amber-700'}`}>
                      {po.status === 'CONFIRMED' ? t('inventory.poStatusConfirmed') : t('inventory.poStatusDraft')}
                    </span>
                    <span className="text-xs text-gray-400">{fmtDateFull(po.createdAt)}</span>
                  </div>
                  <div className="flex flex-wrap gap-x-3 gap-y-0.5 mt-1">
                    {po.items.map(item => (
                      <span key={item.id} className="text-xs text-gray-600">
                        {ingName(item.ingredientId)} × {item.qty} {ingUnit(item.ingredientId)}
                      </span>
                    ))}
                  </div>
                  <p className="text-xs font-semibold text-gray-700 mt-1 text-end">¥{(poTotal(po) / 100).toFixed(2)}</p>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </>
  )
}

function SuppliersTab({ onSuppliersChange }: { onSuppliersChange: (suppliers: Supplier[]) => void }) {
  const { t } = useTranslation()
  const [suppliers, setSuppliers] = useState<Supplier[]>([])
  const [orders, setOrders] = useState<PurchaseOrder[]>([])
  const [ingredients, setIngredients] = useState<Ingredient[]>([])
  const [loading, setLoading] = useState(true)
  const [modal, setModal] = useState<{ mode: 'add' } | { mode: 'edit'; item: Supplier } | null>(null)
  const [drawer, setDrawer] = useState<Supplier | null>(null)

  function reload() {
    setLoading(true)
    Promise.all([supplierApi.list(), purchaseOrderApi.list(), ingredientApi.list()])
      .then(([sups, pos, ings]) => {
        setSuppliers(sups); setOrders(pos); setIngredients(ings)
        onSuppliersChange(sups)
      })
      .finally(() => setLoading(false))
  }
  useEffect(() => { reload() }, [])

  async function handleSave(data: Omit<Supplier, 'id' | 'createdAt'>) {
    if (!modal) return
    if (modal.mode === 'add') await supplierApi.create(data)
    else await supplierApi.update(modal.item.id, data)
    reload()
  }

  async function handleDelete(id: string) {
    if (!confirm(t('inventory.supplierConfirmDelete'))) return
    await supplierApi.delete(id)
    setSuppliers(prev => { const next = prev.filter(s => s.id !== id); onSuppliersChange(next); return next })
    if (drawer?.id === id) setDrawer(null)
  }

  function supplierOrders(supId: string) {
    return orders.filter(po => po.supplierId === supId)
  }
  function supplierTotal(supId: string) {
    return supplierOrders(supId).reduce((s, po) => s + po.items.reduce((t, i) => t + i.qty * i.unitCost, 0), 0)
  }

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between px-6 py-3 border-b border-gray-50 bg-white flex-shrink-0">
        <span className="text-xs text-gray-400">{suppliers.length} {t('inventory.supplierColName')}</span>
        <button className="btn-primary text-sm" onClick={() => setModal({ mode: 'add' })}>{t('inventory.supplierAdd')}</button>
      </div>
      <div className="flex-1 overflow-auto">
        {loading ? (
          <div className="p-8 text-sm text-gray-400">{t('common.loading')}</div>
        ) : suppliers.length === 0 ? (
          <div className="p-12 text-center">
            <p className="text-sm text-gray-400 mb-4">{t('inventory.supplierNoItems')}</p>
            <button className="btn-primary text-sm" onClick={() => setModal({ mode: 'add' })}>{t('inventory.supplierAdd')}</button>
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="sticky top-0 bg-gray-50 border-b border-gray-100">
              <tr>
                <th className="text-start px-6 py-3 font-medium text-gray-500">{t('inventory.supplierColName')}</th>
                <th className="text-start px-4 py-3 font-medium text-gray-500">{t('inventory.supplierColContact')}</th>
                <th className="text-start px-4 py-3 font-medium text-gray-500">{t('inventory.supplierColPhone')}</th>
                <th className="text-end px-4 py-3 font-medium text-gray-500">{t('inventory.supplierColOrders')}</th>
                <th className="text-end px-4 py-3 font-medium text-gray-500">{t('inventory.supplierColTotal')}</th>
                <th className="text-end px-6 py-3 font-medium text-gray-500">{t('inventory.supplierColActions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {suppliers.map(sup => {
                const pos = supplierOrders(sup.id)
                const total = supplierTotal(sup.id)
                return (
                  <tr key={sup.id}
                    className={`hover:bg-gray-50 transition-colors group cursor-pointer ${drawer?.id === sup.id ? 'bg-brand-50' : ''}`}
                    onClick={() => setDrawer(sup)}>
                    <td className="px-6 py-3.5">
                      <p className="font-medium text-gray-900">{sup.name}</p>
                      {sup.note && <p className="text-xs text-gray-400 mt-0.5 truncate max-w-[200px]">{sup.note}</p>}
                    </td>
                    <td className="px-4 py-3.5 text-gray-600">{sup.contact || '—'}</td>
                    <td className="px-4 py-3.5 text-gray-500 font-mono text-xs">{sup.phone || '—'}</td>
                    <td className="px-4 py-3.5 text-end text-gray-600">{pos.length}</td>
                    <td className="px-4 py-3.5 text-end font-mono font-semibold text-gray-900">
                      {total > 0 ? `¥${(total / 100).toFixed(2)}` : '—'}
                    </td>
                    <td className="px-6 py-3.5 text-end" onClick={e => e.stopPropagation()}>
                      <div className="flex justify-end gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                        <button className="text-xs text-brand-600 hover:text-brand-800 font-medium px-2 py-1 rounded hover:bg-brand-50"
                          onClick={() => setModal({ mode: 'edit', item: sup })}>{t('inventory.edit')}</button>
                        <button className="text-xs text-red-500 hover:text-red-700 font-medium px-2 py-1 rounded hover:bg-red-50"
                          onClick={() => handleDelete(sup.id)}>{t('inventory.delete')}</button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>
      {modal !== null && (
        <SupplierModal initial={modal.mode === 'edit' ? modal.item : undefined} onSave={handleSave} onClose={() => setModal(null)} />
      )}
      {drawer !== null && (
        <SupplierHistoryDrawer
          supplier={drawer}
          orders={supplierOrders(drawer.id)}
          ingredients={ingredients}
          onClose={() => setDrawer(null)}
        />
      )}
    </div>
  )
}

// ── Inventory reports tab ─────────────────────────────────────────────────────

type ReportPeriod = 'today' | 'week' | 'month' | 'custom'

function getPeriodRange(period: ReportPeriod, customFrom: string, customTo: string): [number, number] {
  const now = Date.now()
  const startOfDay = (d: Date) => { d.setHours(0, 0, 0, 0); return d.getTime() }
  if (period === 'today') return [startOfDay(new Date()), now]
  if (period === 'week') {
    const d = new Date(); d.setDate(d.getDate() - 6); return [startOfDay(d), now]
  }
  if (period === 'month') {
    const d = new Date(); d.setDate(1); return [startOfDay(d), now]
  }
  const from = customFrom ? new Date(customFrom).getTime() : startOfDay(new Date(Date.now() - 30 * 86400000))
  const to = customTo ? (new Date(customTo).setHours(23, 59, 59, 999)) : now
  return [from, to]
}

function ReportsTab() {
  const { t } = useTranslation()
  const [period, setPeriod] = useState<ReportPeriod>('month')
  const [customFrom, setCustomFrom] = useState('')
  const [customTo, setCustomTo] = useState('')
  const [report, setReport] = useState<InventoryReport | null>(null)
  const [loading, setLoading] = useState(false)

  function load() {
    const [from, to] = getPeriodRange(period, customFrom, customTo)
    setLoading(true)
    inventoryReportApi.summary(from, to).then(setReport).finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [period, customFrom, customTo])

  function exportMovementsCsv() {
    if (!report) return
    const header = [t('inventory.reportColIngredient'), t('inventory.reportColUnit'),
      t('inventory.reportColIn'), t('inventory.reportColOut'),
      t('inventory.reportColAdjust'), t('inventory.reportColClosing')]
    const rows = report.movements.map(m => [m.name, m.unit, m.totalIn, m.totalOut, m.totalAdjust, m.closingStock])
    const csv = [header, ...rows].map(r => r.join(',')).join('\n')
    const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url
    a.download = `inventory-report-${Date.now()}.csv`; a.click(); URL.revokeObjectURL(url)
  }

  const periods: { key: ReportPeriod; label: string }[] = [
    { key: 'today', label: t('inventory.reportPeriodToday') },
    { key: 'week',  label: t('inventory.reportPeriodWeek') },
    { key: 'month', label: t('inventory.reportPeriodMonth') },
    { key: 'custom', label: t('inventory.reportPeriodCustom') },
  ]

  const activeMovements = report?.movements.filter(m => m.totalIn > 0 || m.totalOut > 0 || m.totalAdjust > 0) ?? []
  const lowStockItems = report?.movements.filter(m => m.closingStock < m.safetyStock)
    .sort((a, b) => (a.closingStock - a.safetyStock) - (b.closingStock - b.safetyStock)) ?? []

  return (
    <div className="flex flex-col h-full overflow-y-auto">
      {/* Toolbar */}
      <div className="flex items-center gap-3 px-6 py-3 border-b border-gray-50 bg-white flex-shrink-0 flex-wrap">
        <div className="flex gap-1 bg-gray-100 rounded-lg p-0.5">
          {periods.map(p => (
            <button key={p.key}
              className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${period === p.key ? 'bg-white shadow-sm text-gray-900' : 'text-gray-500 hover:text-gray-700'}`}
              onClick={() => setPeriod(p.key)}>
              {p.label}
            </button>
          ))}
        </div>
        {period === 'custom' && (
          <div className="flex items-center gap-2">
            <input type="date" className="input text-xs py-1.5" value={customFrom} onChange={e => setCustomFrom(e.target.value)} />
            <span className="text-xs text-gray-400">—</span>
            <input type="date" className="input text-xs py-1.5" value={customTo} onChange={e => setCustomTo(e.target.value)} />
          </div>
        )}
        <button className="btn-secondary text-xs ms-auto" onClick={exportMovementsCsv} disabled={!report || loading}>
          {t('inventory.reportExportCsv')}
        </button>
      </div>

      {loading ? (
        <div className="p-8 text-sm text-gray-400">{t('common.loading')}</div>
      ) : (
        <div className="px-6 py-5 space-y-8">

          {/* Summary cards */}
          {report && (
            <div className="grid grid-cols-3 gap-4">
              <div className="stat-card">
                <p className="text-xs font-medium text-gray-500 uppercase tracking-wide">{t('inventory.reportSectionLowStock')}</p>
                <p className={`text-2xl font-semibold tabular-nums ${report.lowStockCount > 0 ? 'text-orange-500' : 'text-gray-900'}`}>
                  {report.lowStockCount}
                </p>
                <p className="text-xs text-gray-400">{t('inventory.lowStockCount')}</p>
              </div>
              <div className="stat-card">
                <p className="text-xs font-medium text-gray-500 uppercase tracking-wide">{t('inventory.reportColIn')}</p>
                <p className="text-2xl font-semibold tabular-nums text-green-600">
                  {report.movements.reduce((s, m) => s + m.totalIn, 0).toFixed(1)}
                </p>
                <p className="text-xs text-gray-400">{t('inventory.reportSectionMovements')}</p>
              </div>
              <div className="stat-card">
                <p className="text-xs font-medium text-gray-500 uppercase tracking-wide">{t('inventory.reportColTotal')}</p>
                <p className="text-2xl font-semibold tabular-nums text-gray-900">
                  {fmtMoney(report.purchaseBySupplier.reduce((s, p) => s + p.totalMinorUnit, 0))}
                </p>
                <p className="text-xs text-gray-400">{t('inventory.reportSectionPurchase')}</p>
              </div>
            </div>
          )}

          {/* Movement summary table */}
          <section>
            <h3 className="text-sm font-semibold text-gray-700 mb-3">{t('inventory.reportSectionMovements')}</h3>
            {activeMovements.length === 0 ? (
              <p className="text-sm text-gray-400">{t('inventory.reportNoMovements')}</p>
            ) : (
              <div className="card overflow-hidden">
                <table className="w-full text-sm">
                  <thead className="bg-gray-50 border-b border-gray-100">
                    <tr>
                      <th className="text-start px-4 py-2.5 font-medium text-gray-500 text-xs">{t('inventory.reportColIngredient')}</th>
                      <th className="text-start px-4 py-2.5 font-medium text-gray-500 text-xs">{t('inventory.reportColUnit')}</th>
                      <th className="text-end px-4 py-2.5 font-medium text-gray-500 text-xs">{t('inventory.reportColIn')}</th>
                      <th className="text-end px-4 py-2.5 font-medium text-gray-500 text-xs">{t('inventory.reportColOut')}</th>
                      <th className="text-end px-4 py-2.5 font-medium text-gray-500 text-xs">{t('inventory.reportColAdjust')}</th>
                      <th className="text-end px-4 py-2.5 font-medium text-gray-500 text-xs">{t('inventory.reportColClosing')}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-50">
                    {activeMovements.map(m => {
                      const belowSafety = m.closingStock < m.safetyStock
                      return (
                        <tr key={m.ingredientId} className="hover:bg-gray-50/50">
                          <td className="px-4 py-2.5 font-medium text-gray-800">{m.name}</td>
                          <td className="px-4 py-2.5 text-gray-400 font-mono text-xs">{m.unit}</td>
                          <td className="px-4 py-2.5 text-end font-mono text-green-600">
                            {m.totalIn > 0 ? `+${m.totalIn}` : '—'}
                          </td>
                          <td className="px-4 py-2.5 text-end font-mono text-gray-600">
                            {m.totalOut > 0 ? `-${m.totalOut}` : '—'}
                          </td>
                          <td className="px-4 py-2.5 text-end font-mono text-blue-500 text-xs">
                            {m.totalAdjust !== 0 ? m.totalAdjust : '—'}
                          </td>
                          <td className="px-4 py-2.5 text-end font-mono">
                            <span className={belowSafety ? 'text-orange-500 font-semibold' : 'text-gray-800'}>
                              {m.closingStock}
                            </span>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          {/* Purchase by supplier */}
          <section>
            <h3 className="text-sm font-semibold text-gray-700 mb-3">{t('inventory.reportSectionPurchase')}</h3>
            {(report?.purchaseBySupplier ?? []).length === 0 ? (
              <p className="text-sm text-gray-400">{t('inventory.reportNoPurchase')}</p>
            ) : (
              <div className="card overflow-hidden">
                <table className="w-full text-sm">
                  <thead className="bg-gray-50 border-b border-gray-100">
                    <tr>
                      <th className="text-start px-4 py-2.5 font-medium text-gray-500 text-xs">{t('inventory.reportColSupplier')}</th>
                      <th className="text-end px-4 py-2.5 font-medium text-gray-500 text-xs">{t('inventory.reportColOrders')}</th>
                      <th className="text-end px-4 py-2.5 font-medium text-gray-500 text-xs">{t('inventory.reportColTotal')}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-50">
                    {(report?.purchaseBySupplier ?? []).map(s => (
                      <tr key={s.supplier} className="hover:bg-gray-50/50">
                        <td className="px-4 py-2.5 font-medium text-gray-800">{s.supplier}</td>
                        <td className="px-4 py-2.5 text-end text-gray-600">{s.orderCount}</td>
                        <td className="px-4 py-2.5 text-end font-mono font-semibold text-gray-900">
                          {fmtMoney(s.totalMinorUnit)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          {/* Low stock detail */}
          <section>
            <h3 className="text-sm font-semibold text-gray-700 mb-3">{t('inventory.reportSectionLowStock')}</h3>
            {lowStockItems.length === 0 ? (
              <div className="flex items-center gap-2 text-sm text-green-600">
                <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor">
                  <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                </svg>
                {t('inventory.reportNoLowStock')}
              </div>
            ) : (
              <div className="card overflow-hidden">
                <table className="w-full text-sm">
                  <thead className="bg-gray-50 border-b border-gray-100">
                    <tr>
                      <th className="text-start px-4 py-2.5 font-medium text-gray-500 text-xs">{t('inventory.reportColIngredient')}</th>
                      <th className="text-end px-4 py-2.5 font-medium text-gray-500 text-xs">{t('inventory.reportColClosing')}</th>
                      <th className="text-end px-4 py-2.5 font-medium text-gray-500 text-xs">{t('inventory.colSafetyStock')}</th>
                      <th className="text-end px-4 py-2.5 font-medium text-gray-500 text-xs">{t('inventory.reportColGap')}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-50">
                    {lowStockItems.map(m => {
                      const gap = m.safetyStock - m.closingStock
                      const critical = m.closingStock <= 0
                      return (
                        <tr key={m.ingredientId}>
                          <td className="px-4 py-2.5">
                            <div className="flex items-center gap-2">
                              <span className={`w-1.5 h-1.5 rounded-full flex-shrink-0 ${critical ? 'bg-red-500' : 'bg-orange-400'}`} />
                              <span className="font-medium text-gray-800">{m.name}</span>
                            </div>
                          </td>
                          <td className="px-4 py-2.5 text-end font-mono">
                            <span className={critical ? 'text-red-600 font-semibold' : 'text-orange-500 font-semibold'}>
                              {m.closingStock} {m.unit}
                            </span>
                          </td>
                          <td className="px-4 py-2.5 text-end font-mono text-gray-400 text-xs">{m.safetyStock} {m.unit}</td>
                          <td className="px-4 py-2.5 text-end">
                            <span className={`text-xs font-medium px-1.5 py-0.5 rounded ${critical ? 'bg-red-100 text-red-600' : 'bg-orange-100 text-orange-600'}`}>
                              -{Math.round(gap * 1000) / 1000} {m.unit}
                            </span>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </section>

        </div>
      )}
    </div>
  )
}

// ── BOM tab ───────────────────────────────────────────────────────────────────

interface MenuItem { id: string; names: string; priceMinorUnit: number; categoryId: string }

function parseItemName(names: string, lang = 'zh-CN'): string {
  try { const m = JSON.parse(names); return m[lang] ?? m['zh-CN'] ?? m['en-US'] ?? names } catch { return names }
}

interface BomLineState { ingredientId: string; qty: string; lastUnitCost?: number }

function BomTab({ ingredients }: { ingredients: Ingredient[] }) {
  const { t } = useTranslation()
  const [menuItems, setMenuItems] = useState<MenuItem[]>([])
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null)
  const [lines, setLines] = useState<BomLineState[]>([])
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [loading, setLoading] = useState(false)
  const [search, setSearch] = useState('')

  useEffect(() => {
    apiClient.get<MenuItem[]>('/admin/menu').then(r => setMenuItems(r.data))
  }, [])

  async function selectItem(id: string) {
    setSelectedItemId(id)
    setLoading(true)
    setSaved(false)
    try {
      const recipe = await bomApi.get(id)
      setLines(recipe.lines.map(l => ({ ingredientId: l.ingredientId, qty: String(l.qty), lastUnitCost: l.lastUnitCost })))
    } finally {
      setLoading(false)
    }
  }

  function setLine(idx: number, patch: Partial<BomLineState>) {
    setLines(prev => prev.map((l, i) => i === idx ? { ...l, ...patch } : l))
    setSaved(false)
  }

  async function handleSave() {
    if (!selectedItemId) return
    const validLines = lines.filter(l => l.ingredientId && Number(l.qty) > 0)
    setSaving(true)
    try {
      await bomApi.save(selectedItemId, validLines.map(l => ({ ingredientId: l.ingredientId, qty: Number(l.qty) })))
      setSaved(true)
    } finally {
      setSaving(false)
    }
  }

  const selectedItem = menuItems.find(m => m.id === selectedItemId)
  const filteredItems = menuItems.filter(m => parseItemName(m.names).toLowerCase().includes(search.toLowerCase()))

  return (
    <div className="flex h-full">
      {/* Left: menu item list */}
      <div className="w-72 flex-shrink-0 border-e border-gray-100 flex flex-col bg-gray-50">
        <div className="px-4 py-3 border-b border-gray-100 bg-white">
          <input className="input text-sm w-full" placeholder="搜索菜品…"
            value={search} onChange={e => setSearch(e.target.value)} />
        </div>
        <div className="flex-1 overflow-y-auto">
          {filteredItems.length === 0 ? (
            <p className="px-4 py-6 text-sm text-gray-400 text-center">{t('common.loading')}</p>
          ) : (
            <div className="py-1">
              {filteredItems.map(item => {
                const name = parseItemName(item.names)
                return (
                  <button key={item.id}
                    className={`w-full text-start px-4 py-3 flex items-center gap-3 transition-colors hover:bg-white ${selectedItemId === item.id ? 'bg-white border-e-2 border-brand-500' : ''}`}
                    onClick={() => selectItem(item.id)}>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-gray-800 truncate">{name}</p>
                      <p className="text-xs text-gray-400">{item.categoryId}</p>
                    </div>
                    {/* dot indicator: recipe exists */}
                    <span className="w-1.5 h-1.5 rounded-full flex-shrink-0 bg-gray-200" />
                  </button>
                )
              })}
            </div>
          )}
        </div>
      </div>

      {/* Right: recipe editor */}
      <div className="flex-1 min-w-0 flex flex-col">
        {!selectedItemId ? (
          <div className="flex-1 flex items-center justify-center text-center">
            <div>
              <svg className="w-12 h-12 mx-auto mb-3 text-gray-200" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m0 12.75h7.5m-7.5 3H12M10.5 2.25H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
              </svg>
              <p className="text-sm text-gray-400">{t('inventory.bomEmpty')}</p>
            </div>
          </div>
        ) : (
          <>
            {/* Item header */}
            <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between flex-shrink-0">
              <div>
                <h2 className="text-base font-semibold text-gray-900">{selectedItem ? parseItemName(selectedItem.names) : ''}</h2>
                <p className="text-xs text-gray-400 mt-0.5">{t('inventory.bomSubtitle')}</p>
              </div>
              <button className="btn-primary text-sm min-w-[90px]" disabled={saving} onClick={handleSave}>
                {saving ? t('inventory.bomSaving') : saved ? t('inventory.bomSaved') : t('inventory.bomSave')}
              </button>
            </div>

            {/* Recipe lines */}
            <div className="flex-1 overflow-y-auto px-6 py-5">
              {loading ? (
                <p className="text-sm text-gray-400">{t('common.loading')}</p>
              ) : (
                <>
                  {/* Column headers */}
                  {lines.length > 0 && (
                    <div className="grid grid-cols-[1fr_160px_36px] gap-3 mb-2 px-1">
                      <span className="text-xs font-medium text-gray-500">{t('inventory.bomColIngredient')}</span>
                      <span className="text-xs font-medium text-gray-500">{t('inventory.bomColQty')}</span>
                      <span />
                    </div>
                  )}

                  <div className="space-y-2">
                    {lines.map((line, idx) => {
                      const ing = ingredients.find(i => i.id === line.ingredientId)
                      return (
                        <div key={idx} className="grid grid-cols-[1fr_160px_36px] gap-3 items-center">
                          <select className="input text-sm py-2"
                            value={line.ingredientId}
                            onChange={e => setLine(idx, { ingredientId: e.target.value })}>
                            <option value="">{t('inventory.poSelectIngredient')}</option>
                            {ingredients.map(i => <option key={i.id} value={i.id}>{i.name} ({i.unit})</option>)}
                          </select>
                          <div className="flex items-center gap-1.5">
                            <input className="input flex-1 text-sm py-2 font-mono" type="number" min={0} step="0.001"
                              value={line.qty} placeholder="0"
                              onChange={e => setLine(idx, { qty: e.target.value })} />
                            {ing && <span className="text-xs text-gray-400 w-8 flex-shrink-0">{ing.unit}</span>}
                          </div>
                          <button className="p-1 text-gray-300 hover:text-red-400 transition-colors"
                            onClick={() => { setLines(prev => prev.filter((_, i) => i !== idx)); setSaved(false) }}>
                            <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor">
                              <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
                            </svg>
                          </button>
                        </div>
                      )
                    })}
                  </div>

                  {lines.length === 0 ? (
                    <button className="mt-4 w-full border-2 border-dashed border-gray-200 rounded-xl py-6 text-sm text-gray-400 hover:border-brand-300 hover:text-brand-500 transition-colors"
                      onClick={() => { setLines([{ ingredientId: '', qty: '' }]); setSaved(false) }}>
                      {t('inventory.bomAddLine')}
                    </button>
                  ) : (
                    <button className="mt-3 text-sm text-brand-600 hover:text-brand-800 font-medium"
                      onClick={() => { setLines(prev => [...prev, { ingredientId: '', qty: '' }]); setSaved(false) }}>
                      {t('inventory.bomAddLine')}
                    </button>
                  )}

                  {/* Cost summary */}
                  {lines.some(l => l.ingredientId && Number(l.qty) > 0) && (() => {
                    const validLines = lines.filter(l => l.ingredientId && Number(l.qty) > 0)
                    const hasCost = validLines.some(l => l.lastUnitCost != null)
                    const totalCost = validLines.reduce((s, l) => s + (l.lastUnitCost != null ? Number(l.qty) * l.lastUnitCost : 0), 0)
                    const selectedItem = menuItems.find(m => m.id === selectedItemId)
                    const menuPrice = selectedItem?.priceMinorUnit ?? 0
                    const margin = menuPrice > 0 ? ((menuPrice - totalCost) / menuPrice * 100) : null
                    return (
                      <div className="mt-6 bg-gray-50 rounded-xl p-4">
                        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-3">{t('inventory.bomCost')}</p>
                        {!hasCost ? (
                          <p className="text-xs text-gray-400">{t('inventory.bomCostNoPO')}</p>
                        ) : (
                          <>
                            <div className="space-y-1.5">
                              {validLines.map((l, idx) => {
                                const ing = ingredients.find(i => i.id === l.ingredientId)
                                if (!ing) return null
                                const lineCost = l.lastUnitCost != null ? Number(l.qty) * l.lastUnitCost : null
                                return (
                                  <div key={idx} className="flex items-center justify-between text-sm">
                                    <span className="text-gray-600">{ing.name} × {l.qty} {ing.unit}</span>
                                    <span className="font-mono text-gray-700">
                                      {lineCost != null ? `¥${(lineCost / 100).toFixed(2)}` : <span className="text-gray-400 text-xs">{t('inventory.bomCostNoPrice')}</span>}
                                    </span>
                                  </div>
                                )
                              })}
                            </div>
                            <div className="mt-3 pt-3 border-t border-gray-200 space-y-1.5">
                              <div className="flex items-center justify-between text-sm font-semibold">
                                <span className="text-gray-700">{t('inventory.bomCostTotal')}</span>
                                <span className="font-mono text-gray-900">¥{(totalCost / 100).toFixed(2)}</span>
                              </div>
                              {menuPrice > 0 && (
                                <div className="flex items-center justify-between text-sm">
                                  <span className="text-gray-500">{t('inventory.bomCostMenuPrice')}</span>
                                  <span className="font-mono text-gray-600">¥{(menuPrice / 100).toFixed(2)}</span>
                                </div>
                              )}
                              {margin !== null && (
                                <div className="flex items-center justify-between text-sm">
                                  <span className="text-gray-500">{t('inventory.bomCostGrossMargin')}</span>
                                  <span className={`font-mono font-semibold ${margin >= 60 ? 'text-green-600' : margin >= 30 ? 'text-amber-600' : 'text-red-600'}`}>
                                    {margin.toFixed(1)}%
                                  </span>
                                </div>
                              )}
                            </div>
                          </>
                        )}
                        <p className="text-xs text-gray-400 mt-3">{t('inventory.bomCostHint')}</p>
                      </div>
                    )
                  })()}
                </>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  )
}

// ── Outbound orders tab ───────────────────────────────────────────────────────

const OUTBOUND_TYPES: OutboundType[] = ['ISSUE', 'WASTE', 'GIFT', 'OTHER']
const OUTBOUND_TYPE_KEY: Record<OutboundType, string> = {
  ISSUE: 'inventory.outboundTypeIssue',
  WASTE: 'inventory.outboundTypeWaste',
  GIFT: 'inventory.outboundTypeGift',
  OTHER: 'inventory.outboundTypeOther',
}
const OUTBOUND_TYPE_COLOR: Record<OutboundType, string> = {
  ISSUE: 'bg-blue-100 text-blue-700',
  WASTE: 'bg-red-100 text-red-600',
  GIFT: 'bg-purple-100 text-purple-700',
  OTHER: 'bg-gray-100 text-gray-600',
}

interface OutboundLineState { ingredientId: string; qty: string }

function OutboundFormDrawer({ ingredients, onSave, onClose }: {
  ingredients: Ingredient[]
  onSave: (type: OutboundType, note: string, items: { ingredientId: string; qty: number }[]) => Promise<void>
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [type, setType] = useState<OutboundType>('ISSUE')
  const [note, setNote] = useState('')
  const [lines, setLines] = useState<OutboundLineState[]>([{ ingredientId: '', qty: '' }])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  function setLine(idx: number, patch: Partial<OutboundLineState>) {
    setLines(prev => prev.map((l, i) => i === idx ? { ...l, ...patch } : l))
    setError('')
  }

  async function submit() {
    const valid = lines.filter(l => l.ingredientId && Number(l.qty) > 0)
    if (valid.length === 0) { setError(t('inventory.poValidationItems')); return }
    setSaving(true)
    try {
      await onSave(type, note, valid.map(l => ({ ingredientId: l.ingredientId, qty: Number(l.qty) })))
      onClose()
    } catch { setError(t('inventory.outboundSaveFailed')) }
    finally { setSaving(false) }
  }

  const ingUnit = (id: string) => ingredients.find(i => i.id === id)?.unit ?? ''

  return (
    <>
      <div className="fixed inset-0 z-30 bg-black/20" onClick={onClose} />
      <div className="fixed inset-y-0 end-0 z-40 w-[480px] bg-white shadow-2xl flex flex-col">
        <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between">
          <h2 className="text-base font-semibold text-gray-900">{t('inventory.outboundAddTitle')}</h2>
          <button className="p-1.5 rounded-lg hover:bg-gray-100 text-gray-400" onClick={onClose}>
            <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" /></svg>
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-5 py-5 space-y-5">
          {/* Type */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">{t('inventory.outboundFieldType')}</label>
            <div className="flex gap-2 flex-wrap">
              {OUTBOUND_TYPES.map(tp => (
                <button key={tp}
                  className={`px-4 py-1.5 rounded-full text-sm font-medium border-2 transition-colors ${type === tp ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-gray-200 text-gray-600 hover:border-gray-300'}`}
                  onClick={() => setType(tp)}>{t(OUTBOUND_TYPE_KEY[tp])}</button>
              ))}
            </div>
          </div>
          {/* Note */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">{t('inventory.outboundFieldNote')}</label>
            <input className="input" value={note} onChange={e => setNote(e.target.value)} placeholder={t('inventory.outboundFieldNotePlaceholder')} />
          </div>
          {/* Lines */}
          <div>
            <div className="grid grid-cols-[1fr_140px_28px] gap-2 mb-1.5 px-0.5">
              <span className="text-xs font-medium text-gray-500">{t('inventory.outboundColIngredient')}</span>
              <span className="text-xs font-medium text-gray-500">{t('inventory.outboundColQty')}</span>
              <span />
            </div>
            <div className="space-y-2">
              {lines.map((line, idx) => (
                <div key={idx} className="grid grid-cols-[1fr_140px_28px] gap-2 items-center">
                  <select className="input text-sm py-2" value={line.ingredientId} onChange={e => setLine(idx, { ingredientId: e.target.value })}>
                    <option value="">{t('inventory.poSelectIngredient')}</option>
                    {ingredients.map(i => <option key={i.id} value={i.id}>{i.name} ({i.unit})</option>)}
                  </select>
                  <div className="flex items-center gap-1.5">
                    <input className="input flex-1 text-sm py-2 font-mono" type="number" min={0} step="0.001"
                      value={line.qty} placeholder="0"
                      onChange={e => setLine(idx, { qty: e.target.value })} />
                    {line.ingredientId && <span className="text-xs text-gray-400 w-8 flex-shrink-0">{ingUnit(line.ingredientId)}</span>}
                  </div>
                  <button className="p-1 text-gray-300 hover:text-red-400 transition-colors"
                    onClick={() => setLines(prev => prev.filter((_, i) => i !== idx))}>
                    <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" /></svg>
                  </button>
                </div>
              ))}
            </div>
            <button className="mt-3 text-sm text-brand-600 hover:text-brand-800 font-medium"
              onClick={() => setLines(prev => [...prev, { ingredientId: '', qty: '' }])}>
              + {t('inventory.bomAddLine')}
            </button>
          </div>
          {error && <p className="text-sm text-red-500">{error}</p>}
        </div>
        <div className="px-5 py-4 border-t border-gray-100 flex justify-end gap-3">
          <button className="btn-secondary" onClick={onClose}>{t('inventory.cancel')}</button>
          <button className="btn-primary min-w-[120px]" disabled={saving} onClick={submit}>
            {saving ? t('inventory.saving') : t('inventory.save')}
          </button>
        </div>
      </div>
    </>
  )
}

function OutboundOrdersTab({ ingredients }: { ingredients: Ingredient[] }) {
  const { t } = useTranslation()
  const [orders, setOrders] = useState<OutboundOrder[]>([])
  const [loading, setLoading] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [confirming, setConfirming] = useState<string | null>(null)

  function reload() {
    outboundApi.list().then(list => { setOrders(list); setLoading(false) })
  }
  useEffect(() => { reload() }, [])

  async function handleSave(type: OutboundType, note: string, items: { ingredientId: string; qty: number }[]) {
    await outboundApi.create({ type, note, items })
    reload()
  }

  async function handleConfirm(id: string) {
    if (!confirm(t('inventory.outboundConfirmConfirm'))) return
    setConfirming(id)
    try {
      await outboundApi.confirm(id)
      reload()
      alert(t('inventory.outboundConfirmSuccess'))
    } finally { setConfirming(null) }
  }

  async function handleDelete(id: string) {
    if (!confirm(t('inventory.outboundConfirmDelete'))) return
    await outboundApi.delete(id)
    setOrders(prev => prev.filter(o => o.id !== id))
  }

  const ingName = (id: string) => ingredients.find(i => i.id === id)?.name ?? id
  const ingUnit = (id: string) => ingredients.find(i => i.id === id)?.unit ?? ''

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between px-6 py-3 border-b border-gray-50 bg-white flex-shrink-0">
        <span className="text-xs text-gray-400">{orders.length} 单</span>
        <button className="btn-primary text-sm" onClick={() => setShowForm(true)}>{t('inventory.outboundNew')}</button>
      </div>
      <div className="flex-1 overflow-auto">
        {loading ? (
          <p className="p-8 text-sm text-gray-400">{t('common.loading')}</p>
        ) : orders.length === 0 ? (
          <div className="p-12 text-center">
            <p className="text-sm text-gray-400 mb-4">{t('inventory.outboundEmpty')}</p>
            <button className="btn-primary text-sm" onClick={() => setShowForm(true)}>{t('inventory.outboundNew')}</button>
          </div>
        ) : (
          <div className="divide-y divide-gray-50">
            {orders.map(order => (
              <div key={order.id} className="px-6 py-4 hover:bg-gray-50/50 transition-colors">
                <div className="flex items-start justify-between gap-4">
                  <div className="flex items-center gap-3 flex-1 min-w-0">
                    <span className={`inline-flex px-2.5 py-1 rounded-full text-xs font-semibold flex-shrink-0 ${OUTBOUND_TYPE_COLOR[order.type]}`}>
                      {t(OUTBOUND_TYPE_KEY[order.type])}
                    </span>
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span className={`inline-flex px-1.5 py-0.5 rounded text-[10px] font-medium ${order.status === 'CONFIRMED' ? 'bg-green-100 text-green-700' : 'bg-amber-100 text-amber-700'}`}>
                          {order.status === 'CONFIRMED' ? t('inventory.outboundStatusConfirmed') : t('inventory.outboundStatusDraft')}
                        </span>
                        <span className="text-xs text-gray-400">{fmtDateFull(order.createdAt)}</span>
                      </div>
                      {order.note && <p className="text-sm text-gray-700 mt-0.5 truncate">{order.note}</p>}
                      <div className="flex flex-wrap gap-x-3 gap-y-0.5 mt-1.5">
                        {order.items.map(item => (
                          <span key={item.id} className="text-xs text-gray-500">
                            {ingName(item.ingredientId)} × {item.qty} {ingUnit(item.ingredientId)}
                          </span>
                        ))}
                      </div>
                    </div>
                  </div>
                  {order.status === 'DRAFT' && (
                    <div className="flex items-center gap-2 flex-shrink-0">
                      <button
                        className="text-xs font-medium text-brand-600 hover:text-brand-800 px-3 py-1.5 rounded-lg hover:bg-brand-50 transition-colors disabled:opacity-50"
                        disabled={confirming === order.id}
                        onClick={() => handleConfirm(order.id)}>
                        {confirming === order.id ? t('inventory.outboundConfirming') : t('inventory.outboundConfirmBtn')}
                      </button>
                      <button
                        className="text-xs font-medium text-red-500 hover:text-red-700 px-3 py-1.5 rounded-lg hover:bg-red-50 transition-colors"
                        onClick={() => handleDelete(order.id)}>
                        {t('inventory.delete')}
                      </button>
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
      {showForm && (
        <OutboundFormDrawer ingredients={ingredients} onSave={handleSave} onClose={() => setShowForm(false)} />
      )}
    </div>
  )
}

// ── Stocktake tab ─────────────────────────────────────────────────────────────

function StocktakeTab({ ingredients }: { ingredients: Ingredient[] }) {
  const { t } = useTranslation()
  const [orders, setOrders] = useState<StocktakeOrder[]>([])
  const [selected, setSelected] = useState<StocktakeOrder | null>(null)
  const [loading, setLoading] = useState(true)
  const [creating, setCreating] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [filter, setFilter] = useState<'all' | 'diff'>('all')
  const [newNote, setNewNote] = useState('')
  const [showNewForm, setShowNewForm] = useState(false)
  // localActual: itemId → string (editing in-progress)
  const [localActual, setLocalActual] = useState<Record<string, string>>({})

  function reload() {
    stocktakeApi.list().then(list => {
      setOrders(list)
      // Keep selected in sync
      if (selected) {
        const updated = list.find(o => o.id === selected.id)
        if (updated) setSelected(updated)
      }
    }).finally(() => setLoading(false))
  }
  useEffect(() => { reload() }, [])

  function openOrder(order: StocktakeOrder) {
    setSelected(order)
    // Seed localActual with current actualQty values
    const seed: Record<string, string> = {}
    for (const item of order.items) seed[item.id] = String(item.actualQty)
    setLocalActual(seed)
    setFilter('all')
  }

  async function handleCreate() {
    setCreating(true)
    try {
      const newOrder = await stocktakeApi.create(newNote)
      const seed: Record<string, string> = {}
      for (const item of newOrder.items) seed[item.id] = String(item.actualQty)
      setLocalActual(seed)
      setOrders(prev => [newOrder, ...prev])
      setSelected(newOrder)
      setShowNewForm(false)
      setNewNote('')
    } finally {
      setCreating(false)
    }
  }

  async function handleBlurItem(orderId: string, itemId: string, val: string) {
    const qty = parseFloat(val)
    if (isNaN(qty) || qty < 0) return
    await stocktakeApi.updateItem(orderId, itemId, qty)
    setSelected(prev => prev ? {
      ...prev,
      items: prev.items.map(i => i.id === itemId ? { ...i, actualQty: qty } : i)
    } : prev)
    setOrders(prev => prev.map(o => o.id === orderId ? {
      ...o,
      items: o.items.map(i => i.id === itemId ? { ...i, actualQty: qty } : i)
    } : o))
  }

  async function handleSubmit() {
    if (!selected || !confirm(t('inventory.stocktakeConfirm'))) return
    setSubmitting(true)
    try {
      const updated = await stocktakeApi.submit(selected.id)
      setSelected(updated)
      setOrders(prev => prev.map(o => o.id === updated.id ? updated : o))
      alert(t('inventory.stocktakeSuccess'))
    } finally {
      setSubmitting(false)
    }
  }

  const ingName = (id: string) => ingredients.find(i => i.id === id)?.name ?? id
  const ingUnit = (id: string) => ingredients.find(i => i.id === id)?.unit ?? ''
  const fmtDate = (ms: number) => new Date(ms).toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })

  const diffItems = (order: StocktakeOrder) => order.items.filter(i => Math.abs(i.actualQty - i.systemQty) > 0.001)

  return (
    <div className="flex h-full">
      {/* Left: stocktake list */}
      <div className="w-72 flex-shrink-0 border-e border-gray-100 flex flex-col bg-gray-50">
        <div className="px-4 py-3 border-b border-gray-100 bg-white flex items-center justify-between gap-2">
          <span className="text-xs text-gray-400">{orders.length} 单</span>
          <button className="btn-primary text-xs py-1.5 px-3" onClick={() => setShowNewForm(true)}>
            {t('inventory.stocktakeNew')}
          </button>
        </div>
        <div className="flex-1 overflow-y-auto">
          {loading ? (
            <p className="p-4 text-sm text-gray-400">{t('common.loading')}</p>
          ) : orders.length === 0 ? (
            <div className="p-6 text-center">
              <p className="text-sm text-gray-400 mb-3">{t('inventory.stocktakeEmpty')}</p>
              <button className="btn-primary text-xs" onClick={() => setShowNewForm(true)}>{t('inventory.stocktakeNew')}</button>
            </div>
          ) : (
            <div className="py-1 divide-y divide-gray-100">
              {orders.map(order => {
                const diffs = diffItems(order).length
                return (
                  <button key={order.id}
                    className={`w-full text-start px-4 py-3 transition-colors hover:bg-white ${selected?.id === order.id ? 'bg-white border-e-2 border-brand-500' : ''}`}
                    onClick={() => openOrder(order)}>
                    <div className="flex items-center justify-between mb-1">
                      <span className={`inline-flex px-2 py-0.5 rounded-full text-[10px] font-medium ${order.status === 'SUBMITTED' ? 'bg-green-100 text-green-700' : 'bg-amber-100 text-amber-700'}`}>
                        {order.status === 'SUBMITTED' ? t('inventory.stocktakeStatusSubmitted') : t('inventory.stocktakeStatusDraft')}
                      </span>
                      <span className="text-xs text-gray-400">{fmtDate(order.createdAt)}</span>
                    </div>
                    {order.note && <p className="text-xs text-gray-600 truncate">{order.note}</p>}
                    <p className="text-xs text-gray-400 mt-0.5">
                      {order.items.length} {t('inventory.stocktakeColItems')}
                      {diffs > 0 && <span className="text-amber-600 ml-1">· {diffs} {t('inventory.stocktakeColDiff')}</span>}
                    </p>
                  </button>
                )
              })}
            </div>
          )}
        </div>
      </div>

      {/* Right: editor / detail */}
      <div className="flex-1 min-w-0 flex flex-col">
        {!selected ? (
          <div className="flex-1 flex items-center justify-center text-center">
            <div>
              <svg className="w-12 h-12 mx-auto mb-3 text-gray-200" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" />
              </svg>
              <p className="text-sm text-gray-400">{t('inventory.stocktakeEmpty')}</p>
            </div>
          </div>
        ) : (
          <>
            {/* Header */}
            <div className="px-6 py-4 border-b border-gray-100 flex items-start justify-between flex-shrink-0">
              <div>
                <h2 className="text-base font-semibold text-gray-900">
                  {selected.note || t('inventory.stocktakeEditorTitle')}
                </h2>
                <p className="text-xs text-gray-400 mt-0.5">{fmtDate(selected.createdAt)} · {selected.items.length} {t('inventory.stocktakeColItems')}</p>
                {(() => {
                  const diffs = diffItems(selected)
                  const over = diffs.filter(i => i.actualQty > i.systemQty).length
                  const short = diffs.filter(i => i.actualQty < i.systemQty).length
                  if (diffs.length === 0) return null
                  return (
                    <p className="text-xs mt-1">
                      {over > 0 && <span className="text-green-600 me-2">{t('inventory.stocktakeSummaryOver')} {over}</span>}
                      {short > 0 && <span className="text-red-500">{t('inventory.stocktakeSummaryShort')} {short}</span>}
                    </p>
                  )
                })()}
              </div>
              <div className="flex items-center gap-2">
                {/* Filter toggle */}
                <div className="flex rounded-lg border border-gray-200 text-xs overflow-hidden">
                  <button className={`px-3 py-1.5 transition-colors ${filter === 'all' ? 'bg-gray-100 font-medium text-gray-800' : 'text-gray-500 hover:bg-gray-50'}`}
                    onClick={() => setFilter('all')}>{t('inventory.stocktakeFilterAll')}</button>
                  <button className={`px-3 py-1.5 border-l border-gray-200 transition-colors ${filter === 'diff' ? 'bg-amber-50 font-medium text-amber-700' : 'text-gray-500 hover:bg-gray-50'}`}
                    onClick={() => setFilter('diff')}>{t('inventory.stocktakeFilterDiff')}</button>
                </div>
                {selected.status === 'DRAFT' && (
                  <button className="btn-primary text-sm min-w-[100px]" disabled={submitting} onClick={handleSubmit}>
                    {submitting ? t('inventory.stocktakeSubmitting') : t('inventory.stocktakeSubmit')}
                  </button>
                )}
              </div>
            </div>

            {/* Table */}
            <div className="flex-1 overflow-auto">
              <table className="w-full text-sm">
                <thead className="sticky top-0 bg-gray-50 border-b border-gray-100">
                  <tr>
                    <th className="text-start px-6 py-3 font-medium text-gray-500">{t('inventory.stocktakeColIngredient')}</th>
                    <th className="text-end px-4 py-3 font-medium text-gray-500">{t('inventory.stocktakeColSystem')}</th>
                    <th className="text-end px-4 py-3 font-medium text-gray-500">{t('inventory.stocktakeColActual')}</th>
                    <th className="text-end px-6 py-3 font-medium text-gray-500">{t('inventory.stocktakeColVariance')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {selected.items
                    .filter(item => filter === 'all' || Math.abs(item.actualQty - item.systemQty) > 0.001)
                    .map(item => {
                      const diff = (localActual[item.id] != null
                        ? parseFloat(localActual[item.id]) || item.actualQty
                        : item.actualQty) - item.systemQty
                      const hasDiff = Math.abs(diff) > 0.001
                      return (
                        <tr key={item.id} className={hasDiff ? 'bg-amber-50/40' : ''}>
                          <td className="px-6 py-3 font-medium text-gray-800">
                            {ingName(item.ingredientId)}
                            <span className="text-xs text-gray-400 ms-1">{ingUnit(item.ingredientId)}</span>
                          </td>
                          <td className="px-4 py-3 text-end font-mono text-gray-500">{item.systemQty}</td>
                          <td className="px-4 py-3 text-end">
                            {selected.status === 'DRAFT' ? (
                              <input
                                className={`w-24 text-end rounded-lg border px-2 py-1 font-mono text-sm focus:outline-none focus:ring-2 focus:ring-brand-400 ${hasDiff ? 'border-amber-300 bg-amber-50' : 'border-gray-200 bg-white'}`}
                                type="number" min={0} step="0.001"
                                value={localActual[item.id] ?? String(item.actualQty)}
                                onChange={e => setLocalActual(prev => ({ ...prev, [item.id]: e.target.value }))}
                                onBlur={e => handleBlurItem(selected.id, item.id, e.target.value)}
                              />
                            ) : (
                              <span className="font-mono text-gray-700">{item.actualQty}</span>
                            )}
                          </td>
                          <td className={`px-6 py-3 text-end font-mono font-semibold ${diff > 0.001 ? 'text-green-600' : diff < -0.001 ? 'text-red-500' : 'text-gray-300'}`}>
                            {hasDiff ? (diff > 0 ? '+' : '') + diff.toFixed(3).replace(/\.?0+$/, '') : '—'}
                          </td>
                        </tr>
                      )
                    })}
                </tbody>
              </table>
            </div>
          </>
        )}
      </div>

      {/* New stocktake modal */}
      {showNewForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-sm mx-4 p-6">
            <h2 className="text-base font-semibold text-gray-900 mb-4">{t('inventory.stocktakeEditorNewTitle')}</h2>
            <p className="text-xs text-gray-500 mb-3">{t('inventory.stocktakeEditorSubtitle')}</p>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">{t('inventory.stocktakeNote')}</label>
            <input className="input w-full mb-5" value={newNote}
              onChange={e => setNewNote(e.target.value)}
              placeholder={t('inventory.stocktakeNotePlaceholder')}
              autoFocus onKeyDown={e => { if (e.key === 'Enter') handleCreate() }} />
            <div className="flex justify-end gap-3">
              <button className="btn-secondary" onClick={() => { setShowNewForm(false); setNewNote('') }}>{t('inventory.cancel')}</button>
              <button className="btn-primary" disabled={creating} onClick={handleCreate}>
                {creating ? t('inventory.stocktakeSubmitting') : t('inventory.stocktakeNew')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

type Tab = 'report' | 'ingredients' | 'purchase-orders' | 'outbound' | 'suppliers' | 'bom' | 'stocktake'

const TAB_LABELS: Record<Tab, string> = {
  'report': 'inventory.tabReport',
  'ingredients': 'inventory.tabIngredients',
  'purchase-orders': 'inventory.tabPurchaseOrders',
  'outbound': 'inventory.tabOutbound',
  'suppliers': 'inventory.tabSuppliers',
  'bom': 'inventory.tabBom',
  'stocktake': 'inventory.tabStocktake',
}

export default function InventoryPage() {
  const { t } = useTranslation()
  const [tab, setTab] = useState<Tab>('ingredients')
  const [ingredients, setIngredients] = useState<Ingredient[]>([])
  const [suppliers, setSuppliers] = useState<Supplier[]>([])

  useEffect(() => {
    ingredientApi.list().then(setIngredients)
    supplierApi.list().then(setSuppliers)
  }, [])

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="px-6 pt-5 pb-0 border-b border-gray-100 bg-white flex-shrink-0">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h1 className="text-lg font-semibold text-gray-900">{t('inventory.title')}</h1>
            <p className="text-sm text-gray-400 mt-0.5">{t('inventory.subtitle')}</p>
          </div>
        </div>
        {/* Tabs */}
        <div className="flex gap-1">
          {(Object.keys(TAB_LABELS) as Tab[]).map(tp => (
            <button key={tp}
              className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${tab === tp ? 'border-brand-500 text-brand-600' : 'border-transparent text-gray-500 hover:text-gray-700'}`}
              onClick={() => setTab(tp)}>
              {t(TAB_LABELS[tp])}
            </button>
          ))}
        </div>
      </div>

      {/* Tab content */}
      <div className="flex-1 min-h-0 overflow-hidden">
        {tab === 'report' && <ReportsTab />}
        {tab === 'ingredients' && <IngredientsTab />}
        {tab === 'purchase-orders' && <PurchaseOrdersTab ingredients={ingredients} suppliers={suppliers} />}
        {tab === 'outbound' && <OutboundOrdersTab ingredients={ingredients} />}
        {tab === 'suppliers' && <SuppliersTab onSuppliersChange={setSuppliers} />}
        {tab === 'bom' && <BomTab ingredients={ingredients} />}
        {tab === 'stocktake' && <StocktakeTab ingredients={ingredients} />}
      </div>
    </div>
  )
}
