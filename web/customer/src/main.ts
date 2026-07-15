import './styles.css'

// ===== i18n 多语言支持 =====
type SupportedLocale = 'zh-CN' | 'en' | 'ja' | 'ko' | 'ar'

const TRANSLATIONS: Record<SupportedLocale, Record<string, string>> = {
  'zh-CN': {
    'order.type.dineIn': '堂食',
    'order.type.takeaway': '自取',
    'order.type.delivery': '外卖',
    'cart.title': '购物车',
    'cart.empty': '请选择菜品',
    'cart.quantity': '数量',
    'cart.add': '添加',
    'cart.remove': '移除',
    'cart.total': '合计',
    'order.submit': '提交订单',
    'order.submitting': '提交中...',
    'order.submitted': '订单已提交',
    'order.staffConfirm': '已提交，等待餐厅确认',
    'payment.payBefore': '先付后下单',
    'payment.payAfter': '下单后支付',
    'payment.payAtRestaurant': '用餐后支付',
    'payment.menuOnly': '只读菜单',
    'menu.all': '全部',
    'menu.empty': '暂无可点菜品',
    'menu.emptyHint': '餐厅还没有发布菜单，或当前菜品已全部下架。',
    'form.name': '姓名',
    'form.phone': '电话',
    'form.address': '地址',
    'form.notes': '备注',
    'status.connecting': '连接中',
    'status.available': '可点餐',
    'status.menuOnly': '浏览菜单',
    'error.qrInvalid': '二维码不可用',
    'error.noOrderType': '此二维码未开启可用的订单类型。',
    'error.loadFailed': '页面加载失败',
    'error.refreshOrContact': '请刷新页面或联系餐厅工作人员。',
    'accessibility.skipToContent': '跳到主要内容',
    'accessibility.cartUpdated': '购物车已更新',
    'accessibility.orderSubmitted': '订单已成功提交',
    'item.allergens': '过敏原',
    'course': '第',
    'course.suffix': '道菜',
  },
  'en': {
    'order.type.dineIn': 'Dine In',
    'order.type.takeaway': 'Takeaway',
    'order.type.delivery': 'Delivery',
    'cart.title': 'Cart',
    'cart.empty': 'Please select items',
    'cart.quantity': 'Qty',
    'cart.add': 'Add',
    'cart.remove': 'Remove',
    'order.submit': 'Submit Order',
    'order.submitting': 'Submitting...',
    'order.submitted': 'Order Submitted',
    'order.staffConfirm': 'Submitted, waiting for restaurant confirmation',
    'payment.payBefore': 'Pay before ordering',
    'payment.payAfter': 'Pay after ordering',
    'payment.payAtRestaurant': 'Pay at restaurant',
    'payment.menuOnly': 'View Menu',
    'menu.all': 'All',
    'menu.empty': 'No items available',
    'menu.emptyHint': 'The restaurant has not published a menu yet, or all items are currently unavailable.',
    'form.name': 'Name',
    'form.phone': 'Phone',
    'form.address': 'Address',
    'form.notes': 'Notes',
    'status.connecting': 'Connecting',
    'status.available': 'Available',
    'status.menuOnly': 'View Menu',
    'error.qrInvalid': 'Invalid QR Code',
    'error.noOrderType': 'No available order types for this QR code.',
    'error.loadFailed': 'Page load failed',
    'error.refreshOrContact': 'Please refresh the page or contact restaurant staff.',
    'accessibility.skipToContent': 'Skip to main content',
    'accessibility.cartUpdated': 'Cart updated',
    'accessibility.orderSubmitted': 'Order successfully submitted',
    'item.allergens': 'Allergens',
    'course': 'Course ',
    'course.suffix': '',
  },
}

function detectLocale(): SupportedLocale {
  const browserLang = navigator.language
  const supported: SupportedLocale[] = ['zh-CN', 'en', 'ja', 'ko', 'ar']
  if (supported.includes(browserLang as SupportedLocale)) return browserLang as SupportedLocale
  const langCode = browserLang.split('-')[0]
  const partial = supported.find(s => s.startsWith(langCode))
  return partial ?? 'zh-CN'
}

