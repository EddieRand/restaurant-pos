import { Icon, type IconName } from './Icon'

interface OrderBadgeProps {
  icon: IconName
  label: string
}

/** Pill badge used for order type (Dine In) and table label (Table 07). */
export function OrderBadge({ icon, label }: OrderBadgeProps) {
  return (
    <span className="inline-flex items-center gap-2 rounded-full bg-accent-soft/70 px-4 py-2 text-base font-medium text-textPrimary">
      <Icon name={icon} className="h-5 w-5 text-textSecondary" />
      {label}
    </span>
  )
}
