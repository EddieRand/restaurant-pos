import { useState } from 'react'
import type { ReceiptMethod } from '../types'
import { Icon } from './Icon'

/**
 * Receipt delivery selector: Email / Text message tabs, an input, a Send button, a QR
 * access placeholder, and a "No thanks" link. No payment-method prompts of any kind.
 */
export function ReceiptMethodSelector() {
  const [method, setMethod] = useState<ReceiptMethod>('email')
  const [value, setValue] = useState('')

  return (
    <div className="cds-card flex flex-col gap-4 px-7 py-6">
      <h3 className="text-2xl font-bold text-textPrimary">Get your receipt</h3>

      <div className="grid grid-cols-2 gap-3">
        <TabButton active={method === 'email'} icon="mail" label="Email" onClick={() => setMethod('email')} />
        <TabButton active={method === 'text'} icon="message" label="Text message" onClick={() => setMethod('text')} />
      </div>

      <input
        type={method === 'email' ? 'email' : 'tel'}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder={method === 'email' ? 'Enter your email address' : 'Enter your phone number'}
        className="cds-input"
      />

      <button type="button" className="cds-btn-primary">
        Send receipt
      </button>

      <div className="flex items-center gap-4">
        <span className="h-px flex-1 bg-cdsborder" />
        <span className="text-base text-textSecondary">Or scan to access your receipt</span>
        <span className="h-px flex-1 bg-cdsborder" />
      </div>

      <div className="flex items-center justify-between gap-6">
        <div className="flex h-28 w-28 items-center justify-center rounded-cds-sm border border-cdsborder bg-surface text-accent">
          <Icon name="qr" className="h-16 w-16" strokeWidth={1.5} />
        </div>
        <button type="button" className="text-xl font-medium text-textSecondary underline underline-offset-4">
          No thanks
        </button>
      </div>
    </div>
  )
}

function TabButton({
  active,
  icon,
  label,
  onClick,
}: {
  active: boolean
  icon: 'mail' | 'message'
  label: string
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={`flex items-center justify-center gap-2.5 rounded-cds-sm border-2 px-4 py-3.5 text-xl font-medium transition-colors duration-150 ${
        active ? 'border-accent bg-accent-soft text-accent-dark' : 'border-cdsborder bg-surface text-textSecondary'
      }`}
    >
      <Icon name={icon} className="h-6 w-6" />
      {label}
    </button>
  )
}