function t(key: string, locale: SupportedLocale = currentLocale): string {
  return TRANSLATIONS[locale]?.[key] ?? key
}

function isRTL(locale: SupportedLocale): boolean {
  return ['ar', 'he', 'fa', 'ur'].includes(locale.split('-')[0])
}

let currentLocale: SupportedLocale = detectLocale()

// 屏幕阅读器播报
function announceToScreenReader(message: string, assertive: boolean = false) {
  const regionId = assertive ? 'live-region-assertive' : 'live-region'
  const region = document.getElementById(regionId)
  if (!region) return
  region.textContent = message
  setTimeout(() => {
    region.textContent = ''
    setTimeout(() => { region.textContent = message }, 100)
  }, 100)
}

// 应用语言和 RTL
function applyLocale() {
  document.documentElement.lang = currentLocale
  document.documentElement.dir = isRTL(currentLocale) ? 'rtl' : 'ltr'
  document.body.classList.toggle('rtl', isRTL(currentLocale))
}

type QrContext = {
  code: string
  scope: string
  tableId?: string | null
  orderTypes: string[]
  menuOnly: boolean
  paymentTiming: string
  firePolicy: string
}

type MenuItem = {
  id: string
  names: string
  priceMinorUnit: number
  categoryId: string
  course: number
  isSoldOut: boolean
  allergens: string
}

type MenuResponse = {
  items: MenuItem[]
  currencySymbol: string
  currencyMinorDigits: number
}

type CartItem = { menuItemId: string; quantity: number; notes?: string }
type Session = {
  id: string
  code: string
  orderType: string
  tableId?: string | null
  status: string
  cart: { items: CartItem[] }
  orderId?: string | null
}

type SubmitResponse = {
  orderId: string
  status: string
  totalMinorUnit: number
  requiresPayment: boolean
  requiresStaffConfirmation: boolean
}

const app = document.querySelector<HTMLDivElement>('#app')!
const params = new URLSearchParams(location.search)
const code = params.get('code') || location.pathname.split('/').filter(Boolean).pop() || 'demo-table-1'

window.addEventListener('error', event => {
  renderState(t('error.loadFailed'), event.message || t('error.refreshOrContact'))
})

window.addEventListener('unhandledrejection', event => {
  const reason = event.reason
  renderState(t('error.loadFailed'), reason instanceof Error ? reason.message : t('error.refreshOrContact'))
})

let context: QrContext
let menu: MenuResponse
let session: Session | null = null
let cart = new Map<string, number>()
let category = 'all'
let submitting = false
let statusText = '连接中'

function api<T>(url: string, init?: RequestInit): Promise<T> {
  return fetch(url, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  }).then(async response => {
    if (!response.ok) {
      const text = await response.text()
      throw new Error(text || `HTTP ${response.status}`)
    }
    return response.json() as Promise<T>
  })
}

function itemName(item: MenuItem) {
  try {
    const names = JSON.parse(item.names) as Record<string, string>
    return names.zh || names.en || Object.values(names)[0] || item.id
  } catch {
    return item.names || item.id
  }
}

function money(minor: number, locale: SupportedLocale = currentLocale) {
  const digits = menu?.currencyMinorDigits ?? 2
  const symbol = menu?.currencySymbol ?? '$'
  const localeMap: Record<SupportedLocale, string> = {
    'zh-CN': 'zh-CN',
    'en': 'en-US',
    'ja': 'ja-JP',
    'ko': 'ko-KR',
    'ar': 'ar-SA',
  }
  const intlLocale = localeMap[locale] ?? 'zh-CN'
  let currencyCode = 'CNY'
  const symMap: Record<string, string> = { '$': 'USD', '¥': 'CNY', '£': 'GBP', '€': 'EUR', '₩': 'KRW', '﷼': 'SAR' }
  currencyCode = symMap[symbol] ?? 'USD'
  try {
    return new Intl.NumberFormat(intlLocale, {
      style: 'currency',
      currency: currencyCode,
      minimumFractionDigits: digits,
      maximumFractionDigits: digits,
    }).format(minor / Math.pow(10, digits))
  } catch {
    return `${symbol}${(minor / Math.pow(10, digits)).toFixed(digits)}`
  }
}

