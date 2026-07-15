import { useEffect, useState } from 'react'
import type { CdsStore } from '../types'
import { formatDate, formatTime } from '../util/format'
import { Icon } from './Icon'

interface CDSHeaderProps {
  store: CdsStore
  /** Tip page shows a date line beneath the time and centers nothing; default false. */
  showDate?: boolean
}

/** Minimal brand header: logo placeholder + store name on the left, live clock on the right. */
export function CDSHeader({ store, showDate = false }: CDSHeaderProps) {
  const [now, setNow] = useState(() => new Date())

  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000)
    return () => clearInterval(id)
  }, [])

  return (
    <header className="flex items-center justify-between px-10 py-5">
      <div className="flex items-center gap-3.5">
        {store.logoUrl ? (
          <img src={store.logoUrl} alt="" className="h-11 w-11 rounded-2xl object-cover" />
        ) : (
          <LogoPlaceholder />
        )}
        <span className="text-2xl font-semibold text-textPrimary">{store.name}</span>
      </div>

      <div className={showDate ? 'text-right' : 'flex items-center gap-2.5 text-textPrimary'}>
        {showDate ? (
          <>
            <div className="text-xl font-semibold text-textPrimary">{formatTime(now)}</div>
            <div className="text-sm text-textSecondary">{formatDate(now)}</div>
          </>
        ) : (
          <>
            <Icon name="clock" className="h-6 w-6 text-textSecondary" />
            <span className="text-xl font-medium tabular-nums">{formatTime(now)}</span>
          </>
        )}
      </div>
    </header>
  )
}

/** Brand tile placeholder (four soft rounded squares) used when no logo is configured. */
function LogoPlaceholder() {
  return (
    <div className="grid h-11 w-11 grid-cols-2 gap-1 rounded-2xl bg-accent-soft p-2">
      <span className="rounded-[3px] bg-accent/70" />
      <span className="rounded-[3px] bg-accent/40" />
      <span className="rounded-[3px] bg-accent/40" />
      <span className="rounded-[3px] bg-accent/70" />
    </div>
  )
}
