import type { ReactNode } from 'react'
import type { CdsStore } from '../types'
import { CDSHeader } from './CDSHeader'
import { InfoBar } from './InfoBar'

interface CDSPageShellProps {
  store: CdsStore
  /** 'split' = left hero + right card column; 'centered' = single centered column (tip). */
  variant?: 'split' | 'centered'
  /** Left hero column (split variant). */
  hero?: ReactNode
  /** Right content column (split variant) or the centered content. */
  children: ReactNode
  /** Bottom helper message; rendered as a full-width InfoBar inside the frame. */
  infoMessage?: string
  /** Tip page header shows a date line. */
  headerShowDate?: boolean
}

/**
 * Shared landscape 16:9 customer-display frame: warm background, minimal brand header,
 * generous padding, optional bottom info bar. Every CDS page renders through this so
 * header / spacing / background stay identical across the flow.
 */
export function CDSPageShell({
  store,
  variant = 'split',
  hero,
  children,
  infoMessage,
  headerShowDate = false,
}: CDSPageShellProps) {
  return (
    <div className="flex h-full min-h-screen w-full flex-col bg-background">
      <div className="border-b border-cdsborder/60">
        <CDSHeader store={store} showDate={headerShowDate} />
      </div>

      {variant === 'split' ? (
        <div className="grid min-h-0 flex-1 grid-cols-1 gap-10 px-10 py-6 lg:grid-cols-[1fr_minmax(420px,540px)]">
          <div className="flex min-h-0 flex-col justify-center">{hero}</div>
          <div className="flex min-h-0 flex-col justify-center">{children}</div>
        </div>
      ) : (
        <div className="flex min-h-0 flex-1 flex-col items-center justify-center px-10 py-6">
          <div className="w-full max-w-5xl">{children}</div>
        </div>
      )}

      {infoMessage && (
        <div className="px-10 pb-6">
          <InfoBar message={infoMessage} />
        </div>
      )}
    </div>
  )
}

/** Left hero text block (headline + subtitle + illustration) shared by split-variant pages. */
export function CDSHero({
  headline,
  subtitle,
  children,
}: {
  headline: string
  subtitle: string
  children?: ReactNode
}) {
  return (
    <div className="max-w-xl">
      <h1 className="text-6xl font-bold leading-[1.05] tracking-tight text-textPrimary">{headline}</h1>
      <p className="mt-4 text-2xl font-normal text-textSecondary">{subtitle}</p>
      {children && <div className="mt-6">{children}</div>}
    </div>
  )
}