function orderTypeLabel(type: string) {
  const map: Record<string, string> = {
    'DINE_IN': 'order.type.dineIn',
    'TAKEAWAY': 'order.type.takeaway',
    'DELIVERY': 'order.type.delivery',
  }
  return t(map[type] ?? 'order.type.dineIn')
}

function allowedOrderType() {
  if (context.scope === 'TABLE') return 'DINE_IN'
  if (context.scope === 'PICKUP') return 'TAKEAWAY'
  if (context.scope === 'DELIVERY') return 'DELIVERY'
  return context.orderTypes[0] ?? 'DINE_IN'
}

function subtotal() {
  return Array.from(cart.entries()).reduce((sum, [id, quantity]) => {
    const item = menu.items.find(candidate => candidate.id === id)
    return sum + (item?.priceMinorUnit ?? 0) * quantity
  }, 0)
}

async function ensureSession() {
  if (session || context.menuOnly) return
  session = await api<Session>('/public/sessions', {
    method: 'POST',
    body: JSON.stringify({ code, orderType: allowedOrderType(), customerInfo: readCustomerInfo() }),
  })
  cart = new Map(session.cart.items.map(item => [item.menuItemId, item.quantity]))
}

async function syncCart() {
  if (!session) return
  session = await api<Session>(`/public/sessions/${session.id}/cart`, {
    method: 'POST',
    body: JSON.stringify({ items: Array.from(cart.entries()).map(([menuItemId, quantity]) => ({ menuItemId, quantity })) }),
  })
}

function readCustomerInfo() {
  const name = document.querySelector<HTMLInputElement>('#customer-name')?.value.trim() || null
  const phone = document.querySelector<HTMLInputElement>('#customer-phone')?.value.trim() || null
  const address = document.querySelector<HTMLInputElement>('#customer-address')?.value.trim() || null
  const notes = document.querySelector<HTMLTextAreaElement>('#customer-notes')?.value.trim() || null
  return { name, phone, address, notes }
}

async function changeQty(id: string, delta: number) {
  if (context.menuOnly) return
  await ensureSession()
  const next = Math.max(0, (cart.get(id) ?? 0) + delta)
  if (next === 0) cart.delete(id)
  else cart.set(id, next)
  await syncCart()
  render()
  
  // 播报购物车更新（屏幕阅读器）
  const item = menu.items.find(i => i.id === id)
  if (item) {
    const name = itemName(item)
    const message = next === 0 
      ? `${name} ${t('cart.remove')}` 
      : `${name} ${t('cart.quantity')}: ${next}`
    announceToScreenReader(message)
  }
}

async function submitOrder() {
  if (submitting || context.menuOnly || cart.size === 0) return
  submitting = true
  statusText = t('order.submitting')
  render()
  try {
    await ensureSession()
    if (!session) return
    const submitted = await api<SubmitResponse>(`/public/sessions/${session.id}/submit`, {
      method: 'POST',
      body: JSON.stringify({}),
    })
    if (submitted.requiresPayment) {
      const intent = await api<{ id: string }>('/public/payments/mock-intents', {
        method: 'POST',
        body: JSON.stringify({ sessionId: session.id, orderId: submitted.orderId }),
      })
      await api(`/public/payments/mock-intents/${intent.id}/confirm`, { method: 'POST', body: JSON.stringify({}) })
    }
    statusText = submitted.requiresStaffConfirmation ? t('order.staffConfirm') : t('order.submitted')
    announceToScreenReader(t('accessibility.orderSubmitted'), true)
    cart.clear()
    session = null
  } catch (error) {
    statusText = error instanceof Error ? error.message : t('error.loadFailed')
    announceToScreenReader(`Error: ${statusText}`, true)
  } finally {
    submitting = false
    render()
  }
}

