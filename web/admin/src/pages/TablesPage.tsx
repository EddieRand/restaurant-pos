import { useEffect, useState, useRef, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { sectionApi, tableApi, type Section, type TableConfig } from '../api/admin'

// ── Virtual canvas coordinate system ──────────────────────────────────────────
// All x/y/w/h values are stored in virtual px (1200×675).
// The canvas DOM element is scaled to fill available space.

const VW = 1200
const VH = 675
const SNAP = 20
const MIN_SIZE = 60

function snap(v: number) {
  return Math.round(v / SNAP) * SNAP
}

// ── Resize handle positions ───────────────────────────────────────────────────

type Handle = 'nw' | 'ne' | 'sw' | 'se' | 'n' | 's' | 'e' | 'w'

const HANDLES: { id: Handle; cursor: string; style: React.CSSProperties }[] = [
  { id: 'nw', cursor: 'nwse-resize', style: { top: -5, left: -5 } },
  { id: 'ne', cursor: 'nesw-resize', style: { top: -5, right: -5 } },
  { id: 'sw', cursor: 'nesw-resize', style: { bottom: -5, left: -5 } },
  { id: 'se', cursor: 'nwse-resize', style: { bottom: -5, right: -5 } },
  { id: 'n',  cursor: 'ns-resize',   style: { top: -5, left: '50%', transform: 'translateX(-50%)' } },
  { id: 's',  cursor: 'ns-resize',   style: { bottom: -5, left: '50%', transform: 'translateX(-50%)' } },
  { id: 'e',  cursor: 'ew-resize',   style: { right: -5, top: '50%', transform: 'translateY(-50%)' } },
  { id: 'w',  cursor: 'ew-resize',   style: { left: -5, top: '50%', transform: 'translateY(-50%)' } },
]

// ── Table card ────────────────────────────────────────────────────────────────

function TableCard({
  table, selected,
  onMouseDown, onResizeMouseDown,
}: {
  table: TableConfig
  selected: boolean
  onMouseDown: (e: React.MouseEvent) => void
  onResizeMouseDown: (e: React.MouseEvent, handle: Handle) => void
}) {
  const isRound = table.shape === 'round'

  return (
    <div
      style={{
        position: 'absolute',
        left: table.x,
        top: table.y,
        width: table.w,
        height: table.h,
        userSelect: 'none',
        cursor: 'grab',
        touchAction: 'none',
        zIndex: selected ? 10 : 1,
      }}
      onMouseDown={onMouseDown}
      onClick={e => e.stopPropagation()}
    >
      {/* Table body */}
      <div
        className={`w-full h-full flex flex-col items-center justify-center gap-0.5
          ${isRound ? 'rounded-full' : 'rounded-xl'}
          ${selected
            ? 'bg-brand-50 shadow-lg'
            : 'bg-white hover:shadow-md'
          }`}
        style={{
          border: selected ? '2px solid #FF5C00' : '2px solid #d1d5db',
          transition: 'box-shadow 0.15s',
        }}
      >
        <span className={`text-sm font-semibold leading-tight truncate px-1 ${selected ? 'text-brand-700' : 'text-gray-800'}`}>
          {table.name}
        </span>
        <span className={`text-[10px] ${selected ? 'text-brand-500' : 'text-gray-400'}`}>
          {table.capacity}人
        </span>
      </div>

      {/* Resize handles — only when selected */}
      {selected && HANDLES.map(h => (
        <div
          key={h.id}
          style={{
            position: 'absolute',
            width: 10,
            height: 10,
            background: '#FF5C00',
            borderRadius: '50%',
            cursor: h.cursor,
            zIndex: 20,
            ...h.style,
          }}
          onMouseDown={e => { e.stopPropagation(); onResizeMouseDown(e, h.id) }}
        />
      ))}
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function TablesPage() {
  const { t } = useTranslation()
  const [sections, setSections] = useState<Section[]>([])
  const [tables, setTables] = useState<TableConfig[]>([])
  const [activeSectionId, setActiveSectionId] = useState<string | null>(null)
  const [selectedTableId, setSelectedTableId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [editingSection, setEditingSection] = useState<string | null>(null)
  const [editingSectionName, setEditingSectionName] = useState('')

  // Canvas scale: virtual 1200×675 → actual DOM size
  const canvasWrapRef = useRef<HTMLDivElement>(null)
  const [scale, setScale] = useState(1)

  // We keep a ref copy of tables so drag/resize handlers always have latest values
  const tablesRef = useRef<TableConfig[]>([])
  tablesRef.current = tables

  // Drag state
  const drag = useRef<{
    tableId: string; startMX: number; startMY: number; origX: number; origY: number
    moved: boolean
  } | null>(null)

  // Resize state
  const resize = useRef<{
    tableId: string; handle: Handle
    startMX: number; startMY: number
    orig: { x: number; y: number; w: number; h: number }
  } | null>(null)

  // ── Scale calculation ───────────────────────────────────────────────────────

  useEffect(() => {
    const obs = new ResizeObserver(entries => {
      const entry = entries[0]
      if (!entry) return
      const { width, height } = entry.contentRect
      const sx = width / VW
      const sy = height / VH
      setScale(Math.min(sx, sy))
    })
    if (canvasWrapRef.current) obs.observe(canvasWrapRef.current)
    return () => obs.disconnect()
  }, [])

  // ── Load data ───────────────────────────────────────────────────────────────

  const reload = useCallback(() => {
    setLoading(true)
    Promise.all([sectionApi.list(), tableApi.list()])
      .then(([s, tbl]) => {
        const sorted = [...s].sort((a, b) => a.sortOrder - b.sortOrder)
        setSections(sorted)
        setTables(tbl)
        setActiveSectionId(prev => prev ?? (sorted[0]?.id ?? null))
      })
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => { reload() }, [reload])

  const sectionTables = tables.filter(t => t.sectionId === activeSectionId)

  // ── Drag ────────────────────────────────────────────────────────────────────

  function handleTableMouseDown(e: React.MouseEvent, tableId: string) {
    if (e.button !== 0) return
    e.preventDefault()
    e.stopPropagation()
    const tbl = tablesRef.current.find(t => t.id === tableId)
    if (!tbl) return
    // Don't select immediately — wait for mouseup to distinguish click vs drag
    drag.current = { tableId, startMX: e.clientX, startMY: e.clientY, origX: tbl.x, origY: tbl.y, moved: false }
  }

  // ── Resize ───────────────────────────────────────────────────────────────────

  function handleResizeMouseDown(e: React.MouseEvent, tableId: string, handle: Handle) {
    if (e.button !== 0) return
    e.preventDefault()
    e.stopPropagation()
    const tbl = tablesRef.current.find(t => t.id === tableId)
    if (!tbl) return
    resize.current = {
      tableId, handle,
      startMX: e.clientX, startMY: e.clientY,
      orig: { x: tbl.x, y: tbl.y, w: tbl.w, h: tbl.h },
    }
  }

  // ── Window-level mouse events ────────────────────────────────────────────────

  useEffect(() => {
    function onMouseMove(e: MouseEvent) {
      const s = scale || 1

      if (drag.current) {
        const dx = (e.clientX - drag.current.startMX) / s
        const dy = (e.clientY - drag.current.startMY) / s
        // Only start dragging after 4px threshold
        if (!drag.current.moved && Math.abs(dx) < 4 && Math.abs(dy) < 4) return
        drag.current.moved = true
        const tbl = tablesRef.current.find(t => t.id === drag.current!.tableId)
        if (!tbl) return
        // Capture values before async setState to avoid stale ref access in updater
        const tableId = drag.current.tableId
        const origX = drag.current.origX
        const origY = drag.current.origY
        const newX = snap(Math.max(0, Math.min(VW - tbl.w, origX + dx)))
        const newY = snap(Math.max(0, Math.min(VH - tbl.h, origY + dy)))
        setTables(prev => prev.map(t => t.id === tableId ? { ...t, x: newX, y: newY } : t))
        return
      }

      if (resize.current) {
        const { handle, startMX, startMY, orig } = resize.current
        const dx = (e.clientX - startMX) / s
        const dy = (e.clientY - startMY) / s
        let { x, y, w, h } = orig

        if (handle.includes('e')) {
          w = Math.max(MIN_SIZE, Math.min(VW - orig.x, snap(orig.w + dx)))
        }
        if (handle.includes('s')) {
          h = Math.max(MIN_SIZE, Math.min(VH - orig.y, snap(orig.h + dy)))
        }
        if (handle.includes('w')) {
          const newW = Math.max(MIN_SIZE, snap(orig.w - dx))
          const newX = Math.max(0, orig.x + orig.w - newW)
          w = orig.x + orig.w - newX
          x = newX
        }
        if (handle.includes('n')) {
          const newH = Math.max(MIN_SIZE, snap(orig.h - dy))
          const newY = Math.max(0, orig.y + orig.h - newH)
          h = orig.y + orig.h - newY
          y = newY
        }

        const resizeId = resize.current.tableId
        setTables(prev => prev.map(t =>
          t.id === resizeId ? { ...t, x, y, w, h } : t
        ))
      }
    }

    function onMouseUp() {
      if (drag.current) {
        const { tableId, moved } = drag.current
        const tbl = tablesRef.current.find(t => t.id === tableId)
        drag.current = null
        if (!moved) {
          // It was a click — select the table
          setSelectedTableId(tableId)
        } else if (tbl) {
          tableApi.update(tableId, { x: tbl.x, y: tbl.y })
        }
      }
      if (resize.current) {
        const id = resize.current.tableId
        const tbl = tablesRef.current.find(t => t.id === id)
        resize.current = null
        if (tbl) tableApi.update(id, { x: tbl.x, y: tbl.y, w: tbl.w, h: tbl.h })
      }
    }

    window.addEventListener('mousemove', onMouseMove)
    window.addEventListener('mouseup', onMouseUp)
    return () => {
      window.removeEventListener('mousemove', onMouseMove)
      window.removeEventListener('mouseup', onMouseUp)
    }
  }, [scale])

  // ── Add table ────────────────────────────────────────────────────────────────

  async function addTable() {
    if (!activeSectionId) return
    const existing = tablesRef.current.filter(t => t.sectionId === activeSectionId)
    const idx = existing.length
    // Place in a grid: 4 columns, rows of 160px
    const col = idx % 4
    const row = Math.floor(idx / 4)
    const newTable: Omit<TableConfig, 'id'> = {
      name: `T${idx + 1}`,
      sectionId: activeSectionId,
      capacity: 4,
      shape: 'square',
      x: snap(60 + col * 180),
      y: snap(60 + row * 180),
      w: 120,
      h: 120,
    }
    const res = await tableApi.create(newTable) as { data?: { id?: string } }
    reload()
    // Select the new table after reload
    const newId = res?.data?.id
    if (newId) setSelectedTableId(newId)
  }

  // ── Delete table ─────────────────────────────────────────────────────────────

  async function deleteTable(id: string) {
    await tableApi.delete(id)
    setSelectedTableId(null)
    setTables(prev => prev.filter(t => t.id !== id))
  }

  // ── Update selected table property ───────────────────────────────────────────

  async function updateTable(patch: Partial<TableConfig>) {
    if (!selectedTableId) return
    setTables(prev => prev.map(t => t.id === selectedTableId ? { ...t, ...patch } : t))
    await tableApi.update(selectedTableId, patch)
  }

  // ── Sections ─────────────────────────────────────────────────────────────────

  async function addSection() {
    await sectionApi.create({ name: t('tables.newSectionName'), sortOrder: sections.length })
    reload()
  }

  async function renameSection(id: string, name: string) {
    await sectionApi.update(id, { name })
    setSections(prev => prev.map(s => s.id === id ? { ...s, name } : s))
    setEditingSection(null)
  }

  async function deleteSection(id: string) {
    if (!confirm(t('tables.confirmDeleteSection'))) return
    await sectionApi.delete(id)
    setActiveSectionId(sections.find(s => s.id !== id)?.id ?? null)
    setSections(prev => prev.filter(s => s.id !== id))
    setTables(prev => prev.filter(t => t.sectionId !== id))
  }

  const selectedTable = tables.find(t => t.id === selectedTableId)

  if (loading) return <div className="p-8 text-sm text-gray-400">{t('common.loading')}</div>

  return (
    <div className="flex h-full overflow-hidden">

      {/* ── Left: Section list ── */}
      <aside className="w-48 flex-shrink-0 border-e border-gray-100 bg-white flex flex-col">
        <div className="px-4 py-4 border-b border-gray-100">
          <h1 className="text-sm font-semibold text-gray-900">{t('tables.title')}</h1>
          <p className="text-xs text-gray-400 mt-0.5">{t('tables.subtitle')}</p>
        </div>

        <div className="flex-1 overflow-y-auto py-2">
          {sections.length === 0 && (
            <p className="px-4 py-6 text-xs text-gray-400 text-center">{t('tables.noSections')}</p>
          )}
          {sections.map(section => (
            <div key={section.id} className="group">
              {editingSection === section.id ? (
                <div className="px-2 py-1">
                  <input
                    className="input text-sm py-1 w-full"
                    value={editingSectionName}
                    onChange={e => setEditingSectionName(e.target.value)}
                    onKeyDown={e => {
                      if (e.key === 'Enter') renameSection(section.id, editingSectionName)
                      if (e.key === 'Escape') setEditingSection(null)
                    }}
                    onBlur={() => renameSection(section.id, editingSectionName)}
                    autoFocus
                  />
                </div>
              ) : (
                <button
                  className={`w-full flex items-center gap-2 px-4 py-2.5 text-sm transition-colors ${
                    activeSectionId === section.id
                      ? 'bg-brand-50 text-brand-700 font-medium'
                      : 'text-gray-700 hover:bg-gray-50'
                  }`}
                  onClick={() => { setActiveSectionId(section.id); setSelectedTableId(null) }}
                >
                  <span className="flex-1 text-start truncate">{section.name}</span>
                  <span className="text-xs text-gray-400">
                    {tables.filter(tt => tt.sectionId === section.id).length}
                  </span>
                  <span className="hidden group-hover:flex gap-1">
                    <button className="p-0.5 text-gray-400 hover:text-gray-600"
                      onClick={e => { e.stopPropagation(); setEditingSection(section.id); setEditingSectionName(section.name) }}>
                      <svg className="w-3 h-3" viewBox="0 0 20 20" fill="currentColor">
                        <path d="M13.586 3.586a2 2 0 112.828 2.828l-.793.793-2.828-2.828.793-.793zM11.379 5.793L3 14.172V17h2.828l8.38-8.379-2.83-2.828z" />
                      </svg>
                    </button>
                    <button className="p-0.5 text-gray-400 hover:text-red-500"
                      onClick={e => { e.stopPropagation(); deleteSection(section.id) }}>
                      <svg className="w-3 h-3" viewBox="0 0 20 20" fill="currentColor">
                        <path fillRule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clipRule="evenodd" />
                      </svg>
                    </button>
                  </span>
                </button>
              )}
            </div>
          ))}
        </div>

        <div className="px-3 py-3 border-t border-gray-100">
          <button className="btn-secondary w-full text-xs" onClick={addSection}>
            + {t('tables.addSection')}
          </button>
        </div>
      </aside>

      {/* ── Right: canvas + bottom properties bar ── */}
      <div className="flex-1 flex flex-col min-w-0 min-h-0">

        {/* Toolbar */}
        <div className="flex items-center gap-3 px-5 py-3 border-b border-gray-100 bg-white flex-shrink-0">
          <span className="text-sm font-medium text-gray-700">
            {sections.find(s => s.id === activeSectionId)?.name ?? '—'}
          </span>
          <span className="text-xs text-gray-400">{sectionTables.length} {t('tables.tableCount')}</span>
          <div className="ml-auto">
            <button
              className="btn-primary text-xs"
              onClick={addTable}
              disabled={!activeSectionId}
            >
              + {t('tables.addTable')}
            </button>
          </div>
        </div>

        {/* Canvas area */}
        <div className="flex-1 min-h-0 bg-gray-100 flex items-center justify-center overflow-hidden" ref={canvasWrapRef}>
          {!activeSectionId ? (
            <p className="text-sm text-gray-400">{t('tables.noSections')}</p>
          ) : (
            <div
              style={{
                width: VW,
                height: VH,
                transform: `scale(${scale})`,
                transformOrigin: 'center center',
                position: 'relative',
                background: 'white',
                borderRadius: 12,
                border: '2px solid #d1d5db',
                boxShadow: '0 4px 24px 0 rgba(0,0,0,0.10)',
                overflow: 'hidden',
                flexShrink: 0,
                cursor: 'default',
              }}
              onClick={() => setSelectedTableId(null)}
            >
              {/* Grid dots */}
              <div
                style={{
                  position: 'absolute', inset: 0, pointerEvents: 'none', opacity: 0.4,
                  backgroundImage: 'radial-gradient(circle, #9ca3af 1px, transparent 1px)',
                  backgroundSize: `${SNAP}px ${SNAP}px`,
                }}
              />

              {sectionTables.map(table => (
                <TableCard
                  key={table.id}
                  table={table}
                  selected={selectedTableId === table.id}
                  onMouseDown={e => handleTableMouseDown(e, table.id)}
                  onResizeMouseDown={(e, handle) => handleResizeMouseDown(e, table.id, handle)}
                />
              ))}

              {sectionTables.length === 0 && (
                <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', pointerEvents: 'none' }}>
                  <span style={{ color: '#9ca3af', fontSize: 14 }}>{t('tables.emptyCanvas')}</span>
                </div>
              )}
            </div>
          )}
        </div>

        {/* ── Bottom properties bar (replaces right panel) ── */}
        {selectedTable ? (
          <div className="flex-shrink-0 border-t border-gray-100 bg-white px-5 py-3 flex items-center gap-6">
            {/* Name */}
            <div className="flex items-center gap-2">
              <label className="text-xs font-medium text-gray-500">{t('tables.tableName')}</label>
              <input
                className="input text-sm py-1 w-28"
                value={selectedTable.name}
                onChange={e => updateTable({ name: e.target.value })}
              />
            </div>

            {/* Capacity */}
            <div className="flex items-center gap-2">
              <label className="text-xs font-medium text-gray-500">{t('tables.capacity')}</label>
              <input
                className="input text-sm py-1 w-16 font-mono"
                type="number" min={1} max={30}
                value={selectedTable.capacity}
                onChange={e => updateTable({ capacity: Number(e.target.value) })}
              />
            </div>

            {/* Shape */}
            <div className="flex items-center gap-2">
              <label className="text-xs font-medium text-gray-500">{t('tables.shape')}</label>
              <div className="flex gap-1.5">
                {(['square', 'round', 'rect'] as const).map(shape => (
                  <button
                    key={shape}
                    type="button"
                    onClick={() => updateTable({
                      shape,
                      w: shape === 'rect' ? 180 : 120,
                      h: 120,
                    })}
                    className={`px-3 py-1 border rounded-lg text-xs transition-colors ${
                      selectedTable.shape === shape
                        ? 'border-brand-400 bg-brand-50 text-brand-700'
                        : 'border-gray-200 text-gray-500 hover:border-gray-300'
                    }`}
                  >
                    {t(`tables.shape${shape.charAt(0).toUpperCase() + shape.slice(1)}`)}
                  </button>
                ))}
              </div>
            </div>

            {/* Size display */}
            <div className="flex items-center gap-1 text-xs text-gray-400 font-mono">
              <span>{selectedTable.w}×{selectedTable.h}</span>
            </div>

            {/* Delete */}
            <div className="ml-auto">
              <button
                className="text-xs text-red-500 hover:text-red-700 transition-colors px-3 py-1 rounded-lg border border-red-200 hover:border-red-400"
                onClick={() => deleteTable(selectedTable.id)}
              >
                {t('tables.deleteTable')}
              </button>
            </div>
          </div>
        ) : (
          <div className="flex-shrink-0 border-t border-gray-100 bg-white px-5 py-3 h-12 flex items-center">
            <span className="text-xs text-gray-400">{t('tables.clickToSelect')}</span>
          </div>
        )}
      </div>
    </div>
  )
}
