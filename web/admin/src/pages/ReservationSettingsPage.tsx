import { useEffect, useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { shiftApi, specialDayApi, type Shift, type TurnTimeRule, type SpecialDay, type SlotAvailability } from '../api/shift'

// ── helpers ───────────────────────────────────────────────────────────────────

function timeToMinutes(t: string) {
  const [h, m] = t.split(':').map(Number)
  return h * 60 + m
}
function todayStr() { return new Date().toISOString().split('T')[0] }

const DAY_LABELS = ['日', '一', '二', '三', '四', '五', '六']

// ══════════════════════════════════════════════════════════════════════════════
// Main page
// ══════════════════════════════════════════════════════════════════════════════

export default function ReservationSettingsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [shifts, setShifts] = useState<Shift[]>([])
  const [specialDays, setSpecialDays] = useState<SpecialDay[]>([])
  const [editingShift, setEditingShift] = useState<Shift | null | 'new'>(null)
  const [editingDay, setEditingDay] = useState<SpecialDay | null | 'new'>(null)
  const [pacingShift, setPacingShift] = useState<Shift | null>(null)
  const [pacingData, setPacingData] = useState<SlotAvailability[]>([])

  const reload = useCallback(() => {
    Promise.all([shiftApi.list(), specialDayApi.list()]).then(([sh, sd]) => {
      setShifts(sh)
      setSpecialDays(sd)
    })
  }, [])

  useEffect(() => { reload() }, [reload])

  useEffect(() => {
    if (pacingShift) {
      shiftApi.getAvailability(pacingShift.id, todayStr()).then(setPacingData)
    }
  }, [pacingShift])

  async function handleDeleteShift(s: Shift) {
    if (!confirm(t('shifts.confirmDelete'))) return
    await shiftApi.delete(s.id)
    reload()
  }

  async function toggleShiftEnabled(s: Shift) {
    await shiftApi.update(s.id, { enabled: !s.enabled })
    reload()
  }

  async function handleDeleteSpecialDay(d: SpecialDay) {
    if (!confirm(t('shifts.confirmDeleteSpecialDay'))) return
    await specialDayApi.delete(d.id)
    reload()
  }

  return (
    <div className="p-8 w-full max-w-4xl">
      {/* Back nav */}
      <button className="text-sm text-gray-400 hover:text-gray-600 flex items-center gap-1 mb-5" onClick={() => navigate('/reservations')}>
        <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M12.707 5.293a1 1 0 010 1.414L9.414 10l3.293 3.293a1 1 0 01-1.414 1.414l-4-4a1 1 0 010-1.414l4-4a1 1 0 011.414 0z" clipRule="evenodd" /></svg>
        返回预约管理
      </button>

      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-gray-900">{t('shifts.title')}</h1>
        <p className="mt-1 text-sm text-gray-500">{t('shifts.subtitle')}</p>
      </div>

      {/* ── Shifts ── */}
      <section className="mb-10">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-base font-semibold text-gray-800">班次列表</h2>
          <button className="btn-primary text-sm" onClick={() => setEditingShift('new')}>+ {t('shifts.add')}</button>
        </div>

        {shifts.length === 0 && (
          <div className="card py-10 text-center text-sm text-gray-400">{t('shifts.empty')}</div>
        )}

        <div className="space-y-3">
          {shifts.map(s => (
            <div key={s.id} className={`card p-4 transition-opacity ${!s.enabled ? 'opacity-60' : ''}`}>
              <div className="flex items-start justify-between gap-4">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <h3 className="font-semibold text-gray-900">{s.name}</h3>
                    <span className="font-mono text-sm text-gray-600">{s.startTime} – {s.endTime}</span>
                    <span className={`badge ${s.enabled ? 'bg-emerald-100 text-emerald-700' : 'bg-gray-100 text-gray-500'}`}>
                      {s.enabled ? t('shifts.enabled') : t('shifts.disabled')}
                    </span>
                  </div>
                  <div className="flex flex-wrap gap-3 mt-2 text-xs text-gray-500">
                    <span>最大 <strong className="text-gray-800">{s.maxCovers}</strong> 人头</span>
                    <span>目标 <strong className="text-gray-800">{s.coverGoal}</strong> 人头</span>
                    <span>粒度 <strong className="text-gray-800">{s.slotIntervalMinutes}</strong> 分钟</span>
                    <span>默认翻台 <strong className="text-gray-800">{s.defaultTurnTimeMinutes}</strong> 分钟</span>
                    {s.daysOfWeek.length > 0
                      ? <span>仅限：{s.daysOfWeek.map(d => `周${DAY_LABELS[d]}`).join(' ')}</span>
                      : <span>每天</span>
                    }
                  </div>
                  {s.turnTimeByPartySize.length > 0 && (
                    <div className="flex gap-2 mt-1.5 flex-wrap">
                      {s.turnTimeByPartySize.map(r => (
                        <span key={r.partySize} className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded">{r.partySize}人 → {r.minutes}分钟</span>
                      ))}
                    </div>
                  )}
                </div>
                <div className="flex gap-2 shrink-0">
                  <button
                    className="text-xs text-gray-400 hover:text-brand-600"
                    onClick={() => { setPacingShift(s); shiftApi.getAvailability(s.id, todayStr()).then(setPacingData) }}
                  >
                    Pacing
                  </button>
                  <button className="text-xs text-gray-400 hover:text-gray-700" onClick={() => toggleShiftEnabled(s)}>
                    {s.enabled ? t('common.disable') : t('common.enable')}
                  </button>
                  <button className="text-xs text-gray-400 hover:text-brand-600" onClick={() => setEditingShift(s)}>
                    {t('menu.edit')}
                  </button>
                  <button className="text-xs text-gray-400 hover:text-red-500" onClick={() => handleDeleteShift(s)}>
                    {t('menu.delete')}
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* ── Pacing Chart ── */}
      {pacingShift && pacingData.length > 0 && (
        <section className="mb-10">
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-base font-semibold text-gray-800">{t('shifts.pacingTitle')} — {pacingShift.name}</h2>
            <button className="text-xs text-gray-400 hover:text-gray-600" onClick={() => setPacingShift(null)}>关闭</button>
          </div>
          <div className="card p-4">
            <p className="text-xs text-gray-400 mb-4">{t('shifts.pacingHint')}</p>
            <div className="flex items-end gap-2 overflow-x-auto pb-2">
              {pacingData.map(slot => {
                const ratio = slot.capacity > 0 ? slot.covers / slot.capacity : 0
                const barH = Math.max(4, Math.round(ratio * 80))
                const barColor = ratio >= 0.9 ? 'bg-red-400' : ratio >= 0.6 ? 'bg-amber-400' : 'bg-emerald-400'
                return (
                  <div key={slot.time} className="flex flex-col items-center gap-1 min-w-[44px]">
                    <span className="text-xs text-gray-600 font-mono">{slot.covers}</span>
                    <div className="w-8 bg-gray-100 rounded-t-sm relative" style={{ height: 80 }}>
                      <div className={`absolute bottom-0 left-0 right-0 rounded-t-sm transition-all ${barColor}`} style={{ height: barH }} />
                    </div>
                    <span className="text-xs text-gray-400 font-mono">{slot.time}</span>
                  </div>
                )
              })}
            </div>
            <div className="flex gap-4 mt-3 text-xs text-gray-400">
              <span className="flex items-center gap-1"><span className="w-2.5 h-2.5 rounded-sm bg-emerald-400 inline-block" /> 正常</span>
              <span className="flex items-center gap-1"><span className="w-2.5 h-2.5 rounded-sm bg-amber-400 inline-block" /> 较满（60%+）</span>
              <span className="flex items-center gap-1"><span className="w-2.5 h-2.5 rounded-sm bg-red-400 inline-block" /> 即将满员（90%+）</span>
            </div>
          </div>
        </section>
      )}

      {/* ── Special Days ── */}
      <section>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-base font-semibold text-gray-800">{t('shifts.specialDaysTitle')}</h2>
          <button className="btn-primary text-sm" onClick={() => setEditingDay('new')}>+ {t('shifts.addSpecialDay')}</button>
        </div>

        {specialDays.length === 0 && (
          <div className="card py-8 text-center text-sm text-gray-400">{t('shifts.specialDaysEmpty')}</div>
        )}

        <div className="space-y-2">
          {[...specialDays].sort((a, b) => a.date.localeCompare(b.date)).map(d => (
            <div key={d.id} className={`card p-3 flex items-center gap-4 ${d.closed ? 'bg-red-50/50' : ''}`}>
              <span className="font-mono text-sm font-semibold text-gray-800 w-28 shrink-0">{d.date}</span>
              <span className="font-medium text-gray-800 flex-1">{d.label}</span>
              {d.closed
                ? <span className="badge bg-red-100 text-red-600">休息</span>
                : d.maxCoversOverride
                  ? <span className="badge bg-blue-100 text-blue-700">容量 {d.maxCoversOverride}</span>
                  : <span className="badge bg-gray-100 text-gray-500">正常运营</span>
              }
              <div className="flex gap-2 shrink-0">
                <button className="text-xs text-gray-400 hover:text-brand-600" onClick={() => setEditingDay(d)}>{t('menu.edit')}</button>
                <button className="text-xs text-gray-400 hover:text-red-500" onClick={() => handleDeleteSpecialDay(d)}>{t('menu.delete')}</button>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Modals */}
      {editingShift !== null && (
        <ShiftModal
          shift={editingShift === 'new' ? undefined : editingShift}
          onClose={() => setEditingShift(null)}
          onSaved={() => { setEditingShift(null); reload() }}
        />
      )}
      {editingDay !== null && (
        <SpecialDayModal
          day={editingDay === 'new' ? undefined : editingDay}
          onClose={() => setEditingDay(null)}
          onSaved={() => { setEditingDay(null); reload() }}
        />
      )}
    </div>
  )
}

// ══════════════════════════════════════════════════════════════════════════════
// Shift Form Modal
// ══════════════════════════════════════════════════════════════════════════════

function ShiftModal({ shift, onClose, onSaved }: { shift?: Shift; onClose: () => void; onSaved: () => void }) {
  const { t } = useTranslation()
  const isEdit = Boolean(shift?.id)
  const [name, setName] = useState(shift?.name ?? '')
  const [startTime, setStartTime] = useState(shift?.startTime ?? '11:30')
  const [endTime, setEndTime] = useState(shift?.endTime ?? '14:30')
  const [daysOfWeek, setDaysOfWeek] = useState<number[]>(shift?.daysOfWeek ?? [])
  const [maxCovers, setMaxCovers] = useState(String(shift?.maxCovers ?? 60))
  const [coverGoal, setCoverGoal] = useState(String(shift?.coverGoal ?? 50))
  const [defaultTurnTime, setDefaultTurnTime] = useState(String(shift?.defaultTurnTimeMinutes ?? 90))
  const [slotInterval, setSlotInterval] = useState<15 | 30>(shift?.slotIntervalMinutes ?? 30)
  const [cutoff, setCutoff] = useState(String(shift?.bookingCutoffMinutes ?? 60))
  const [turnRules, setTurnRules] = useState<TurnTimeRule[]>(shift?.turnTimeByPartySize ?? [{ partySize: 2, minutes: 75 }, { partySize: 4, minutes: 90 }])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  function toggleDay(d: number) {
    setDaysOfWeek(prev => prev.includes(d) ? prev.filter(x => x !== d) : [...prev, d].sort())
  }

  function addTurnRule() {
    setTurnRules(prev => [...prev, { partySize: (prev[prev.length - 1]?.partySize ?? 4) + 2, minutes: parseInt(defaultTurnTime) + 15 }])
  }
  function removeTurnRule(i: number) { setTurnRules(prev => prev.filter((_, j) => j !== i)) }
  function updateTurnRule(i: number, field: keyof TurnTimeRule, value: number) {
    setTurnRules(prev => prev.map((r, j) => j === i ? { ...r, [field]: value } : r))
  }

  async function handleSave() {
    if (!name.trim()) { setError('请输入班次名称'); return }
    if (timeToMinutes(startTime) >= timeToMinutes(endTime)) { setError('结束时间必须晚于开始时间'); return }
    setSaving(true); setError('')
    try {
      const payload = {
        name: name.trim(), startTime, endTime, daysOfWeek,
        maxCovers: parseInt(maxCovers) || 60, coverGoal: parseInt(coverGoal) || 50,
        defaultTurnTimeMinutes: parseInt(defaultTurnTime) || 90,
        turnTimeByPartySize: turnRules.filter(r => r.partySize > 0 && r.minutes > 0),
        slotIntervalMinutes: slotInterval, bookingCutoffMinutes: parseInt(cutoff) || 60,
        enabled: shift?.enabled ?? true,
      }
      if (isEdit && shift) await shiftApi.update(shift.id, payload)
      else await shiftApi.create(payload)
      onSaved()
    } catch { setError(t('menu.saveFailed')) }
    finally { setSaving(false) }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/30 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-lg max-h-[92vh] overflow-y-auto">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h2 className="text-base font-semibold text-gray-900">{isEdit ? t('shifts.editTitle') : t('shifts.addTitle')}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 p-1 rounded-lg hover:bg-gray-100">
            <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" /></svg>
          </button>
        </div>
        <div className="px-6 py-5 space-y-4">
          {/* Name */}
          <div>
            <label className="field-label">{t('shifts.fieldName')} *</label>
            <input className="input w-full" value={name} onChange={e => setName(e.target.value)} autoFocus placeholder="午市、晚市…" />
          </div>

          {/* Time range */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="field-label">{t('shifts.fieldStart')}</label>
              <input type="time" className="input w-full font-mono" value={startTime} onChange={e => setStartTime(e.target.value)} />
            </div>
            <div>
              <label className="field-label">{t('shifts.fieldEnd')}</label>
              <input type="time" className="input w-full font-mono" value={endTime} onChange={e => setEndTime(e.target.value)} />
            </div>
          </div>

          {/* Days of week */}
          <div>
            <label className="field-label">{t('shifts.fieldDays')}</label>
            <div className="flex gap-1.5">
              {[0, 1, 2, 3, 4, 5, 6].map(d => (
                <button
                  key={d}
                  type="button"
                  onClick={() => toggleDay(d)}
                  className={`w-8 h-8 rounded-full text-xs font-medium transition-colors ${daysOfWeek.includes(d) ? 'bg-brand-600 text-white' : 'bg-gray-100 text-gray-500 hover:bg-gray-200'}`}
                >
                  {DAY_LABELS[d]}
                </button>
              ))}
              {daysOfWeek.length > 0 && (
                <button type="button" className="text-xs text-gray-400 hover:text-gray-600 ml-1" onClick={() => setDaysOfWeek([])}>清空（每天）</button>
              )}
            </div>
          </div>

          {/* Covers + Goal */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="field-label">{t('shifts.fieldMaxCovers')}</label>
              <input type="number" min={1} className="input w-full font-mono" value={maxCovers} onChange={e => setMaxCovers(e.target.value)} />
            </div>
            <div>
              <label className="field-label">{t('shifts.fieldCoverGoal')}</label>
              <input type="number" min={1} className="input w-full font-mono" value={coverGoal} onChange={e => setCoverGoal(e.target.value)} />
            </div>
          </div>

          {/* Slot interval + Cutoff */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="field-label">{t('shifts.fieldSlotInterval')}</label>
              <div className="flex gap-2">
                {([15, 30] as const).map(v => (
                  <button key={v} type="button" onClick={() => setSlotInterval(v)}
                    className={`flex-1 text-sm py-2 rounded-lg border font-mono transition-colors ${slotInterval === v ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-gray-200 text-gray-600 hover:border-gray-400'}`}>
                    {v} 分钟
                  </button>
                ))}
              </div>
            </div>
            <div>
              <label className="field-label">{t('shifts.fieldCutoff')}</label>
              <input type="number" min={0} className="input w-full font-mono" value={cutoff} onChange={e => setCutoff(e.target.value)} />
            </div>
          </div>

          {/* Turn times */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="field-label mb-0">{t('shifts.fieldTurnTimes')}</label>
              <button type="button" className="text-xs text-brand-600 hover:underline" onClick={addTurnRule}>+ {t('shifts.turnTimeAddRule')}</button>
            </div>
            <div>
              <label className="field-label">{t('shifts.fieldDefaultTurnTime')}</label>
              <input type="number" min={30} max={300} className="input w-32 font-mono" value={defaultTurnTime} onChange={e => setDefaultTurnTime(e.target.value)} />
            </div>
            {turnRules.length > 0 && (
              <div className="space-y-1.5 mt-2">
                {turnRules.map((rule, i) => (
                  <div key={i} className="flex items-center gap-2">
                    <input type="number" min={1} max={20} className="input w-16 font-mono text-center" value={rule.partySize} onChange={e => updateTurnRule(i, 'partySize', parseInt(e.target.value) || 2)} />
                    <span className="text-xs text-gray-400">{t('shifts.turnTimePartyOf')}</span>
                    <input type="number" min={30} max={300} step={15} className="input w-20 font-mono text-center" value={rule.minutes} onChange={e => updateTurnRule(i, 'minutes', parseInt(e.target.value) || 90)} />
                    <span className="text-xs text-gray-400">分钟</span>
                    <button type="button" className="text-gray-300 hover:text-red-400 ml-auto" onClick={() => removeTurnRule(i)}>×</button>
                  </div>
                ))}
              </div>
            )}
          </div>

          {error && <p className="text-sm text-red-600">{error}</p>}
          <div className="flex gap-3 pt-2">
            <button className="btn-secondary flex-1" onClick={onClose}>{t('menu.cancel')}</button>
            <button className="btn-primary flex-1" onClick={handleSave} disabled={saving}>{saving ? t('menu.saving') : t('menu.save')}</button>
          </div>
        </div>
      </div>
    </div>
  )
}

// ══════════════════════════════════════════════════════════════════════════════
// Special Day Modal
// ══════════════════════════════════════════════════════════════════════════════

function SpecialDayModal({ day, onClose, onSaved }: { day?: SpecialDay; onClose: () => void; onSaved: () => void }) {
  const { t } = useTranslation()
  const [date, setDate] = useState(day?.date ?? '')
  const [label, setLabel] = useState(day?.label ?? '')
  const [closed, setClosed] = useState(day?.closed ?? false)
  const [maxCovers, setMaxCovers] = useState(String(day?.maxCoversOverride ?? ''))
  const [saving, setSaving] = useState(false)

  async function handleSave() {
    if (!date || !label.trim()) return
    setSaving(true)
    try {
      const payload = { date, label: label.trim(), closed, maxCoversOverride: maxCovers ? parseInt(maxCovers) : undefined }
      if (day) await specialDayApi.update(day.id, payload)
      else await specialDayApi.create(payload)
      onSaved()
    } finally { setSaving(false) }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/30 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-sm">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h2 className="text-base font-semibold text-gray-900">{day ? '编辑特殊日期' : t('shifts.addSpecialDay')}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 p-1 rounded-lg hover:bg-gray-100">
            <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" /></svg>
          </button>
        </div>
        <div className="px-6 py-5 space-y-3">
          <div>
            <label className="field-label">{t('shifts.specialDayDate')} *</label>
            <input type="date" className="input w-full font-mono" value={date} onChange={e => setDate(e.target.value)} autoFocus />
          </div>
          <div>
            <label className="field-label">{t('shifts.specialDayLabel')} *</label>
            <input className="input w-full" value={label} onChange={e => setLabel(e.target.value)} placeholder="春节、父亲节…" />
          </div>
          <label className="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
            <input type="checkbox" checked={closed} onChange={e => setClosed(e.target.checked)} className="rounded" />
            {t('shifts.specialDayClosed')}
          </label>
          {!closed && (
            <div>
              <label className="field-label">{t('shifts.specialDayMaxCovers')}</label>
              <input type="number" className="input w-full font-mono" value={maxCovers} onChange={e => setMaxCovers(e.target.value)} placeholder="留空 = 不限" />
            </div>
          )}
          <div className="flex gap-3 pt-2">
            <button className="btn-secondary flex-1" onClick={onClose}>{t('menu.cancel')}</button>
            <button className="btn-primary flex-1" onClick={handleSave} disabled={saving || !date || !label}>{saving ? t('menu.saving') : t('menu.save')}</button>
          </div>
        </div>
      </div>
    </div>
  )
}