function render() {
  const categories = ['all', ...Array.from(new Set(menu.items.map(item => item.categoryId)))]
  const visibleItems = category === 'all' ? menu.items : menu.items.filter(item => item.categoryId === category)
  const cartItems = Array.from(cart.entries())
    .map(([id, quantity]) => ({ item: menu.items.find(candidate => candidate.id === id), quantity }))
    .filter(entry => entry.item) as Array<{ item: MenuItem; quantity: number }>

  app.innerHTML = `
    <main class="shell" id="main-content" role="main">
      <header class="topbar">
        <div class="brand">
          <h1>${context.scope === 'TABLE' ? `桌台 ${context.tableId ?? ''}` : t('order.type.dineIn')}</h1>
          <span>${context.menuOnly ? t('payment.menuOnly') : `${orderTypeLabel(allowedOrderType())} · ${paymentCopy(context.paymentTiming)}`}</span>
        </div>
        <div class="pill"><span class="dot"></span>${statusText}</div>
      </header>
      <section class="panel menu-panel">
        <nav class="tabs" role="tablist" aria-label="${t('menu.all')}">
          ${categories.map(entry => `<button class="tab ${entry === category ? 'active' : ''}" data-category="${entry}" role="tab" aria-selected="${entry === category}" tabindex="0">${entry === 'all' ? t('menu.all') : escapeHtml(entry)}</button>`).join('')}
        </nav>
        <div class="menu" role="tabpanel" aria-label="${category === 'all' ? t('menu.all') : category}">
          ${visibleItems.length === 0 ? menuEmptyState() : visibleItems.map(item => itemCard(item)).join('')}
        </div>
      </section>
      <aside class="panel cart" role="complementary" aria-label="${t('cart.title')}">
        <div class="cart-head">
          <h2>${context.menuOnly ? t('payment.menuOnly') : t('cart.title')}</h2>
          <span class="muted">${cartItems.reduce((sum, entry) => sum + entry.quantity, 0)} ${t('cart.quantity')}</span>
        </div>
        ${context.menuOnly ? `<div class="empty">${t('error.noOrderType')}</div>` : cartControls(cartItems)}
      </aside>
    </main>
  `

  document.querySelectorAll<HTMLButtonElement>('[data-category]').forEach(button => {
    button.addEventListener('click', () => {
      category = button.dataset.category || 'all'
      render()
    })
  })
  document.querySelectorAll<HTMLButtonElement>('[data-inc]').forEach(button => button.addEventListener('click', () => changeQty(button.dataset.inc!, 1)))
  document.querySelectorAll<HTMLButtonElement>('[data-dec]').forEach(button => button.addEventListener('click', () => changeQty(button.dataset.dec!, -1)))
  document.querySelector<HTMLButtonElement>('#submit-order')?.addEventListener('click', submitOrder)
  
  // 键盘导航：Tab 导航
  const focusableElements = app.querySelectorAll<HTMLElement>('button:not([disabled]):not([aria-hidden="true"]), input:not([disabled]), textarea:not([disabled]), [tabindex="0"]')
  focusableElements.forEach((el, idx) => {
    el.addEventListener('keydown', (e: KeyboardEvent) => {
      if (e.key === 'Tab') {
        // 允许默认 Tab 行为
      }
    })
  })
}

function menuEmptyState() {
  return `
    <div class="menu-empty">
      <div>
        <strong>${t('menu.empty')}</strong>
        <div class="muted">${t('menu.emptyHint')}</div>
      </div>
    </div>
  `
}

function itemCard(item: MenuItem) {
  const quantity = cart.get(item.id) ?? 0
  const name = itemName(item)
  return `
    <article class="item" role="listitem">
      <h3>${escapeHtml(name)}</h3>
      <div class="muted">${item.allergens ? `${t('item.allergens')}: ${escapeHtml(item.allergens)}` : `${t('course')}${item.course}${t('course.suffix')}`}</div>
      <div class="item-bottom">
        <span class="price">${money(item.priceMinorUnit)}</span>
        ${context.menuOnly ? '' : `
          <div class="qty" role="group" aria-label="${name} ${t('cart.quantity')}">
            <button 
              data-dec="${item.id}" 
              ${quantity === 0 ? 'disabled' : ''} 
              aria-label="${t('cart.remove')} ${name}"
              aria-controls="cart-list"
            >−</button>
            <span aria-label="${t('cart.quantity')}: ${quantity}">${quantity}</span>
            <button 
              data-inc="${item.id}" 
              ${item.isSoldOut ? 'disabled' : ''} 
              aria-label="${t('cart.add')} ${name}"
              aria-controls="cart-list"
            >+</button>
          </div>
        `}
      </div>
    </article>
  `
}

