import { useState, useRef, useEffect } from 'react'
import { DayPicker } from 'react-day-picker'
import { useTranslation } from 'react-i18next'
import 'react-day-picker/dist/style.css'

// Map i18n locale → Intl locale for date formatting
const INTL_LOCALE: Record<string, string> = {
  'zh-CN': 'zh-CN',
  'zh-TW': 'zh-TW',
  'en-US': 'en-US',
  'en-GB': 'en-GB',
  'ja-JP': 'ja-JP',
  'ko-KR': 'ko-KR',
  'th-TH': 'th-TH',
  'vi-VN': 'vi-VN',
  'ms-MY': 'ms-MY',
  'ar-SA': 'ar-SA',
}


function formatDate(date: Date, locale: string): string {
  const intlLocale = INTL_LOCALE[locale] ?? locale
  return new Intl.DateTimeFormat(intlLocale, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

function parseDateString(value: string): Date | undefined {
  if (!value) return undefined
  // Accept ISO YYYY-MM-DD for internal value
  const d = new Date(value + 'T00:00:00')
  return isNaN(d.getTime()) ? undefined : d
}

function toISODate(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

interface Props {
  value: string        // ISO YYYY-MM-DD, empty = unset
  onChange: (iso: string) => void
  placeholder?: string
  className?: string
}

export default function DatePicker({ value, onChange, placeholder, className = '' }: Props) {
  const { i18n } = useTranslation()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  const selected = parseDateString(value)
  const displayValue = selected ? formatDate(selected, i18n.language) : ''
  const intlLocale = INTL_LOCALE[i18n.language] ?? i18n.language

  // Close on outside click
  useEffect(() => {
    function handle(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handle)
    return () => document.removeEventListener('mousedown', handle)
  }, [])

  function handleSelect(day: Date | undefined) {
    if (day) {
      onChange(toISODate(day))
      setOpen(false)
    }
  }

  return (
    <div ref={ref} className={`relative ${className}`}>
      <button
        type="button"
        className="input flex items-center gap-2 cursor-pointer min-w-[140px] text-start w-full"
        onClick={() => setOpen(v => !v)}
      >
        <svg className="w-4 h-4 text-gray-400 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
            d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
        </svg>
        <span className={displayValue ? 'text-gray-900' : 'text-gray-400'}>
          {displayValue || placeholder || '—'}
        </span>
        {value && (
          <span
            className="ms-auto text-gray-300 hover:text-gray-500 leading-none"
            onMouseDown={e => { e.preventDefault(); e.stopPropagation(); onChange('') }}
          >✕</span>
        )}
      </button>

      {open && (
        <div className="absolute z-50 mt-1 bg-white border border-gray-200 rounded-xl shadow-lg p-2 start-0">
          <DayPicker
            mode="single"
            selected={selected}
            onSelect={handleSelect}
            defaultMonth={selected ?? new Date()}
            formatters={{
              formatWeekdayName: (day) =>
                new Intl.DateTimeFormat(intlLocale, { weekday: 'short' }).format(day),
              formatCaption: (date) =>
                new Intl.DateTimeFormat(intlLocale, { year: 'numeric', month: 'long' }).format(date),
            }}
            styles={{
              day: { borderRadius: '8px' },
            }}
            modifiersClassNames={{
              selected: 'rdp-selected',
            }}
          />
        </div>
      )}
    </div>
  )
}
