import { useEffect, useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { reservationApi, waitlistApi, type Reservation, type WaitlistEntry, type GuestTag } from '../api/crm'
import { shiftApi, type Shift, type SlotAvailability } from '../api/shift'
import { tableApi, type RestaurantTable } from '../api/tables'

// ── helpers ───────────────────────────────────────────────────────────────────

function timeToMinutes(t: string): number {
  const [h, m] = t.split(':').map(Number)
  return h * 60 + m
}
function minutesToTime(m: number): string {
  return `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`
}
function todayStr(): string {
  return new Date().toISOString().split('T')[0]
}
function fmtElapsed(ms: number) {
  const min = Math.floor((Date.now() - ms) / 60000)
  return min < 60 ? `${min}分钟前` : `${Math.floor(min / 60)}小时前`
}
function fmtCountdown(ms: number) {
  const diff = Math.max(0, Math.floor((ms - Date.now()) / 1000))
  const m = Math.floor(diff / 60), s = diff % 60
  return `${m}:${String(s).padStart(2, '0')}`
}
function getTurnTime(shift: Shift | null, partySize: number): number {
  if (!shift) return 90
  const rule = [...shift.turnTimeByPartySize].sort((a, b) => b.partySize - a.partySize).find(r => partySize >= r.partySize)
  return rule?.minutes ?? shift.defaultTurnTimeMinutes
}

// ── constants ─────────────────────────────────────────────────────────────────

const STATUS_BLOCK_COLORS: Record<string, string> = {
  PENDING:   'bg-amber-400 text-white',
  CONFIRMED: 'bg-blue-500 text-white',
  SEATED:    'bg-emerald-500 text-white',
  COMPLETED: 'bg-gray-300 text-gray-600',
  CANCELLED: 'bg-gray-100 text-gray-400',
  NO_SHOW:   'bg-red-300 text-red-800',
}
const STATUS_BADGE_COLORS: Record<string, string> = {
  PENDING:   'bg-amber-100 text-amber-700',
  CONFIRMED: 'bg-blue-100 text-blue-700',
  SEATED:    'bg-emerald-100 text-emerald-700',
  COMPLETED: 'bg-gray-100 text-gray-600',
  CANCELLED: 'bg-gray-100 text-gray-400',
  NO_SHOW:   'bg-red-100 text-red-700',
}
const GUEST_TAG_EMOJIS: Record<GuestTag, string> = {
  BIRTHDAY:   '🎂',
  ANNIVERSARY: '💍',
  VIP:        '⭐',
  FIRST_TIME: '🆕',
}
const SOURCE_LABELS: Record<string, string> = {
  ONLINE: '在线', PHONE: '电话', WALK_IN: '到店', CONCIERGE: '礼宾',
}

// ══════════════════════════════════════════════════════════════════════════════
// Main page
// ══════════════════════════════════════════════════════════════════════════════

type View = 'timeline' | 'list' | 'floor'

export default function ReservationsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const [date, setDate] = useState(todayStr())
  const [shifts, setShifts] = useState<Shift[]>([])
  const [selectedShift, setSelectedShift] = useState<Shift | null>(null)
  const [reservations, setReservations] = useState<Reservation[]>([])
  const [tables, setTables] = useState<RestaurantTable[]>([])
  const [waitlist, setWaitlist] = useState<WaitlistEntry[]>([])
  const [view, setView] = useState<View>('timeline')
  const [editReservation, setEditReservation] = useState<Reservation | null | 'new'>(null)
  const [addWaitlist, setAddWaitlist] = useState(false)
  const [detailReservation, setDetailReservation] = useState<Reservation | null>(null)
  const [, forceRefresh] = useState(0)

  // Countdown ticker
  useEffect(() => {
    const interval = setInterval(() => forceRefresh(n => n + 1), 10000)
    return () => clearInterval(interval)
  }, [])

  const loadAll = useCallback(() => {
    Promise.all([
      shiftApi.list(),
      tableApi.list(),
      waitlistApi.list(),
    ]).then(([sh, tb, wl]) => {
      setShifts(sh)
      setTables(tb.filter(t => t.active))
      setWaitlist(wl.filter(w => ['WAITING', 'NOTIFIED'].includes(w.status)))
      if (sh.length > 0 && !selectedShift) {
        // Auto-select shift closest to current time
        const nowMin = new Date().getHours() * 60 + new Date().getMinutes()
        const best = sh.find(s => s.enabled && timeToMinutes(s.startTime) <= nowMin && timeToMinutes(s.endTime) >= nowMin)
          ?? sh.find(s => s.enabled && timeToMinutes(s.startTime) > nowMin)
          ?? sh.find(s => s.enabled)
          ?? null
        setSelectedShift(best)
      }
    })
  }, [selectedShift])

  const loadReservations = useCallback(() => {
    reservationApi.list({ date }).then(setReservations)
  }, [date])

  useEffect(() => { loadAll() }, [date])
  useEffect(() => { loadReservations() }, [loadReservations])

  const shiftReservations = selectedShift
    ? reservations.filter(r => r.shiftId === selectedShift.id)
    : reservations

  const covers = shiftReservations.filter(r => ['PENDING', 'CONFIRMED', 'SEATED'].includes(r.status)).reduce((s, r) => s + r.partySize, 0)
  const seated = shiftReservations.filter(r => r.status === 'SEATED').reduce((s, r) => s + r.partySize, 0)
  const pending = shiftReservations.filter(r => r.status === 'PENDING').length
  const activeWaiting = waitlist.filter(w => w.status === 'WAITING').length

  async function handleStatusChange(r: Reservation, status: Reservation['status']) {
    const patch: Partial<Reservation> = { status }
    if (status === 'SEATED') patch.seatedAt = Date.now()
    if (status === 'COMPLETED') patch.completedAt = Date.now()
    await reservationApi.update(r.id, patch)
    loadReservations()
  }

  async function handleWaitlistNotify(w: WaitlistEntry) {
    await waitlistApi.notify(w.id)
    loadAll()
  }
  async function handleWaitlistSeat(w: WaitlistEntry) {
    await waitlistApi.seat(w.id, w.tableId)
    loadAll()
  }
  async function handleWaitlistCancel(w: WaitlistEntry) {
    await waitlistApi.cancel(w.id)
    loadAll()
  }

  return (
    <div className="flex h-screen overflow-hidden">
      {/* Main content */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* Header */}
        <div className="px-6 pt-6 pb-4 border-b border-gray-100 bg-white">
          <div className="flex items-center justify-between mb-3">
            <div>
              <h1 className="text-xl font-semibold text-gray-900">{t('reservations.title')}</h1>
              <p className="text-xs text-gray-400 mt-0.5">{t('reservations.subtitle')}</p>
            </div>
            <div className="flex items-center gap-2">
              <button
                className="text-xs text-gray-400 hover:text-gray-600 flex items-center gap-1 px-2 py-1.5 rounded-md hover:bg-gray-100"
                onClick={() => navigate('/reservations/settings')}
              >
                <svg className="w-3.5 h-3.5" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M11.49 3.17c-.38-1.56-2.6-1.56-2.98 0a1.532 1.532 0 01-2.286.948c-1.372-.836-2.942.734-2.106 2.106.54.886.061 2.042-.947 2.287-1.561.379-1.561 2.6 0 2.978a1.532 1.532 0 01.947 2.287c-.836 1.372.734 2.942 2.106 2.106a1.532 1.532 0 012.287.947c.379 1.561 2.6 1.561 2.978 0a1.533 1.533 0 012.287-.947c1.372.836 2.942-.734 2.106-2.106a1.533 1.533 0 01.947-2.287c1.561-.379 1.561-2.6 0-2.978a1.532 1.532 0 01-.947-2.287c.836-1.372-.734-2.942-2.106-2.106a1.532 1.532 0 01-2.287-.947zM10 13a3 3 0 100-6 3 3 0 000 6z" clipRule="evenodd" /></svg>
                {t('reservations.settingsLink')}
              </button>
              <button className="btn-primary text-sm" onClick={() => setEditReservation('new')}>
                + {t('reservations.newReservation')}
              </button>
            </div>
          </div>

          {/* Date nav + Shift selector */}
          <div className="flex items-center gap-4 flex-wrap">
            <div className="flex items-center gap-1">
              <button className="p-1 rounded hover:bg-gray-100" onClick={() => {
                const d = new Date(date); d.setDate(d.getDate() - 1); setDate(d.toISOString().split('T')[0])
              }}>
                <svg className="w-4 h-4 text-gray-500" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M12.707 5.293a1 1 0 010 1.414L9.414 10l3.293 3.293a1 1 0 01-1.414 1.414l-4-4a1 1 0 010-1.414l4-4a1 1 0 011.414 0z" clipRule="evenodd" /></svg>
              </button>
              <input type="date" className="input text-sm font-mono py-1 px-2" value={date} onChange={e => setDate(e.target.value)} />
              <button className="p-1 rounded hover:bg-gray-100" onClick={() => {
                const d = new Date(date); d.setDate(d.getDate() + 1); setDate(d.toISOString().split('T')[0])
              }}>
                <svg className="w-4 h-4 text-gray-500" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clipRule="evenodd" /></svg>
              </button>
              {date !== todayStr() && (
                <button className="text-xs text-brand-600 hover:underline ml-1" onClick={() => setDate(todayStr())}>{t('reservations.today')}</button>
              )}
            </div>

            <div className="flex items-center gap-1">
              <button
                onClick={() => setSelectedShift(null)}
                className={`text-xs px-3 py-1.5 rounded-md font-medium transition-colors ${!selectedShift ? 'bg-gray-900 text-white' : 'text-gray-500 hover:bg-gray-100'}`}
              >{t('reservations.allDay')}</button>
              {shifts.filter(s => s.enabled).map(s => (
                <button
                  key={s.id}
                  onClick={() => setSelectedShift(s)}
                  className={`text-xs px-3 py-1.5 rounded-md font-medium transition-colors ${selectedShift?.id === s.id ? 'bg-gray-900 text-white' : 'text-gray-500 hover:bg-gray-100'}`}
                >
                  {s.name}
                  <span className="ml-1 opacity-60">{s.startTime}–{s.endTime}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Stats + view toggle */}
          <div className="flex items-center justify-between mt-3">
            <div className="flex gap-4">
              {[
                [covers, `/ ${selectedShift?.maxCovers ?? '—'} ${t('reservations.statsCovers')}`],
                [seated, t('reservations.statsSeated')],
                [pending, t('reservations.statsPending')],
                [activeWaiting, t('reservations.statsWaiting')],
              ].map(([val, label]) => (
                <div key={String(label)} className="flex items-baseline gap-1">
                  <span className="text-xl font-bold text-gray-900">{val}</span>
                  <span className="text-xs text-gray-400">{label}</span>
                </div>
              ))}
            </div>
            <div className="flex gap-1 bg-gray-100 p-0.5 rounded-md">
              {(['timeline', 'list', 'floor'] as View[]).map(v => (
                <button
                  key={v}
                  onClick={() => setView(v)}
                  className={`text-xs px-3 py-1 rounded transition-colors ${view === v ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'}`}
                >
                  {t(`reservations.view${v.charAt(0).toUpperCase() + v.slice(1)}`)}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* View content */}
        <div className="flex-1 overflow-auto p-4">
          {shifts.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-40 gap-3 text-gray-400">
              <p className="text-sm">{t('reservations.noShifts')}</p>
              <button className="btn-primary text-sm" onClick={() => navigate('/reservations/settings')}>
                {t('reservations.goToSettings')}
              </button>
            </div>
          ) : view === 'timeline' ? (
            <TimelineView
              tables={tables}
              reservations={shiftReservations}
              shift={selectedShift}
              onClickReservation={r => setDetailReservation(r)}
              onClickNew={(tableId, time) => setEditReservation({ id: '', customerName: '', phone: '', partySize: 2, date, time: time ?? '', tableId, status: 'PENDING', createdAt: 0, shiftId: selectedShift?.id, source: 'WALK_IN', guestTags: [], estimatedDurationMinutes: selectedShift?.defaultTurnTimeMinutes ?? 90 })}
            />
          ) : view === 'list' ? (
            <ListView reservations={shiftReservations} tables={tables} onClickReservation={setDetailReservation} onStatusChange={handleStatusChange} />
          ) : (
            <FloorView tables={tables} reservations={shiftReservations} waitlist={waitlist} onClickTable={t => { const r = shiftReservations.find(r => r.tableId === t.id && ['SEATED','CONFIRMED','PENDING'].includes(r.status)); if (r) setDetailReservation(r) }} />
          )}
        </div>
      </div>

      {/* Waitlist sidebar */}
      <WaitlistSidebar
        waitlist={waitlist}
        onNotify={handleWaitlistNotify}
        onSeat={handleWaitlistSeat}
        onCancel={handleWaitlistCancel}
        onAdd={() => setAddWaitlist(true)}
      />

      {/* Modals */}
      {(editReservation === 'new' || (typeof editReservation === 'object' && editReservation !== null && editReservation.id === '')) && (
        <ReservationModal
          reservation={editReservation === 'new' ? undefined : (editReservation as Reservation)}
          shifts={shifts}
          tables={tables}
          reservations={reservations}
          date={date}
          selectedShiftId={selectedShift?.id}
          onClose={() => setEditReservation(null)}
          onSaved={() => { setEditReservation(null); loadReservations() }}
        />
      )}
      {detailReservation && (
        <ReservationDetailModal
          reservation={detailReservation}
          tables={tables}
          onClose={() => setDetailReservation(null)}
          onStatusChange={async (status) => { await handleStatusChange(detailReservation, status); setDetailReservation(null) }}
          onEdit={() => { setEditReservation(detailReservation); setDetailReservation(null) }}
          onDeleted={() => { setDetailReservation(null); loadReservations() }}
        />
      )}
      {editReservation && editReservation !== 'new' && editReservation.id !== '' && !detailReservation && (
        <ReservationModal
          reservation={editReservation}
          shifts={shifts}
          tables={tables}
          reservations={reservations}
          date={date}
          selectedShiftId={selectedShift?.id}
          onClose={() => setEditReservation(null)}
          onSaved={() => { setEditReservation(null); loadReservations() }}
        />
      )}
      {addWaitlist && (
        <WaitlistAddModal
          onClose={() => setAddWaitlist(false)}
          onSaved={() => { setAddWaitlist(false); loadAll() }}
        />
      )}
    </div>
  )
}

// ══════════════════════════════════════════════════════════════════════════════
// Timeline View
// ══════════════════════════════════════════════════════════════════════════════

function TimelineView({ tables, reservations, shift, onClickReservation, onClickNew }: {
  tables: RestaurantTable[]
  reservations: Reservation[]
  shift: Shift | null
  onClickReservation: (r: Reservation) => void
  onClickNew: (tableId: string, time: string) => void
}) {
  const { t } = useTranslation()
  if (!shift) {
    return <div className="text-center text-sm text-gray-400 py-12">{t('reservations.noShiftSelected')}</div>
  }

  const startMin = timeToMinutes(shift.startTime)
  const endMin = timeToMinutes(shift.endTime)
  const totalMin = endMin - startMin

  // Time slot headers every 30 min
  const slotMinutes: number[] = []
  for (let m = startMin; m <= endMin; m += 30) slotMinutes.push(m)

  const now = new Date()
  const nowMin = now.getHours() * 60 + now.getMinutes()
  const nowPct = nowMin >= startMin && nowMin <= endMin ? ((nowMin - startMin) / totalMin) * 100 : null

  function blockStyle(r: Reservation): React.CSSProperties {
    const rStart = timeToMinutes(r.time)
    const dur = r.estimatedDurationMinutes ?? getTurnTime(shift, r.partySize)
    const left = Math.max(0, ((rStart - startMin) / totalMin) * 100)
    const width = Math.max(1, (Math.min(dur, endMin - rStart) / totalMin) * 100)
    return { left: `${left}%`, width: `${width}%` }
  }

  function handleRowClick(e: React.MouseEvent<HTMLDivElement>, tableId: string) {
    const rect = e.currentTarget.getBoundingClientRect()
    const pct = (e.clientX - rect.left) / rect.width
    const clickedMin = Math.round((startMin + pct * totalMin) / 30) * 30
    onClickNew(tableId, minutesToTime(Math.max(startMin, Math.min(endMin - 30, clickedMin))))
  }

  const TABLE_NAME_W = 88

  return (
    <div className="overflow-x-auto rounded-xl border border-gray-100">
      <div style={{ minWidth: 700 }}>
        {/* Time header */}
        <div className="flex border-b border-gray-100 bg-gray-50/50" style={{ paddingLeft: TABLE_NAME_W }}>
          {slotMinutes.map((m, i) => (
            <div key={m} className={`flex-1 text-xs text-gray-400 py-2 text-center border-r border-gray-100 ${i === slotMinutes.length - 1 ? 'border-r-0' : ''}`}>
              {minutesToTime(m)}
            </div>
          ))}
        </div>

        {/* Table rows */}
        {tables.map(table => {
          const tableRsvs = reservations.filter(r => r.tableId === table.id)
          return (
            <div key={table.id} className="flex items-stretch border-b border-gray-50 last:border-b-0 group">
              <div className="shrink-0 px-3 py-2 border-r border-gray-100 bg-gray-50/30 flex flex-col justify-center" style={{ width: TABLE_NAME_W }}>
                <p className="text-xs font-semibold text-gray-700">{table.name}</p>
                <p className="text-xs text-gray-400">{table.capacity}人</p>
              </div>
              <div
                className="relative flex-1 h-14 cursor-crosshair"
                onClick={e => handleRowClick(e, table.id)}
              >
                {/* Grid lines */}
                {slotMinutes.map((m, i) => (
                  <div key={m} className="absolute top-0 bottom-0 border-r border-gray-100" style={{ left: `${((m - startMin) / totalMin) * 100}%`, display: i === slotMinutes.length - 1 ? 'none' : undefined }} />
                ))}
                {/* Now indicator */}
                {nowPct !== null && (
                  <div className="absolute top-0 bottom-0 w-0.5 bg-red-400 z-20 pointer-events-none" style={{ left: `${nowPct}%` }}>
                    <div className="absolute -top-1 left-1/2 -translate-x-1/2 w-2 h-2 bg-red-400 rounded-full" />
                  </div>
                )}
                {/* Reservation blocks */}
                {tableRsvs.map(r => (
                  <div
                    key={r.id}
                    className={`absolute top-1.5 bottom-1.5 rounded-md cursor-pointer overflow-hidden flex items-center gap-1.5 px-2 text-xs font-medium shadow-sm hover:z-10 hover:shadow-md transition-all select-none ${STATUS_BLOCK_COLORS[r.status]}`}
                    style={blockStyle(r)}
                    onClick={e => { e.stopPropagation(); onClickReservation(r) }}
                    title={`${r.customerName} ${r.partySize}人 ${r.time}`}
                  >
                    {r.guestTags && r.guestTags.length > 0 && (
                      <span className="text-xs leading-none shrink-0">{GUEST_TAG_EMOJIS[r.guestTags[0]]}</span>
                    )}
                    <span className="truncate">{r.customerName}</span>
                    <span className="opacity-75 shrink-0">{r.partySize}人</span>
                  </div>
                ))}
              </div>
            </div>
          )
        })}

        {/* Unassigned row */}
        {(() => {
          const unassigned = reservations.filter(r => !r.tableId || !tables.find(t => t.id === r.tableId))
          if (unassigned.length === 0) return null
          return (
            <div className="flex items-stretch border-t border-gray-100 bg-amber-50/30">
              <div className="shrink-0 px-3 py-2 border-r border-gray-100 bg-amber-50/50 flex flex-col justify-center" style={{ width: TABLE_NAME_W }}>
                <p className="text-xs font-semibold text-amber-600">未分桌</p>
              </div>
              <div className="relative flex-1 h-14">
                {unassigned.map(r => (
                  <div
                    key={r.id}
                    className={`absolute top-1.5 bottom-1.5 rounded-md cursor-pointer overflow-hidden flex items-center gap-1.5 px-2 text-xs font-medium shadow-sm hover:z-10 ${STATUS_BLOCK_COLORS[r.status]}`}
                    style={blockStyle(r)}
                    onClick={() => onClickReservation(r)}
                  >
                    {r.guestTags?.length ? <span>{GUEST_TAG_EMOJIS[r.guestTags[0]]}</span> : null}
                    <span className="truncate">{r.customerName}</span>
                    <span className="opacity-75 shrink-0">{r.partySize}人</span>
                  </div>
                ))}
              </div>
            </div>
          )
        })()}
      </div>
    </div>
  )
}

// ══════════════════════════════════════════════════════════════════════════════
// List View
// ══════════════════════════════════════════════════════════════════════════════

function ListView({ reservations, tables, onClickReservation, onStatusChange }: {
  reservations: Reservation[]
  tables: RestaurantTable[]
  onClickReservation: (r: Reservation) => void
  onStatusChange: (r: Reservation, status: Reservation['status']) => void
}) {
  const { t } = useTranslation()
  const sorted = [...reservations].sort((a, b) => a.time.localeCompare(b.time))

  return (
    <div className="card overflow-hidden">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-gray-100 bg-gray-50/50">
            <th className="px-4 py-3 text-start font-medium text-gray-500">{t('reservations.colTime')}</th>
            <th className="px-4 py-3 text-start font-medium text-gray-500">{t('reservations.colName')}</th>
            <th className="px-4 py-3 text-center font-medium text-gray-500">{t('reservations.colParty')}</th>
            <th className="px-4 py-3 text-start font-medium text-gray-500">{t('reservations.colTable')}</th>
            <th className="px-4 py-3 text-start font-medium text-gray-500">{t('reservations.colSource')}</th>
            <th className="px-4 py-3 text-center font-medium text-gray-500">{t('reservations.colStatus')}</th>
            <th className="px-4 py-3 text-start font-medium text-gray-500">{t('reservations.colNotes')}</th>
            <th className="px-4 py-3 text-end font-medium text-gray-500">{t('menu.colActions')}</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-50">
          {sorted.length === 0 && (
            <tr><td colSpan={8} className="px-4 py-10 text-center text-gray-400 text-sm">{t('reservations.timelineEmpty')}</td></tr>
          )}
          {sorted.map(r => {
            const table = tables.find(t => t.id === r.tableId)
            return (
              <tr key={r.id} className="hover:bg-gray-50/50 cursor-pointer" onClick={() => onClickReservation(r)}>
                <td className="px-4 py-3 font-mono font-semibold text-gray-900">{r.time}</td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-1.5">
                    <span className="font-medium text-gray-900">{r.customerName}</span>
                    {r.guestTags?.map(tag => (
                      <span key={tag} title={tag} className="text-sm">{GUEST_TAG_EMOJIS[tag as GuestTag]}</span>
                    ))}
                  </div>
                  <p className="text-xs text-gray-400 font-mono">{r.phone}</p>
                </td>
                <td className="px-4 py-3 text-center text-gray-600">{r.partySize}人</td>
                <td className="px-4 py-3 text-gray-600">{table?.name || <span className="text-amber-500 text-xs">未分桌</span>}</td>
                <td className="px-4 py-3 text-gray-500 text-xs">{SOURCE_LABELS[r.source ?? ''] ?? '—'}</td>
                <td className="px-4 py-3 text-center">
                  <span className={`badge ${STATUS_BADGE_COLORS[r.status]}`}>{t(`reservations.status${r.status.charAt(0) + r.status.slice(1).toLowerCase()}`)}</span>
                </td>
                <td className="px-4 py-3 text-xs text-gray-400 max-w-32 truncate">{r.notes || r.internalNotes || '—'}</td>
                <td className="px-4 py-3 text-right" onClick={e => e.stopPropagation()}>
                  <div className="flex justify-end gap-1.5 flex-wrap">
                    {r.status === 'PENDING' && <button className="text-xs btn-primary py-0.5 px-2" onClick={() => onStatusChange(r, 'CONFIRMED')}>{t('reservations.confirm')}</button>}
                    {r.status === 'CONFIRMED' && <button className="text-xs bg-emerald-500 text-white px-2 py-0.5 rounded-md hover:bg-emerald-600 text-xs" onClick={() => onStatusChange(r, 'SEATED')}>{t('reservations.seat')}</button>}
                    {r.status === 'SEATED' && <button className="text-xs bg-gray-600 text-white px-2 py-0.5 rounded-md hover:bg-gray-700 text-xs" onClick={() => onStatusChange(r, 'COMPLETED')}>{t('reservations.complete')}</button>}
                    {['PENDING', 'CONFIRMED'].includes(r.status) && <button className="text-xs text-gray-400 hover:text-gray-600" onClick={() => onStatusChange(r, 'CANCELLED')}>{t('reservations.cancel')}</button>}
                    {['PENDING', 'CONFIRMED'].includes(r.status) && <button className="text-xs text-red-400 hover:text-red-600" onClick={() => onStatusChange(r, 'NO_SHOW')}>{t('reservations.noShow')}</button>}
                  </div>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

// ══════════════════════════════════════════════════════════════════════════════
// Floor View
// ══════════════════════════════════════════════════════════════════════════════

const STATUS_COLORS_FLOOR: Record<string, { bg: string; border: string; text: string; dot: string }> = {
  SEATED:    { bg: '#ecfdf5', border: '#34d399', text: '#065f46', dot: '#10b981' },
  NOTIFIED:  { bg: '#fffbeb', border: '#fbbf24', text: '#92400e', dot: '#f59e0b' },
  INCOMING:  { bg: '#eff6ff', border: '#60a5fa', text: '#1e40af', dot: '#3b82f6' },
  RESERVED:  { bg: '#eef2ff', border: '#818cf8', text: '#3730a3', dot: '#6366f1' },
  AVAILABLE: { bg: '#f9fafb', border: '#e5e7eb', text: '#6b7280', dot: '#d1d5db' },
}

const STATUS_LABELS_FLOOR: Record<string, string> = {
  SEATED: '就坐中', NOTIFIED: '叫号中', INCOMING: '即将到来', RESERVED: '已预约', AVAILABLE: '空闲',
}

function floorTableStatus(table: RestaurantTable, reservations: Reservation[], waitlist: WaitlistEntry[]) {
  const nowMin = new Date().getHours() * 60 + new Date().getMinutes()
  if (reservations.some(r => r.tableId === table.id && r.status === 'SEATED')) return 'SEATED'
  if (waitlist.some(w => w.tableId === table.id && w.status === 'NOTIFIED')) return 'NOTIFIED'
  if (reservations.some(r => {
    if (r.tableId !== table.id || !['CONFIRMED', 'PENDING'].includes(r.status)) return false
    const rMin = timeToMinutes(r.time)
    return rMin - nowMin <= 45 && rMin >= nowMin
  })) return 'INCOMING'
  if (reservations.some(r => r.tableId === table.id && ['CONFIRMED', 'PENDING'].includes(r.status))) return 'RESERVED'
  return 'AVAILABLE'
}

function FloorView({ tables, reservations, waitlist, onClickTable }: {
  tables: RestaurantTable[]
  reservations: Reservation[]
  waitlist: WaitlistEntry[]
  onClickTable: (t: RestaurantTable) => void
}) {
  const sections = [...new Set(tables.map(t => t.section))]
  const [activeSection, setActiveSection] = useState<string>('')
  const effectiveSection = sections.includes(activeSection) ? activeSection : (sections[0] ?? '')

  // tables with layout coords use spatial view; fallback to grid
  const hasSpatial = tables.some(t => t.x != null && t.y != null)

  return (
    <div className="space-y-4">
      {/* Legend */}
      <div className="flex items-center gap-4 flex-wrap">
        <div className="flex flex-wrap gap-3 text-xs text-gray-500 flex-1">
          {Object.entries(STATUS_LABELS_FLOOR).map(([k, v]) => (
            <div key={k} className="flex items-center gap-1.5">
              <div className="w-2.5 h-2.5 rounded-full" style={{ background: STATUS_COLORS_FLOOR[k].dot }} />
              {v}
            </div>
          ))}
        </div>
        {/* Section tabs */}
        {sections.length > 1 && (
          <div className="flex gap-1">
            {sections.map(s => (
              <button
                key={s}
                onClick={() => setActiveSection(s)}
                className={`px-3 py-1 text-xs rounded-full font-medium transition-colors ${
                  effectiveSection === s ? 'bg-gray-900 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}
              >{s}</button>
            ))}
          </div>
        )}
      </div>

      {hasSpatial ? (
        <FloorCanvas
          tables={tables.filter(t => sections.length <= 1 || t.section === effectiveSection)}
          reservations={reservations}
          waitlist={waitlist}
          onClickTable={onClickTable}
        />
      ) : (
        // Fallback grid when no layout coordinates
        <div className="grid grid-cols-3 sm:grid-cols-4 lg:grid-cols-6 gap-3">
          {tables.filter(t => sections.length <= 1 || t.section === effectiveSection).map(table => {
            const status = floorTableStatus(table, reservations, waitlist)
            const c = STATUS_COLORS_FLOOR[status]
            const activeR = reservations.find(r => r.tableId === table.id && ['SEATED', 'CONFIRMED', 'PENDING'].includes(r.status))
            return (
              <div key={table.id} onClick={() => onClickTable(table)} className="border-2 rounded-xl p-3 cursor-pointer hover:shadow-sm transition-all" style={{ background: c.bg, borderColor: c.border, color: c.text }}>
                <div className="flex items-center justify-between mb-1">
                  <span className="font-bold text-sm">{table.name}</span>
                  <span className="text-xs opacity-60">{table.capacity}人</span>
                </div>
                {activeR ? <p className="text-xs font-medium truncate">{activeR.customerName}</p> : <p className="text-xs opacity-50">{STATUS_LABELS_FLOOR[status]}</p>}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

function FloorCanvas({ tables, reservations, waitlist, onClickTable }: {
  tables: RestaurantTable[]
  reservations: Reservation[]
  waitlist: WaitlistEntry[]
  onClickTable: (t: RestaurantTable) => void
}) {
  const SCALE = 0.72 // px per mock-unit
  const PAD = 24

  const maxX = Math.max(...tables.map(t => (t.x ?? 0) + (t.w ?? 120))) + PAD
  const maxY = Math.max(...tables.map(t => (t.y ?? 0) + (t.h ?? 120))) + PAD

  return (
    <div
      className="relative rounded-2xl border border-gray-100 overflow-auto bg-gray-50/60"
      style={{ minHeight: 320 }}
    >
      <div
        className="relative"
        style={{ width: maxX * SCALE + PAD, height: maxY * SCALE + PAD }}
      >
        {tables.map(table => {
          const x = (table.x ?? 0) * SCALE + PAD / 2
          const y = (table.y ?? 0) * SCALE + PAD / 2
          const w = (table.w ?? 120) * SCALE
          const h = (table.h ?? 120) * SCALE
          const isRound = table.shape === 'round'
          const status = floorTableStatus(table, reservations, waitlist)
          const c = STATUS_COLORS_FLOOR[status]
          const seatedR = reservations.find(r => r.tableId === table.id && r.status === 'SEATED')
          const nextR = reservations.filter(r => r.tableId === table.id && ['CONFIRMED', 'PENDING'].includes(r.status)).sort((a, b) => a.time.localeCompare(b.time))[0]
          const activeR = seatedR ?? nextR

          return (
            <div
              key={table.id}
              onClick={() => onClickTable(table)}
              className="absolute cursor-pointer transition-all duration-150 hover:scale-105 hover:z-10 flex flex-col items-center justify-center overflow-hidden select-none"
              style={{
                left: x, top: y, width: w, height: h,
                borderRadius: isRound ? '50%' : 12,
                background: c.bg,
                border: `2px solid ${c.border}`,
                color: c.text,
                boxShadow: status === 'SEATED' ? `0 0 0 3px ${c.border}40` : undefined,
              }}
            >
              <div className="w-2 h-2 rounded-full mb-0.5" style={{ background: c.dot }} />
              <span className="font-bold text-center leading-tight" style={{ fontSize: Math.max(10, Math.min(13, w / 9)) }}>{table.name}</span>
              {activeR && (
                <span className="text-center leading-tight truncate px-1" style={{ fontSize: Math.max(9, Math.min(11, w / 11)), maxWidth: w - 8 }}>
                  {activeR.partySize}人{seatedR ? '' : ` · ${activeR.time}`}
                </span>
              )}
              {!activeR && (
                <span className="opacity-40 text-center" style={{ fontSize: Math.max(9, Math.min(10, w / 12)) }}>{table.capacity}人</span>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}

// ══════════════════════════════════════════════════════════════════════════════
// Waitlist Sidebar
// ══════════════════════════════════════════════════════════════════════════════

function WaitlistSidebar({ waitlist, onNotify, onSeat, onCancel, onAdd }: {
  waitlist: WaitlistEntry[]
  onNotify: (w: WaitlistEntry) => void
  onSeat: (w: WaitlistEntry) => void
  onCancel: (w: WaitlistEntry) => void
  onAdd: () => void
}) {
  const { t } = useTranslation()
  const PREF_LABELS: Record<string, string> = { WINDOW: '靠窗', INDOOR: '室内', OUTDOOR: '室外', QUIET: '安静' }

  return (
    <div className="w-72 shrink-0 border-l border-gray-100 bg-gray-50/40 flex flex-col h-full overflow-hidden">
      <div className="px-4 py-4 border-b border-gray-100 bg-white flex items-center justify-between">
        <div>
          <p className="text-sm font-semibold text-gray-900">{t('reservations.waitlistTitle')}</p>
          <p className="text-xs text-gray-400">{waitlist.filter(w => w.status === 'WAITING').length} 组等位中</p>
        </div>
        <button className="btn-primary text-xs py-1.5 px-3" onClick={onAdd}>
          + {t('reservations.waitlistAdd')}
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-3 space-y-2">
        {waitlist.length === 0 && (
          <p className="text-center text-xs text-gray-400 py-8">{t('reservations.waitlistEmpty')}</p>
        )}

        {[...waitlist].sort((a, b) => a.requestedAt - b.requestedAt).map((w, idx) => (
          <div key={w.id} className={`bg-white rounded-xl border p-3 shadow-sm ${w.status === 'NOTIFIED' ? 'border-amber-300' : 'border-gray-100'}`}>
            <div className="flex items-start justify-between mb-2">
              <div className="flex items-center gap-2">
                <span className="w-5 h-5 rounded-full bg-gray-200 text-gray-600 text-xs flex items-center justify-center font-bold shrink-0">{idx + 1}</span>
                <div>
                  <p className="text-sm font-semibold text-gray-900">{w.customerName}</p>
                  <p className="text-xs text-gray-400 font-mono">{w.phone}</p>
                </div>
              </div>
              <div className="text-right">
                <span className="text-sm font-bold text-gray-800">{w.partySize}人</span>
              </div>
            </div>

            {w.preferences.length > 0 && (
              <div className="flex flex-wrap gap-1 mb-2">
                {w.preferences.map(p => (
                  <span key={p} className="text-xs bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded">{PREF_LABELS[p] ?? p}</span>
                ))}
              </div>
            )}

            {w.status === 'NOTIFIED' ? (
              <div className="bg-amber-50 border border-amber-200 rounded-lg px-2.5 py-1.5 mb-2 flex items-center justify-between">
                <span className="text-xs font-medium text-amber-700">⏰ 已叫号</span>
                {w.expiresAt && (
                  <span className="text-xs font-mono text-amber-600">{fmtCountdown(w.expiresAt)}</span>
                )}
              </div>
            ) : (
              <div className="text-xs text-gray-500 mb-2 flex justify-between">
                <span>{t('reservations.waitlistWaitTime')} {fmtElapsed(w.requestedAt)}</span>
                <span>{t('reservations.waitlistEstimated')} ~{w.quotedWaitMinutes}分钟</span>
              </div>
            )}

            {w.notes && <p className="text-xs text-gray-400 mb-2 italic">"{w.notes}"</p>}

            <div className="flex gap-1.5">
              {w.status === 'WAITING' && (
                <button className="flex-1 text-xs bg-brand-600 text-white px-2 py-1.5 rounded-md hover:bg-brand-700 font-medium" onClick={() => onNotify(w)}>
                  {t('reservations.waitlistNotify')}
                </button>
              )}
              {w.status === 'NOTIFIED' && (
                <button className="flex-1 text-xs bg-emerald-600 text-white px-2 py-1.5 rounded-md hover:bg-emerald-700 font-medium" onClick={() => onSeat(w)}>
                  {t('reservations.waitlistSeat')}
                </button>
              )}
              <button className="text-xs text-gray-400 hover:text-gray-600 px-2 py-1.5" onClick={() => onCancel(w)}>
                {t('reservations.waitlistCancel')}
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

// ══════════════════════════════════════════════════════════════════════════════
// Reservation Detail Modal
// ══════════════════════════════════════════════════════════════════════════════

function ReservationDetailModal({ reservation: r, tables, onClose, onStatusChange, onEdit, onDeleted }: {
  reservation: Reservation
  tables: RestaurantTable[]
  onClose: () => void
  onStatusChange: (status: Reservation['status']) => void
  onEdit: () => void
  onDeleted: () => void
}) {
  const { t } = useTranslation()
  const table = tables.find(t => t.id === r.tableId)

  async function handleDelete() {
    if (!confirm(t('reservations.confirmDeleteReservation'))) return
    await reservationApi.delete(r.id)
    onDeleted()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/30 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-md max-h-[92vh] overflow-y-auto">
        <div className="flex items-start justify-between px-6 py-4 border-b border-gray-100">
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-base font-semibold text-gray-900">{r.customerName}</h2>
              {r.guestTags?.map(tag => <span key={tag} className="text-base">{GUEST_TAG_EMOJIS[tag as GuestTag]}</span>)}
              <span className={`badge ${STATUS_BADGE_COLORS[r.status]}`}>{r.status === 'PENDING' ? t('reservations.statusPending') : r.status === 'CONFIRMED' ? t('reservations.statusConfirmed') : r.status === 'SEATED' ? t('reservations.statusSeated') : r.status === 'COMPLETED' ? t('reservations.statusCompleted') : r.status === 'CANCELLED' ? t('reservations.statusCancelled') : t('reservations.statusNoShow')}</span>
            </div>
            <p className="text-sm text-gray-500 font-mono mt-0.5">{r.phone}</p>
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 p-1 rounded-lg hover:bg-gray-100 mt-0.5">
            <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" /></svg>
          </button>
        </div>

        <div className="px-6 py-4 space-y-3">
          <div className="grid grid-cols-3 gap-3 text-center">
            {[
              [r.date, '日期'],
              [r.time, '时间'],
              [`${r.partySize}人`, '人数'],
            ].map(([v, l]) => (
              <div key={l} className="bg-gray-50 rounded-xl p-2.5">
                <p className="font-mono font-semibold text-gray-900 text-sm">{v}</p>
                <p className="text-xs text-gray-400">{l}</p>
              </div>
            ))}
          </div>

          <div className="grid grid-cols-2 gap-2 text-sm">
            {table && <div className="flex justify-between"><span className="text-gray-500">桌台</span><span className="font-medium">{table.name} ({table.capacity}人桌)</span></div>}
            {r.source && <div className="flex justify-between"><span className="text-gray-500">来源</span><span>{SOURCE_LABELS[r.source]}</span></div>}
            {r.confirmationCode && <div className="flex justify-between"><span className="text-gray-500">确认码</span><span className="font-mono text-xs bg-gray-100 px-2 py-0.5 rounded">{r.confirmationCode}</span></div>}
            {r.estimatedDurationMinutes && <div className="flex justify-between"><span className="text-gray-500">预计时长</span><span>{r.estimatedDurationMinutes}分钟</span></div>}
          </div>

          {r.notes && (
            <div className="bg-blue-50 rounded-lg px-3 py-2">
              <p className="text-xs text-blue-600 font-medium mb-0.5">顾客要求</p>
              <p className="text-sm text-blue-800">{r.notes}</p>
            </div>
          )}
          {r.internalNotes && (
            <div className="bg-amber-50 rounded-lg px-3 py-2">
              <p className="text-xs text-amber-600 font-medium mb-0.5">内部备注</p>
              <p className="text-sm text-amber-800">{r.internalNotes}</p>
            </div>
          )}

          {/* Action buttons */}
          <div className="flex flex-wrap gap-2 pt-1">
            {r.status === 'PENDING' && <button className="btn-primary flex-1 text-sm" onClick={() => onStatusChange('CONFIRMED')}>{t('reservations.confirm')}</button>}
            {r.status === 'CONFIRMED' && <button className="bg-emerald-600 text-white rounded-xl px-4 py-2 text-sm font-medium hover:bg-emerald-700 flex-1" onClick={() => onStatusChange('SEATED')}>{t('reservations.seat')}</button>}
            {r.status === 'SEATED' && <button className="bg-gray-700 text-white rounded-xl px-4 py-2 text-sm font-medium hover:bg-gray-800 flex-1" onClick={() => onStatusChange('COMPLETED')}>{t('reservations.complete')}</button>}
            {['PENDING', 'CONFIRMED', 'SEATED'].includes(r.status) && (
              <button className="btn-secondary text-sm px-3" onClick={onEdit}>{t('menu.edit')}</button>
            )}
          </div>
          {['PENDING', 'CONFIRMED'].includes(r.status) && (
            <div className="flex gap-2">
              <button className="flex-1 text-xs text-red-400 hover:text-red-600 py-1" onClick={() => onStatusChange('CANCELLED')}>{t('reservations.cancel')}</button>
              <button className="flex-1 text-xs text-gray-400 hover:text-gray-600 py-1" onClick={() => onStatusChange('NO_SHOW')}>{t('reservations.noShow')}</button>
              <button className="flex-1 text-xs text-red-300 hover:text-red-500 py-1" onClick={handleDelete}>{t('menu.delete')}</button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

// ══════════════════════════════════════════════════════════════════════════════
// Reservation Form Modal (New / Edit)
// ══════════════════════════════════════════════════════════════════════════════

function ReservationModal({ reservation, shifts, tables, reservations, date, selectedShiftId, onClose, onSaved }: {
  reservation?: Reservation
  shifts: Shift[]
  tables: RestaurantTable[]
  reservations: Reservation[]
  date: string
  selectedShiftId?: string
  onClose: () => void
  onSaved: () => void
}) {
  const { t } = useTranslation()
  const isEdit = Boolean(reservation?.id)
  const [name, setName] = useState(reservation?.customerName ?? '')
  const [phone, setPhone] = useState(reservation?.phone ?? '')
  const [partySize, setPartySize] = useState(reservation?.partySize ?? 2)
  const [rDate, setRDate] = useState(reservation?.date ?? date)
  const [shiftId, setShiftId] = useState(reservation?.shiftId ?? selectedShiftId ?? shifts[0]?.id ?? '')
  const [time, setTime] = useState(reservation?.time ?? '')
  const [tableId, setTableId] = useState(reservation?.tableId ?? '')
  const [source, setSource] = useState<string>(reservation?.source ?? 'ONLINE')
  const [guestTags, setGuestTags] = useState<GuestTag[]>(reservation?.guestTags ?? [])
  const [notes, setNotes] = useState(reservation?.notes ?? '')
  const [internalNotes, setInternalNotes] = useState(reservation?.internalNotes ?? '')
  const [depositAmount, setDepositAmount] = useState(String(reservation?.depositAmount ?? ''))
  const [depositPaid, setDepositPaid] = useState(reservation?.depositPaid ?? false)
  const [duration, setDuration] = useState(String(reservation?.estimatedDurationMinutes ?? ''))
  const [availability, setAvailability] = useState<SlotAvailability[]>([])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const shift = shifts.find(s => s.id === shiftId) ?? null

  useEffect(() => {
    if (shiftId && rDate) {
      shiftApi.getAvailability(shiftId, rDate).then(setAvailability).catch(() => setAvailability([]))
    }
  }, [shiftId, rDate])

  // Auto duration from shift turn time
  useEffect(() => {
    if (!duration) setDuration(String(getTurnTime(shift, partySize)))
  }, [partySize, shiftId])

  function isTableAvailable(tid: string): boolean {
    if (!tid) return true
    const dur = parseInt(duration) || 90
    const reqStart = timeToMinutes(time)
    const reqEnd = reqStart + dur
    return !reservations.some(r =>
      r.id !== reservation?.id && r.tableId === tid && r.date === rDate &&
      ['PENDING', 'CONFIRMED', 'SEATED'].includes(r.status) &&
      timeToMinutes(r.time) < reqEnd &&
      timeToMinutes(r.time) + (r.estimatedDurationMinutes ?? 90) > reqStart
    )
  }

  function toggleTag(tag: GuestTag) {
    setGuestTags(prev => prev.includes(tag) ? prev.filter(t => t !== tag) : [...prev, tag])
  }

  async function handleSave() {
    if (!name.trim()) { setError('请输入客人姓名'); return }
    if (!phone.trim()) { setError('请输入手机号'); return }
    if (!time) { setError('请选择预约时间'); return }
    setSaving(true); setError('')
    try {
      const payload = {
        customerName: name.trim(), phone: phone.trim(), partySize,
        date: rDate, time, shiftId, tableId: tableId || undefined,
        source: source as Reservation['source'], guestTags, notes: notes.trim() || undefined,
        internalNotes: internalNotes.trim() || undefined, status: reservation?.status ?? 'PENDING' as Reservation['status'],
        estimatedDurationMinutes: parseInt(duration) || undefined,
        depositAmount: depositAmount ? parseInt(depositAmount) : undefined,
        depositPaid: depositAmount ? depositPaid : undefined,
      }
      if (isEdit && reservation) await reservationApi.update(reservation.id, payload)
      else await reservationApi.create(payload)
      onSaved()
    } catch { setError(t('menu.saveFailed')) }
    finally { setSaving(false) }
  }

  const availableTables = tables.filter(t => {
    if (partySize > t.capacity) return false
    if (t.minCapacity && partySize < t.minCapacity) return false
    return true
  })

  const slotFillColor = (slot: SlotAvailability) => {
    const ratio = slot.capacity > 0 ? slot.covers / slot.capacity : 0
    if (!slot.available) return 'bg-gray-100 text-gray-400 cursor-not-allowed opacity-50'
    if (ratio >= 0.8) return 'border border-red-200 bg-red-50 text-red-700'
    if (ratio >= 0.5) return 'border border-amber-200 bg-amber-50 text-amber-700'
    return 'border border-emerald-200 bg-emerald-50 text-emerald-700'
  }

  const ALL_TAGS: GuestTag[] = ['BIRTHDAY', 'ANNIVERSARY', 'VIP', 'FIRST_TIME']
  const TAG_LABELS: Record<GuestTag, string> = { BIRTHDAY: '🎂 生日', ANNIVERSARY: '💍 纪念日', VIP: '⭐ VIP', FIRST_TIME: '🆕 初次到访' }
  const SOURCE_OPTS: [string, string][] = [['ONLINE', '在线'], ['PHONE', '电话'], ['WALK_IN', '到店'], ['CONCIERGE', '礼宾']]

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/30 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-lg max-h-[92vh] overflow-y-auto">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h2 className="text-base font-semibold text-gray-900">{isEdit ? t('reservations.editTitle') : t('reservations.addTitle')}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 p-1 rounded-lg hover:bg-gray-100">
            <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" /></svg>
          </button>
        </div>
        <div className="px-6 py-5 space-y-4">
          {/* Guest */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="field-label">姓名 *</label>
              <input className="input w-full" value={name} onChange={e => setName(e.target.value)} autoFocus placeholder="张先生 / 李女士" />
            </div>
            <div>
              <label className="field-label">手机号 *</label>
              <input className="input w-full font-mono" value={phone} onChange={e => setPhone(e.target.value)} />
            </div>
          </div>

          {/* Date + Shift + Party */}
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="field-label">{t('reservations.fieldDate')}</label>
              <input type="date" className="input w-full font-mono text-sm" value={rDate} onChange={e => setRDate(e.target.value)} />
            </div>
            <div>
              <label className="field-label">{t('reservations.fieldShift')}</label>
              <select className="input w-full text-sm" value={shiftId} onChange={e => { setShiftId(e.target.value); setTime('') }}>
                {shifts.filter(s => s.enabled).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </div>
            <div>
              <label className="field-label">{t('reservations.fieldParty')}</label>
              <input type="number" min={1} max={20} className="input w-full font-mono" value={partySize} onChange={e => setPartySize(parseInt(e.target.value) || 2)} />
            </div>
          </div>

          {/* Slot picker */}
          {availability.length > 0 && (
            <div>
              <label className="field-label">{t('reservations.fieldTime')} *</label>
              <div className="flex flex-wrap gap-1.5">
                {availability.map(slot => (
                  <button
                    key={slot.time}
                    type="button"
                    disabled={!slot.available}
                    onClick={() => setTime(slot.time)}
                    className={`text-xs px-2.5 py-1.5 rounded-md font-mono font-medium transition-colors ${time === slot.time ? 'ring-2 ring-brand-500 bg-brand-500 text-white' : slotFillColor(slot)}`}
                  >
                    {slot.time}
                    {slot.covers > 0 && <span className="ml-1 opacity-70">{slot.covers}/{slot.capacity}</span>}
                  </button>
                ))}
              </div>
              {!time && <p className="text-xs text-gray-400 mt-1">点击时间段选择</p>}
            </div>
          )}

          {/* Table picker */}
          {time && (
            <div>
              <label className="field-label">{t('reservations.fieldTable')}</label>
              <div className="flex flex-wrap gap-1.5">
                <button
                  type="button"
                  onClick={() => setTableId('')}
                  className={`text-xs px-2.5 py-1.5 rounded-md border font-medium transition-colors ${!tableId ? 'bg-gray-900 text-white border-gray-900' : 'border-gray-200 text-gray-500 hover:border-gray-400'}`}
                >
                  未分桌
                </button>
                {availableTables.map(table => {
                  const avail = isTableAvailable(table.id)
                  return (
                    <button
                      key={table.id}
                      type="button"
                      disabled={!avail}
                      onClick={() => avail && setTableId(table.id)}
                      className={`text-xs px-2.5 py-1.5 rounded-md border font-medium transition-colors ${!avail ? 'border-gray-100 text-gray-300 cursor-not-allowed opacity-50' : tableId === table.id ? 'bg-gray-900 text-white border-gray-900' : 'border-gray-200 text-gray-600 hover:border-gray-400'}`}
                    >
                      {table.name} <span className="opacity-60">{table.capacity}人</span>
                    </button>
                  )
                })}
              </div>
            </div>
          )}

          {/* Source + Tags */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="field-label">{t('reservations.fieldSource')}</label>
              <select className="input w-full text-sm" value={source} onChange={e => setSource(e.target.value)}>
                {SOURCE_OPTS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
              </select>
            </div>
            <div>
              <label className="field-label">{t('reservations.fieldGuestTags')}</label>
              <div className="flex flex-wrap gap-1">
                {ALL_TAGS.map(tag => (
                  <button
                    key={tag}
                    type="button"
                    onClick={() => toggleTag(tag)}
                    className={`text-xs px-2 py-1 rounded-md transition-colors ${guestTags.includes(tag) ? 'bg-brand-100 text-brand-700 font-medium' : 'bg-gray-100 text-gray-500 hover:bg-gray-200'}`}
                  >
                    {TAG_LABELS[tag]}
                  </button>
                ))}
              </div>
            </div>
          </div>

          {/* Notes */}
          <div>
            <label className="field-label">{t('reservations.fieldNotes')}</label>
            <input className="input w-full text-sm" value={notes} onChange={e => setNotes(e.target.value)} placeholder="过敏、特殊要求…（显示给后厨）" />
          </div>
          <div>
            <label className="field-label">{t('reservations.fieldInternalNotes')}</label>
            <input className="input w-full text-sm" value={internalNotes} onChange={e => setInternalNotes(e.target.value)} placeholder="仅员工可见" />
          </div>

          {/* Duration + Deposit */}
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="field-label">{t('reservations.fieldDuration')}</label>
              <input type="number" className="input w-full font-mono" value={duration} onChange={e => setDuration(e.target.value)} placeholder="90" />
            </div>
            <div>
              <label className="field-label">{t('reservations.fieldDeposit')}</label>
              <input type="number" className="input w-full font-mono" value={depositAmount} onChange={e => setDepositAmount(e.target.value)} placeholder="0" />
            </div>
            <div className="flex items-end pb-2">
              <label className="flex items-center gap-1.5 text-xs text-gray-600 cursor-pointer">
                <input type="checkbox" checked={depositPaid} onChange={e => setDepositPaid(e.target.checked)} />
                已收定金
              </label>
            </div>
          </div>

          {error && <p className="text-sm text-red-600">{error}</p>}
          <div className="flex gap-3 pt-1">
            <button className="btn-secondary flex-1" onClick={onClose}>{t('menu.cancel')}</button>
            <button className="btn-primary flex-1" onClick={handleSave} disabled={saving}>{saving ? t('menu.saving') : t('menu.save')}</button>
          </div>
        </div>
      </div>
    </div>
  )
}

// ══════════════════════════════════════════════════════════════════════════════
// Waitlist Add Modal
// ══════════════════════════════════════════════════════════════════════════════

function WaitlistAddModal({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const { t } = useTranslation()
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [partySize, setPartySize] = useState(2)
  const [quoted, setQuoted] = useState('20')
  const [prefs, setPrefs] = useState<string[]>([])
  const [notes, setNotes] = useState('')
  const [saving, setSaving] = useState(false)

  const PREFS = [['WINDOW', '靠窗'], ['INDOOR', '室内'], ['OUTDOOR', '室外'], ['QUIET', '安静']]

  function togglePref(p: string) { setPrefs(prev => prev.includes(p) ? prev.filter(x => x !== p) : [...prev, p]) }

  async function handleSave() {
    if (!name.trim() || !phone.trim()) return
    setSaving(true)
    await waitlistApi.create({
      customerName: name.trim(), phone: phone.trim(), partySize,
      quotedWaitMinutes: parseInt(quoted) || 20,
      preferences: prefs, notes: notes.trim() || undefined,
      status: 'WAITING', requestedAt: Date.now(),
    })
    onSaved()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/30 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-sm max-h-[92vh] overflow-y-auto">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h2 className="text-base font-semibold text-gray-900">{t('reservations.waitlistAddTitle')}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 p-1 rounded-lg hover:bg-gray-100">
            <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor"><path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" /></svg>
          </button>
        </div>
        <div className="px-6 py-5 space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="field-label">{t('reservations.waitlistFieldName')} *</label>
              <input className="input w-full" value={name} onChange={e => setName(e.target.value)} autoFocus />
            </div>
            <div>
              <label className="field-label">{t('reservations.waitlistFieldPhone')}</label>
              <input className="input w-full font-mono" value={phone} onChange={e => setPhone(e.target.value)} />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="field-label">{t('reservations.waitlistFieldParty')}</label>
              <input type="number" min={1} max={20} className="input w-full font-mono" value={partySize} onChange={e => setPartySize(parseInt(e.target.value) || 2)} />
            </div>
            <div>
              <label className="field-label">{t('reservations.waitlistFieldQuoted')}</label>
              <input type="number" className="input w-full font-mono" value={quoted} onChange={e => setQuoted(e.target.value)} />
            </div>
          </div>
          <div>
            <label className="field-label">{t('reservations.waitlistFieldPrefs')}</label>
            <div className="flex gap-2">
              {PREFS.map(([v, l]) => (
                <button key={v} type="button" onClick={() => togglePref(v)}
                  className={`text-xs px-2.5 py-1.5 rounded-md border transition-colors ${prefs.includes(v) ? 'border-brand-400 bg-brand-50 text-brand-700' : 'border-gray-200 text-gray-500 hover:border-gray-400'}`}>
                  {l}
                </button>
              ))}
            </div>
          </div>
          <div>
            <label className="field-label">{t('reservations.waitlistFieldNotes')}</label>
            <input className="input w-full text-sm" value={notes} onChange={e => setNotes(e.target.value)} placeholder="如：带小孩、宝宝椅…" />
          </div>
          <div className="flex gap-3 pt-1">
            <button className="btn-secondary flex-1" onClick={onClose}>{t('menu.cancel')}</button>
            <button className="btn-primary flex-1" onClick={handleSave} disabled={saving || !name || !phone}>{saving ? t('menu.saving') : '加入候位'}</button>
          </div>
        </div>
      </div>
    </div>
  )
}