function cartControls(cartItems: Array<{ item: MenuItem; quantity: number }>) {
  const needsDelivery = allowedOrderType() === 'DELIVERY'
  return `
    <div class="section">
      <div class="field">
        <label for="customer-name">${t('form.name')}</label>
        <input id="customer-name" autocomplete="name" aria-required="true" />
      </div>
      <div class="field">
        <label for="customer-phone">${t('form.phone')}</label>
        <input id="customer-phone" autocomplete="tel" type="tel" aria-required="true" />
      </div>
      ${needsDelivery ? `
        <div class="field">
          <label for="customer-address">${t('form.address')}</label>
          <input id="customer-address" autocomplete="street-address" aria-required="true" />
        </div>
      ` : ''}
      <div class="field">
        <label for="customer-notes">${t('form.notes')}</label>
        <textarea id="customer-notes" aria-label="${t('form.notes')}"></textarea>
      </div>
    </div>
    <div class="cart-list" id="cart-list" role="list" aria-label="${t('cart.title')}">
      ${cartItems.length === 0 ? 
        `<div class="empty">${t('cart.empty')}</div>` : 
        cartItems.map(({ item, quantity }) => `
          <div class="cart-item" role="listitem">
            <strong>${escapeHtml(itemName(item))}</strong>
            <span>${quantity} × ${money(item.priceMinorUnit)}</span>
          </div>
        `).join('')
      }
    </div>
    ${context.firePolicy === 'STAFF_CONFIRM' ? 
      `<div class="section"><div class="notice" role="alert">${t('order.staffConfirm')}</div></div>` : 
      ''}
    <div class="total">
      <span>${t('cart.total')}</span>
      <span id="total-amount">${money(subtotal())}</span>
    </div>
    <div class="actions">
      <button 
        id="submit-order" 
        class="primary" 
        ${cartItems.length === 0 || submitting ? 'disabled' : ''}
        aria-label="${submitting ? t('order.submitting') : t('order.submit')}"
      >${submitting ? t('order.submitting') : t('order.submit')}</button>
    </div>
  `
}

function paymentCopy(paymentTiming: string) {
  if (paymentTiming === 'PAY_BEFORE_SUBMIT') return t('payment.payBefore')
  if (paymentTiming === 'PAY_AFTER_SUBMIT') return t('payment.payAfter')
  if (paymentTiming === 'MENU_ONLY') return t('payment.menuOnly')
  return t('payment.payAtRestaurant')
}

function escapeHtml(value: string) {
  return value.replace(/[&<>"']/g, char => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  })[char]!)
}

function renderState(title: string, body: string) {
  app.innerHTML = `<div class="state"><h1>${title}</h1><p class="muted">${body}</p></div>`
}

async function bootstrap() {
  try {
    applyLocale()  // 应用语言和 RTL
    context = await api<QrContext>(`/public/qr/${encodeURIComponent(code)}`)
    if (!context.orderTypes.length && !context.menuOnly) {
      renderState(t('error.noOrderType'), t('error.refreshOrContact'))
      return
    }
    menu = await api<MenuResponse>('/public/menu')
    statusText = context.menuOnly ? t('status.menuOnly') : t('status.available')
    render()
  } catch (error) {
    renderState(t('error.qrInvalid'), error instanceof Error ? error.message : t('error.refreshOrContact'))
  }
}

// 键盘导航支持
document.addEventListener('keydown', (e: KeyboardEvent) => {
  if (e.key === 'Escape') {
    // 可以在这里添加关闭模态框或其他 ESC 行为
    const focused = document.activeElement as HTMLElement
    if (focused && focused.blur) {
      focused.blur()
    }
  }
})

bootstrap()
