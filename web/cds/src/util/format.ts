// Currency follows the POS region config (delivered with live state). Module-level so every
// component formats consistently without prop-drilling; defaults suit the mock/offline preview.
let currencySymbol = '$'
let minorDigits = 2

/** Sets the active currency (called when live CDS state arrives). */
export function setCurrency(symbol: string, digits: number): void {
  currencySymbol = symbol || '$'
  minorDigits = Number.isFinite(digits) && digits >= 0 ? digits : 2
}

/** Formats a whole-currency number with the active currency, e.g. 38.88 -> "$38.88" / "¥38.88". */
export function formatCurrency(amount: number): string {
  const sign = amount < 0 ? '-' : ''
  return `${sign}${currencySymbol}${Math.abs(amount).toFixed(minorDigits)}`
}

/** Formats the live clock time, e.g. "2:30 PM". */
export function formatTime(date: Date): string {
  return date.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })
}

/** Formats a date line, e.g. "May 24, 2024". */
export function formatDate(date: Date): string {
  return date.toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' })
}
