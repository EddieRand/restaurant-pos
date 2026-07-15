/**
 * Dev mock layer — activated when VITE_MOCK_AUTH=true.
 * Intercepts axios requests via an adapter so no real HTTP calls go out.
 */
import type { InternalAxiosRequestConfig, AxiosResponse } from 'axios'
type AxiosRequestConfig = InternalAxiosRequestConfig
import { apiClient } from './client'

// ── Mock data ─────────────────────────────────────────────────────────────────

const now = Date.now()
const day = 86_400_000

const MOCK_CATEGORIES = [
  { id: 'starters', name: '前菜', sortOrder: 1 },
  { id: 'mains',    name: '主菜', sortOrder: 2 },
  { id: 'desserts', name: '甜品', sortOrder: 3 },
  { id: 'drinks',   name: '饮料', sortOrder: 4 },
  { id: 'other',    name: '其他', sortOrder: 5 },
]

const MOCK_PAYMENT_METHODS = [
  { id: 'pm-cash',   code: 'CASH',   baseType: 'CASH',  displayName: '现金',   color: 'green',   sortOrder: 10, isActive: true },
  { id: 'pm-card',   code: 'CARD',   baseType: 'CARD',  displayName: '银行卡', color: 'blue',    sortOrder: 20, isActive: true },
  { id: 'pm-wechat', code: 'WECHAT', baseType: 'OTHER', displayName: '微信',   color: 'emerald', sortOrder: 30, isActive: true },
  { id: 'pm-alipay', code: 'ALIPAY', baseType: 'OTHER', displayName: '支付宝', color: 'sky',     sortOrder: 40, isActive: true },
]

const MOCK_MENU = [
  { id: 'm1', names: '{"zh-CN":"红烧肉","en-US":"Braised Pork"}',        description: '{"zh-CN":"精选五花肉，慢火红烧，入口即化","en-US":"Slow-braised pork belly, melt-in-your-mouth texture"}', priceMinorUnit: 6800, categoryId: 'mains',  course: 1, isSoldOut: false, allergens: '', modifierGroups: [
    { id: 'mg-m1-1', name: '{"zh":"份量","en":"Size"}', selectionType: 'SINGLE', required: true,  minSelect: 1, maxSelect: 1, options: [
      { id: 'o1', name: '{"zh":"大份","en":"Large"}',  priceAdjustMinorUnit: 500,  isDefault: false, sortOrder: 1 },
      { id: 'o2', name: '{"zh":"中份","en":"Medium"}', priceAdjustMinorUnit: 200,  isDefault: false, sortOrder: 2 },
      { id: 'o3', name: '{"zh":"小份","en":"Small"}',  priceAdjustMinorUnit: 0,    isDefault: true,  sortOrder: 3 },
    ]},
    { id: 'mg-m1-2', name: '{"zh":"加料","en":"Add-ons"}', selectionType: 'MULTIPLE', required: false, minSelect: 0, maxSelect: 3, options: [
      { id: 'o4', name: '{"zh":"加蛋","en":"Add Egg"}',    priceAdjustMinorUnit: 100, isDefault: false, sortOrder: 1 },
      { id: 'o5', name: '{"zh":"加肉","en":"Extra Meat"}', priceAdjustMinorUnit: 300, isDefault: false, sortOrder: 2 },
    ]},
  ], updatedAt: now },
  { id: 'm2', names: '{"zh-CN":"清蒸鱼","en-US":"Steamed Fish"}',         description: '{"zh-CN":"新鲜草鱼，清蒸保留原味","en-US":"Fresh grass carp, steamed to preserve natural flavour"}',         priceMinorUnit: 8800, categoryId: 'mains',  course: 1, isSoldOut: false, allergens: 'fish', modifierGroups: [
    { id: 'mg-m2-1', name: '{"zh":"份量","en":"Size"}', selectionType: 'SINGLE', required: true, minSelect: 1, maxSelect: 1, options: [
      { id: 'o6', name: '{"zh":"大份","en":"Large"}', priceAdjustMinorUnit: 500, isDefault: false, sortOrder: 1 },
      { id: 'o7', name: '{"zh":"小份","en":"Small"}', priceAdjustMinorUnit: 0,   isDefault: true,  sortOrder: 2 },
    ]},
  ], menuProfileIds: ['mp2', 'mp3'], updatedAt: now },
  { id: 'm3', names: '{"zh-CN":"炒青菜","en-US":"Stir-fried Greens"}',    description: '', priceMinorUnit: 2800, categoryId: 'mains',  course: 1, isSoldOut: false, allergens: '', modifierGroups: [], menuProfileIds: ['mp2', 'mp3'], updatedAt: now },
  { id: 'm4', names: '{"zh-CN":"白米饭","en-US":"Steamed Rice"}',          description: '', priceMinorUnit: 500,  categoryId: 'mains',  course: 2, isSoldOut: false, allergens: '', modifierGroups: [], updatedAt: now },
  { id: 'm5', names: '{"zh-CN":"可乐","en-US":"Cola"}',                    description: '', priceMinorUnit: 1200, categoryId: 'drinks', course: 3, isSoldOut: false, allergens: '', modifierGroups: [
    { id: 'mg-m5-1', name: '{"zh":"温度","en":"Temperature"}', selectionType: 'SINGLE', required: false, minSelect: 0, maxSelect: 1, options: [
      { id: 'o8', name: '{"zh":"正常","en":"Regular"}',  priceAdjustMinorUnit: 0, isDefault: true,  sortOrder: 1 },
      { id: 'o9', name: '{"zh":"少冰","en":"Less Ice"}', priceAdjustMinorUnit: 0, isDefault: false, sortOrder: 2 },
      { id: 'o10', name: '{"zh":"去冰","en":"No Ice"}',  priceAdjustMinorUnit: 0, isDefault: false, sortOrder: 3 },
    ]},
  ], menuProfileIds: ['mp4'], updatedAt: now },
  { id: 'm6', names: '{"zh-CN":"茉莉花茶","en-US":"Jasmine Tea"}',         description: '{"zh-CN":"精选云南茉莉花茶包，清香回甘","en-US":"Premium Yunnan jasmine tea, refreshing floral aftertaste"}', priceMinorUnit: 1500, categoryId: 'drinks', course: 3, isSoldOut: false, allergens: '', modifierGroups: [
    { id: 'mg-m6-1', name: '{"zh":"温度","en":"Temperature"}', selectionType: 'SINGLE', required: false, minSelect: 0, maxSelect: 1, options: [
      { id: 'o11', name: '{"zh":"热","en":"Hot"}',       priceAdjustMinorUnit: 0, isDefault: false, sortOrder: 1 },
      { id: 'o12', name: '{"zh":"正常","en":"Regular"}', priceAdjustMinorUnit: 0, isDefault: true,  sortOrder: 2 },
      { id: 'o13', name: '{"zh":"少冰","en":"Less Ice"}', priceAdjustMinorUnit: 0, isDefault: false, sortOrder: 3 },
      { id: 'o14', name: '{"zh":"去冰","en":"No Ice"}',  priceAdjustMinorUnit: 0, isDefault: false, sortOrder: 4 },
    ]},
  ], updatedAt: now },
]

let MOCK_MENU_PROFILES = [
  { id: 'mp1', name: '早餐',     enabled: true,  startTime: '06:00', endTime: '10:30', daysOfWeek: [], channels: [] },
  { id: 'mp2', name: '午市',     enabled: true,  startTime: '11:00', endTime: '14:30', daysOfWeek: [], channels: [] },
  { id: 'mp3', name: '晚市',     enabled: true,  startTime: '17:00', endTime: '22:00', daysOfWeek: [], channels: ['DINE_IN'] },
  { id: 'mp4', name: '外卖专属', enabled: true,  startTime: undefined, endTime: undefined, daysOfWeek: [], channels: ['TAKEAWAY', 'DELIVERY'] },
]

const MOCK_MODIFIER_GROUPS = [
  {
    id: 'mg1',
    name: '{"zh":"份量","en":"Size"}',
    selectionType: 'SINGLE',
    required: true,
    minSelect: 1,
    maxSelect: 1,
    options: [
      { id: 'mgo1', name: '{"zh":"小份","en":"Small"}',  priceAdjustMinorUnit: 0,    isDefault: true,  sortOrder: 1 },
      { id: 'mgo2', name: '{"zh":"中份","en":"Medium"}', priceAdjustMinorUnit: 200,  isDefault: false, sortOrder: 2 },
      { id: 'mgo3', name: '{"zh":"大份","en":"Large"}',  priceAdjustMinorUnit: 500,  isDefault: false, sortOrder: 3 },
    ],
  },
  {
    id: 'mg2',
    name: '{"zh":"温度","en":"Temperature"}',
    selectionType: 'SINGLE',
    required: false,
    minSelect: 0,
    maxSelect: 1,
    options: [
      { id: 'mgo4', name: '{"zh":"正常","en":"Regular"}',   priceAdjustMinorUnit: 0, isDefault: true,  sortOrder: 1 },
      { id: 'mgo5', name: '{"zh":"少冰","en":"Less Ice"}',  priceAdjustMinorUnit: 0, isDefault: false, sortOrder: 2 },
      { id: 'mgo6', name: '{"zh":"去冰","en":"No Ice"}',    priceAdjustMinorUnit: 0, isDefault: false, sortOrder: 3 },
      { id: 'mgo7', name: '{"zh":"热饮","en":"Hot"}',       priceAdjustMinorUnit: 0, isDefault: false, sortOrder: 4 },
    ],
  },
  {
    id: 'mg3',
    name: '{"zh":"加料","en":"Add-ons"}',
    selectionType: 'MULTIPLE',
    required: false,
    minSelect: 0,
    maxSelect: 3,
    options: [
      { id: 'mgo8',  name: '{"zh":"加蛋","en":"Add Egg"}',    priceAdjustMinorUnit: 100, isDefault: false, sortOrder: 1 },
      { id: 'mgo9',  name: '{"zh":"加肉","en":"Extra Meat"}', priceAdjustMinorUnit: 300, isDefault: false, sortOrder: 2 },
      { id: 'mgo10', name: '{"zh":"加粉","en":"Extra Noodles"}', priceAdjustMinorUnit: 200, isDefault: false, sortOrder: 3 },
    ],
  },
]

let MOCK_QR_ORDERING_CONFIG = {
  enabled: true,
  supportedOrderTypes: ['DINE_IN', 'TAKEAWAY', 'DELIVERY'],
  menuOnlyMode: false,
  paymentTiming: 'PAY_AT_END',
  firePolicy: 'BY_ORDER_TYPE',
  customerIdentityPolicy: 'BY_ORDER_TYPE',
}

let MOCK_QR_CODES = [
  {
    code: 'demo-table-1',
    scope: 'TABLE',
    tableId: 'table-1',
    enabled: true,
    expiresAt: null,
    createdAt: now,
    updatedAt: now,
  },
]

const MOCK_COMBOS = [
  {
    id: 'c1',
    names: '{"zh-CN":"双人套餐","en-US":"Duo Set"}',
    comboPriceMinorUnit: 13800,
    isActive: true,
    components: [
      { id: 'cc1', comboId: 'c1', menuItemId: 'm1', quantity: 1, sortOrder: 0 },
      { id: 'cc2', comboId: 'c1', menuItemId: 'm3', quantity: 1, sortOrder: 1 },
      { id: 'cc3', comboId: 'c1', menuItemId: 'm4', quantity: 2, sortOrder: 2 },
    ],
  },
]

const MOCK_TIERS = [
  { id: 'tier1', name: '普通会员', minPoints: 0,    benefits: ['积分兑换优惠'],                            color: 'gray',   discountPermille: 0,   pointsMultiplier: 100 },
  { id: 'tier2', name: '银卡会员', minPoints: 500,  benefits: ['9.5折优惠', '积分1.2倍'],                 color: 'blue',   discountPermille: 50,  pointsMultiplier: 120 },
  { id: 'tier3', name: '金卡会员', minPoints: 2000, benefits: ['9折优惠', '积分1.5倍', '生日双倍积分'],   color: 'amber',  discountPermille: 100, pointsMultiplier: 150 },
  { id: 'tier4', name: '钻石会员', minPoints: 5000, benefits: ['8.5折优惠', '积分2倍', '专属客服', '生日礼包'], color: 'purple', discountPermille: 150, pointsMultiplier: 200 },
]

const MOCK_CUSTOMERS = [
  { id: 'cust1', name: '张伟',   phone: '13800138001', email: 'zhangwei@example.com', gender: 'M', birthday: '03-15', registeredAt: now - 365*day, lastVisitAt: now - 2*day,   totalVisits: 42, totalSpendMinorUnit: 685000, loyaltyPoints: 3200, membershipTierId: 'tier3', tags: ['VIP', '常客'],       notes: '偏好靠窗座位' },
  { id: 'cust2', name: '李娜',   phone: '13900139001', email: '',                     gender: 'F', birthday: '06-22', registeredAt: now - 180*day, lastVisitAt: now - 1*day,   totalVisits: 18, totalSpendMinorUnit: 198000, loyaltyPoints: 980,  membershipTierId: 'tier2', tags: ['素食'],               notes: '' },
  { id: 'cust3', name: '王磊',   phone: '13700137001', email: 'wanglei@example.com',  gender: 'M', birthday: '11-08', registeredAt: now - 90*day,  lastVisitAt: now - 30*day,  totalVisits: 5,  totalSpendMinorUnit: 42000,  loyaltyPoints: 210,  membershipTierId: 'tier1', tags: [],                     notes: '' },
  { id: 'cust4', name: '陈静',   phone: '13600136001', email: '',                     gender: 'F', birthday: '01-30', registeredAt: now - 60*day,  lastVisitAt: now - 8*day,   totalVisits: 11, totalSpendMinorUnit: 93000,  loyaltyPoints: 465,  membershipTierId: 'tier1', tags: ['过敏花生'],           notes: '花生过敏，请务必注意' },
  { id: 'cust5', name: '刘洋',   phone: '13500135001', email: 'liuyang@example.com',  gender: 'M', birthday: '07-04', registeredAt: now - 400*day, lastVisitAt: now - 45*day,  totalVisits: 28, totalSpendMinorUnit: 312000, loyaltyPoints: 1560, membershipTierId: 'tier2', tags: ['商务宴请'],           notes: '' },
  { id: 'cust6', name: '赵敏',   phone: '13400134001', email: '',                     gender: 'F', birthday: '09-18', registeredAt: now - 500*day, lastVisitAt: now - 70*day,  totalVisits: 62, totalSpendMinorUnit: 891000, loyaltyPoints: 6100, membershipTierId: 'tier4', tags: ['VIP', '老顾客'],     notes: '每次都点红烧肉' },
  { id: 'cust7', name: '孙浩',   phone: '13300133001', email: '',                     gender: 'M', birthday: '04-25', registeredAt: now - 20*day,  lastVisitAt: now - 20*day,  totalVisits: 1,  totalSpendMinorUnit: 8800,   loyaltyPoints: 44,   membershipTierId: 'tier1', tags: ['新客'],               notes: '' },
  { id: 'cust8', name: '周芳',   phone: '13200132001', email: 'zhoufang@example.com', gender: 'F', birthday: '12-03', registeredAt: now - 280*day, lastVisitAt: now - 62*day,  totalVisits: 9,  totalSpendMinorUnit: 67000,  loyaltyPoints: 335,  membershipTierId: 'tier1', tags: [],                     notes: '' },
]

const MOCK_LOYALTY_TX = [
  { id: 'ltx1',  customerId: 'cust1', orderId: 'ord-1',  type: 'EARN',   points: 200,  description: '消费获积分',    createdAt: now - 2*day },
  { id: 'ltx2',  customerId: 'cust1', orderId: undefined, type: 'ADJUST', points: -100, description: '活动补扣',      createdAt: now - 5*day },
  { id: 'ltx3',  customerId: 'cust1', orderId: 'ord-3',  type: 'EARN',   points: 180,  description: '消费获积分',    createdAt: now - 10*day },
  { id: 'ltx4',  customerId: 'cust1', orderId: undefined, type: 'REDEEM', points: -500, description: '积分兑换优惠券', createdAt: now - 15*day },
  { id: 'ltx5',  customerId: 'cust2', orderId: 'ord-2',  type: 'EARN',   points: 120,  description: '消费获积分',    createdAt: now - 1*day },
  { id: 'ltx6',  customerId: 'cust6', orderId: 'ord-5',  type: 'EARN',   points: 400,  description: '消费获积分（金卡2倍）', createdAt: now - 70*day },
]

const MOCK_CAMPAIGNS = [
  { id: 'camp1', name: '春节回馈活动', type: 'COUPON_PUSH',   targetSegment: 'ALL',           targetTierId: undefined, couponId: 'cp1', message: '感谢您一直以来的支持，春节期间送您专属优惠券！', scheduledAt: undefined, status: 'SENT',      sentCount: 156, createdAt: now - 30*day },
  { id: 'camp2', name: '金卡专属活动', type: 'COUPON_PUSH',   targetSegment: 'TIER',          targetTierId: 'tier3',   couponId: 'cp2', message: '尊贵的金卡会员，您有一张专属满减券等待领取！',     scheduledAt: now + 2*day, status: 'SCHEDULED', sentCount: 0,   createdAt: now - 2*day },
  { id: 'camp3', name: '生日祝福推送', type: 'IN_APP_NOTICE', targetSegment: 'BIRTHDAY_MONTH', targetTierId: undefined, couponId: undefined, message: '祝您生日快乐！本月到店可享受双倍积分。',        scheduledAt: undefined, status: 'DRAFT',     sentCount: 0,   createdAt: now - 1*day },
]

// ── Tables ────────────────────────────────────────────────────────────────────

// ── Shifts ────────────────────────────────────────────────────────────────────

let MOCK_SHIFTS = [
  {
    id: 'sh1', name: '午市', startTime: '11:30', endTime: '14:30',
    daysOfWeek: [], maxCovers: 60, coverGoal: 50,
    defaultTurnTimeMinutes: 90,
    turnTimeByPartySize: [{ partySize: 2, minutes: 75 }, { partySize: 4, minutes: 90 }, { partySize: 6, minutes: 105 }],
    slotIntervalMinutes: 30, bookingCutoffMinutes: 60, enabled: true,
  },
  {
    id: 'sh2', name: '晚市', startTime: '17:30', endTime: '21:30',
    daysOfWeek: [], maxCovers: 80, coverGoal: 70,
    defaultTurnTimeMinutes: 90,
    turnTimeByPartySize: [{ partySize: 2, minutes: 75 }, { partySize: 4, minutes: 90 }, { partySize: 6, minutes: 110 }, { partySize: 8, minutes: 120 }],
    slotIntervalMinutes: 30, bookingCutoffMinutes: 120, enabled: true,
  },
  {
    id: 'sh3', name: '早午餐', startTime: '09:00', endTime: '11:30',
    daysOfWeek: [0, 6], maxCovers: 40, coverGoal: 35,
    defaultTurnTimeMinutes: 75,
    turnTimeByPartySize: [],
    slotIntervalMinutes: 30, bookingCutoffMinutes: 30, enabled: true,
  },
]

// ── Special Days ──────────────────────────────────────────────────────────────

let MOCK_SPECIAL_DAYS = [
  { id: 'sd1', date: '2026-06-21', label: '父亲节', maxCoversOverride: undefined, closed: false },
  { id: 'sd2', date: '2026-07-01', label: '建党节', maxCoversOverride: 100, closed: false },
  { id: 'sd3', date: '2026-10-01', label: '国庆节', maxCoversOverride: 120, closed: false },
]

// ── Reservations ──────────────────────────────────────────────────────────────

const MOCK_RESERVATIONS = (() => {
  const today = new Date(); const todayStr = today.toISOString().split('T')[0]
  const tomorrow = new Date(today); tomorrow.setDate(today.getDate() + 1)
  const tomorrowStr = tomorrow.toISOString().split('T')[0]
  const code = () => Math.random().toString(36).slice(2,8).toUpperCase()
  return [
    // ── Lunch today ──
    { id: 'rsv1', customerName: '张伟',   phone: '13800138001', customerId: 'cust1', partySize: 4, date: todayStr,    time: '11:30', tableId: 't3', shiftId: 'sh1', source: 'ONLINE',    status: 'SEATED',    guestTags: [],             estimatedDurationMinutes: 90,  internalNotes: '',                  notes: '靠窗座位',    confirmationCode: code(), seatedAt: now - 25*60*1000, createdAt: now - 2*day },
    { id: 'rsv2', customerName: '陈静',   phone: '13600136001', customerId: 'cust4', partySize: 2, date: todayStr,    time: '11:30', tableId: 't1', shiftId: 'sh1', source: 'PHONE',     status: 'SEATED',    guestTags: ['FIRST_TIME'], estimatedDurationMinutes: 75,  internalNotes: '花生过敏，注意',     notes: '',            confirmationCode: code(), seatedAt: now - 30*60*1000, createdAt: now - 3*day },
    { id: 'rsv3', customerName: '王芳',   phone: '13700137001', customerId: 'cust2', partySize: 3, date: todayStr,    time: '12:00', tableId: 't7', shiftId: 'sh1', source: 'ONLINE',    status: 'CONFIRMED', guestTags: ['BIRTHDAY'],   estimatedDurationMinutes: 90,  internalNotes: '准备生日蛋糕',       notes: '',            confirmationCode: code(), createdAt: now - 1*day },
    { id: 'rsv4', customerName: '陈东',   phone: '13500135001', customerId: undefined, partySize: 2, date: todayStr,  time: '12:00', tableId: 't2', shiftId: 'sh1', source: 'WALK_IN',   status: 'CONFIRMED', guestTags: [],             estimatedDurationMinutes: 75,  internalNotes: '',                  notes: '',            confirmationCode: code(), createdAt: now - 3600000 },
    { id: 'rsv5', customerName: '刘洋',   phone: '13400134001', customerId: 'cust5', partySize: 6, date: todayStr,    time: '12:30', tableId: 't9', shiftId: 'sh1', source: 'CONCIERGE', status: 'CONFIRMED', guestTags: ['VIP'],        estimatedDurationMinutes: 120, internalNotes: '商务宴请，提前备好红酒', notes: '不吃香菜', confirmationCode: code(), createdAt: now - 5*day },
    { id: 'rsv6', customerName: '赵家',   phone: '13300133001', customerId: undefined, partySize: 4, date: todayStr,  time: '13:00', tableId: 't4', shiftId: 'sh1', source: 'ONLINE',    status: 'PENDING',   guestTags: ['ANNIVERSARY'], estimatedDurationMinutes: 90, internalNotes: '',                  notes: '结婚纪念日', confirmationCode: code(), createdAt: now - 2*60*1000 },
    { id: 'rsv7', customerName: '前客户', phone: '13999999999', customerId: undefined, partySize: 2, date: todayStr,  time: '11:30', tableId: 't8', shiftId: 'sh1', source: 'ONLINE',    status: 'NO_SHOW',   guestTags: [],             estimatedDurationMinutes: 75,  internalNotes: '',                  notes: '',            confirmationCode: code(), createdAt: now - 3*day },
    // ── Dinner today ──
    { id: 'rsv8',  customerName: '李梅',   phone: '13200132001', customerId: 'cust3', partySize: 2, date: todayStr,   time: '17:30', tableId: 't1', shiftId: 'sh2', source: 'ONLINE',    status: 'CONFIRMED', guestTags: [],             estimatedDurationMinutes: 75,  internalNotes: '',                  notes: '',            confirmationCode: code(), createdAt: now - 2*day },
    { id: 'rsv9',  customerName: '孙飞',   phone: '13100131001', customerId: undefined, partySize: 4, date: todayStr, time: '18:00', tableId: 't3', shiftId: 'sh2', source: 'PHONE',     status: 'CONFIRMED', guestTags: [],             estimatedDurationMinutes: 90,  internalNotes: '',                  notes: '4岁小孩，要宝宝椅', confirmationCode: code(), createdAt: now - 1*day },
    { id: 'rsv10', customerName: '吴婷',   phone: '13011301001', customerId: 'cust6', partySize: 5, date: todayStr,   time: '18:00', tableId: 't9', shiftId: 'sh2', source: 'ONLINE',    status: 'CONFIRMED', guestTags: ['BIRTHDAY'],   estimatedDurationMinutes: 105, internalNotes: '',                  notes: '',            confirmationCode: code(), createdAt: now - 4*day },
    { id: 'rsv11', customerName: '郑先生', phone: '18900189001', customerId: undefined, partySize: 2, date: todayStr, time: '18:30', tableId: 't2', shiftId: 'sh2', source: 'ONLINE',    status: 'PENDING',   guestTags: ['ANNIVERSARY'], estimatedDurationMinutes: 75, internalNotes: '',                  notes: '20周年', confirmationCode: code(), createdAt: now - 60*60*1000 },
    { id: 'rsv12', customerName: '陈总',   phone: '13800130001', customerId: 'cust7', partySize: 8, date: todayStr,   time: '19:00', tableId: 't9', shiftId: 'sh2', source: 'CONCIERGE', status: 'CONFIRMED', guestTags: ['VIP'],        estimatedDurationMinutes: 120, internalNotes: '重要客户',           notes: '',            confirmationCode: code(), createdAt: now - 6*day },
    { id: 'rsv13', customerName: '冯家',   phone: '13700137002', customerId: undefined, partySize: 4, date: todayStr, time: '19:30', tableId: 't5', shiftId: 'sh2', source: 'ONLINE',    status: 'CONFIRMED', guestTags: [],             estimatedDurationMinutes: 90,  internalNotes: '',                  notes: '',            confirmationCode: code(), createdAt: now - 3*day },
    { id: 'rsv14', customerName: '黄小姐', phone: '13600136002', customerId: undefined, partySize: 3, date: todayStr, time: '20:00', tableId: 't4', shiftId: 'sh2', source: 'PHONE',     status: 'CONFIRMED', guestTags: [],             estimatedDurationMinutes: 90,  internalNotes: '',                  notes: '',            confirmationCode: code(), createdAt: now - 1*day },
    { id: 'rsv15', customerName: '周先生', phone: '13500135002', customerId: undefined, partySize: 4, date: todayStr, time: '20:30', tableId: 't7', shiftId: 'sh2', source: 'ONLINE',    status: 'PENDING',   guestTags: [],             estimatedDurationMinutes: 90,  internalNotes: '',                  notes: '',            confirmationCode: code(), createdAt: now - 90*60*1000 },
    // ── Tomorrow ──
    { id: 'rsv16', customerName: '王磊',   phone: '13700137003', customerId: 'cust3', partySize: 3, date: tomorrowStr, time: '12:00', tableId: 't4', shiftId: 'sh1', source: 'ONLINE',    status: 'CONFIRMED', guestTags: [],             estimatedDurationMinutes: 90,  internalNotes: '', notes: '', confirmationCode: code(), createdAt: now - 2*day },
    { id: 'rsv17', customerName: '马总',   phone: '13400134002', customerId: undefined, partySize: 6, date: tomorrowStr, time: '19:00', tableId: 't9', shiftId: 'sh2', source: 'PHONE', status: 'CONFIRMED', guestTags: ['VIP'], estimatedDurationMinutes: 120, internalNotes: '', notes: '商务', confirmationCode: code(), createdAt: now - 1*day },
  ]
})()

// ── Waitlist ──────────────────────────────────────────────────────────────────

let MOCK_WAITLIST: Array<{
  id: string; customerName: string; phone: string; partySize: number; requestedAt: number
  quotedWaitMinutes: number; status: string; preferences: string[]; notes: string
  tableId: string | undefined; notifiedAt: number | undefined; expiresAt: number | undefined
  seatedAt: number | undefined
}> = [
  { id: 'wl1', customerName: '孙小姐', phone: '13900139001', partySize: 3, requestedAt: now - 22*60*1000, quotedWaitMinutes: 25, status: 'WAITING',  preferences: ['WINDOW'],           notes: '有小孩', tableId: undefined, notifiedAt: undefined, expiresAt: undefined, seatedAt: undefined },
  { id: 'wl2', customerName: '周先生', phone: '13800138002', partySize: 2, requestedAt: now - 38*60*1000, quotedWaitMinutes: 30, status: 'NOTIFIED', preferences: [],                    notes: '',       tableId: 't8',    notifiedAt: now - 8*60*1000, expiresAt: now + 2*60*1000, seatedAt: undefined },
  { id: 'wl3', customerName: '吴一家', phone: '13700137004', partySize: 4, requestedAt: now - 8*60*1000,  quotedWaitMinutes: 40, status: 'WAITING',  preferences: ['INDOOR', 'QUIET'],  notes: '',       tableId: undefined, notifiedAt: undefined, expiresAt: undefined, seatedAt: undefined },
]

const MOCK_COUPONS = [
  { id: 'cp1', code: 'WELCOME10', type: 'PERCENT', value: 10, expiresAt: now + 30 * day, isActive: true },
  { id: 'cp2', code: 'SAVE20',    type: 'FIXED',   value: 2000, expiresAt: 0,            isActive: true },
]

function minutesBetween(start: string, end: string): number {
  const [sh, sm] = start.split(':').map(Number)
  const [eh, em] = end.split(':').map(Number)
  return Math.max(0, (eh * 60 + em) - (sh * 60 + sm))
}

const todayStr = new Date(now).toISOString().slice(0, 10)
const tomorrowStr = new Date(now + day).toISOString().slice(0, 10)

const MOCK_SCHEDULES = [
  { id: 'sch1', operatorId: 'u2', operatorName: '李收银', date: todayStr, startTime: '09:00', endTime: '17:00', notes: '' },
  { id: 'sch2', operatorId: 'u3', operatorName: '王服务', date: todayStr, startTime: '10:00', endTime: '18:00', notes: '' },
  { id: 'sch3', operatorId: 'u2', operatorName: '李收银', date: tomorrowStr, startTime: '09:00', endTime: '17:00', notes: '' },
]

const MOCK_GIFT_CARDS = [
  { id: 'gc1', code: 'GIFT1000', balanceMinorUnit: 10000, customerId: null, isActive: true, createdAt: now - 10 * day, updatedAt: now - 10 * day },
  { id: 'gc2', code: 'GIFT5000', balanceMinorUnit: 35000, customerId: null, isActive: true, createdAt: now - 5 * day, updatedAt: now - 2 * day },
]

const MOCK_GIFT_CARD_TRANSACTIONS: Record<string, any[]> = {
  gc1: [
    { id: 'gct1', giftCardId: 'gc1', type: 'ISSUE', amountMinorUnit: 10000, orderId: null, operatorId: '', note: '开卡', createdAt: now - 10 * day },
  ],
  gc2: [
    { id: 'gct2', giftCardId: 'gc2', type: 'ISSUE', amountMinorUnit: 50000, orderId: null, operatorId: '', note: '开卡', createdAt: now - 5 * day },
    { id: 'gct3', giftCardId: 'gc2', type: 'REDEEM', amountMinorUnit: -15000, orderId: 'ord-1', operatorId: 'u2', note: '', createdAt: now - 2 * day },
  ],
}

const ORDER_OPERATORS = [
  { id: 'u1', name: '张经理' },
  { id: 'u2', name: '李收银' },
  { id: 'u3', name: '王服务' },
]
const ORDER_PAYMENT_METHODS = ['CASH', 'CARD', 'WECHAT', 'ALIPAY']

const makeOrders = () => Array.from({ length: 18 }, (_, i) => ({
  id: `ord-${i + 1}`,
  type: 'DINE_IN',
  tableId: `T${(i % 8) + 1}`,
  guestCount: (i % 4) + 1,
  status: ['SETTLED', 'SETTLED', 'SETTLED', 'IN_PROGRESS', 'READY_TO_CHECKOUT'][i % 5],
  subtotalMinorUnit:    (8000 + i * 1500),
  taxTotalMinorUnit:    Math.round((8000 + i * 1500) * 0.06),
  serviceChargeMinorUnit: 0,
  tipMinorUnit: 0,
  discountMinorUnit: i % 3 === 0 ? 1000 : 0,
  orderNotes: i % 4 === 0 ? '少辣' : '',
  createdAt: now - (i + 1) * 1800_000,
  updatedAt: now - i * 900_000,
  operatorId: ORDER_OPERATORS[i % ORDER_OPERATORS.length].id,
  operatorName: ORDER_OPERATORS[i % ORDER_OPERATORS.length].name,
  paymentMethod: ORDER_PAYMENT_METHODS[i % ORDER_PAYMENT_METHODS.length],
}))

const MOCK_ORDERS = makeOrders()

const MOCK_ORDER_PAYMENTS = new Map<string, import('./reports').Payment[]>()

function getMockOrderPayments(order: ReturnType<typeof makeOrders>[number]) {
  let payments = MOCK_ORDER_PAYMENTS.get(order.id)
  if (!payments) {
    payments = [{
      id: `p1-${order.id}`,
      orderId: order.id,
      amountMinorUnit: order.subtotalMinorUnit,
      method: order.paymentMethod,
      status: 'PAID',
      operatorId: order.operatorId,
      createdAt: order.updatedAt,
    }]
    MOCK_ORDER_PAYMENTS.set(order.id, payments)
  }
  return payments
}

function buildOrderDetail(id: string) {
  const order = MOCK_ORDERS.find(o => o.id === id) ?? MOCK_ORDERS[0]
  return {
    order,
    items: [
      { id: 'i1', orderId: order.id, menuItemId: 'm1', menuItemNameSnapshot: '红烧肉', quantity: 1, unitPriceMinorUnit: 6800, course: 1, status: 'SERVED', notes: '', allergenSnapshot: '' },
      { id: 'i2', orderId: order.id, menuItemId: 'm4', menuItemNameSnapshot: '白米饭', quantity: 2, unitPriceMinorUnit: 500, course: 2, status: 'SERVED', notes: '', allergenSnapshot: '' },
    ],
    payments: getMockOrderPayments(order),
  }
}

const MOCK_SHIFT: import('./reports').ShiftReport = {
  fromMs: now - day,
  toMs: now,
  orderCount: 42,
  grossRevenueMinorUnit:  328600,
  netRevenueMinorUnit:    302300,
  totalDiscountMinorUnit: 12000,
  totalTipMinorUnit:      8200,
  totalServiceChargeMinorUnit: 0,
  totalTaxMinorUnit:      18100,
  totalGuestCount:        97,
  averageOrderValueMinorUnit: 7824,
  averageSpendPerGuestMinorUnit: 3116,
  paymentMethodBreakdown: { CASH: 98600, CARD: 156300, WECHAT: 47400, ALIPAY: 26300 },
}

// ── Mock Trend Data (30 days) ────────────────────────────────────────────────
function generateMockTrendData(days: number): import('./reports').TrendDataPoint[] {
  const pts: import('./reports').TrendDataPoint[] = []
  const base = new Date()
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date(base)
    d.setDate(d.getDate() - i)
    const dateStr = d.toISOString().slice(0, 10)
    // Weekday boost (Fri-Sun busier)
    const dow = d.getDay()
    const weekendFactor = dow === 0 || dow === 6 ? 1.35 : dow === 5 ? 1.2 : 1.0
    const orderCount = Math.round((30 + Math.random() * 25) * weekendFactor)
    const netRev = Math.round(orderCount * (6500 + Math.random() * 2000))
    const grossRev = Math.round(netRev * 1.08)
    const guestCount = Math.round(orderCount * (2.0 + Math.random() * 0.8))
    const discount = Math.round(netRev * 0.03)
    const tax = Math.round(netRev * 0.08)
    pts.push({
      date: dateStr,
      grossRevenueMinorUnit: grossRev,
      netRevenueMinorUnit: netRev,
      orderCount,
      guestCount,
      averageOrderValueMinorUnit: Math.round(netRev / orderCount),
      averageSpendPerGuestMinorUnit: Math.round(netRev / guestCount),
      refundMinorUnit: 0,
      discountMinorUnit: discount,
      taxMinorUnit: tax,
    })
  }
  return pts
}

const MOCK_TREND_30 = generateMockTrendData(30)
const MOCK_TREND_15 = MOCK_TREND_30.slice(-15)
const MOCK_TREND_7 = MOCK_TREND_30.slice(-7)

function buildTrendReport(days: number): import('./reports').TrendReport {
  const data = days <= 7 ? MOCK_TREND_7 : days <= 15 ? MOCK_TREND_15 : MOCK_TREND_30
  const totalNet = data.reduce((s, p) => s + p.netRevenueMinorUnit, 0)
  const totalGross = data.reduce((s, p) => s + p.grossRevenueMinorUnit, 0)
  const totalOrders = data.reduce((s, p) => s + p.orderCount, 0)
  const totalGuests = data.reduce((s, p) => s + p.guestCount, 0)
  // Simulate ~12% WoW growth
  const growth = 12.3
  return {
    dataPoints: data,
    summary: {
      totalGrossRevenue: totalGross,
      totalNetRevenue: totalNet,
      totalOrderCount: totalOrders,
      totalGuestCount: totalGuests,
      avgOrderValue: Math.round(totalNet / totalOrders),
      avgSpendPerGuest: Math.round(totalNet / totalGuests),
      growthFromPrevious: growth,
    },
  }
}

const MOCK_USERS = [
  { id: 'u1', displayName: '张经理', role: 'ADMIN',   isActive: true,  createdAt: now - 90 * day },
  { id: 'u2', displayName: '李收银', role: 'CASHIER', isActive: true,  createdAt: now - 60 * day },
  { id: 'u3', displayName: '王服务', role: 'WAITER',  isActive: true,  createdAt: now - 30 * day },
  { id: 'u4', displayName: '陈备用', role: 'CASHIER', isActive: false, createdAt: now - 10 * day },
]

const MOCK_INGREDIENTS = [
  { id: 'ing1', name: '猪五花肉',   category: '肉类', unit: 'kg',  safetyStock: 5,   currentStock: 8.5,  createdAt: now - 30 * day },
  { id: 'ing2', name: '草鱼',       category: '水产', unit: 'kg',  safetyStock: 3,   currentStock: 2.0,  createdAt: now - 30 * day },
  { id: 'ing3', name: '青江菜',     category: '蔬菜', unit: 'kg',  safetyStock: 2,   currentStock: 4.5,  createdAt: now - 25 * day },
  { id: 'ing4', name: '大米',       category: '粮食', unit: 'kg',  safetyStock: 20,  currentStock: 15.0, createdAt: now - 25 * day },
  { id: 'ing5', name: '生抽酱油',   category: '调料', unit: 'L',   safetyStock: 2,   currentStock: 3.5,  createdAt: now - 20 * day },
  { id: 'ing6', name: '食用油',     category: '调料', unit: 'L',   safetyStock: 5,   currentStock: 1.5,  createdAt: now - 20 * day },
  { id: 'ing7', name: '可口可乐',   category: '饮料', unit: '罐',  purchaseUnit: '箱', purchaseUnitFactor: 24, safetyStock: 24,  currentStock: 36,   createdAt: now - 15 * day },
  { id: 'ing8', name: '茉莉花茶包', category: '饮料', unit: '盒',  safetyStock: 5,   currentStock: 3,    createdAt: now - 15 * day },
  { id: 'ing9', name: '一次性餐具', category: '耗材', unit: '套',  safetyStock: 100, currentStock: 240,  createdAt: now - 10 * day },
]

const MOCK_OUTBOUND_ORDERS: Array<{
  id: string; status: 'DRAFT' | 'CONFIRMED'; type: 'ISSUE' | 'WASTE' | 'GIFT' | 'OTHER'
  note: string; createdAt: number; confirmedAt?: number
  items: Array<{ id: string; orderId: string; ingredientId: string; qty: number }>
}> = [
  {
    id: 'ob1', status: 'CONFIRMED', type: 'WASTE', note: '试菜报损', createdAt: now - 20 * day, confirmedAt: now - 20 * day,
    items: [
      { id: 'obi1', orderId: 'ob1', ingredientId: 'ing1', qty: 0.5 },
      { id: 'obi2', orderId: 'ob1', ingredientId: 'ing3', qty: 1 },
    ],
  },
  {
    id: 'ob2', status: 'CONFIRMED', type: 'GIFT', note: '招待贵宾', createdAt: now - 10 * day, confirmedAt: now - 10 * day,
    items: [
      { id: 'obi3', orderId: 'ob2', ingredientId: 'ing2', qty: 2 },
    ],
  },
  {
    id: 'ob3', status: 'DRAFT', type: 'ISSUE', note: '厨房领用', createdAt: now - 1 * day,
    items: [
      { id: 'obi4', orderId: 'ob3', ingredientId: 'ing1', qty: 1 },
      { id: 'obi5', orderId: 'ob3', ingredientId: 'ing4', qty: 3 },
    ],
  },
]

// Stocktakes are keyed by id; items snapshot MOCK_INGREDIENTS at creation time
const MOCK_STOCKTAKES: Array<{
  id: string; status: 'DRAFT' | 'SUBMITTED'; note: string; createdAt: number; submittedAt?: number
  items: Array<{ id: string; orderId: string; ingredientId: string; systemQty: number; actualQty: number }>
}> = [
  {
    id: 'st1', status: 'SUBMITTED', note: '2025-05 月度盘点', createdAt: now - 35 * day, submittedAt: now - 34 * day,
    items: [
      { id: 'sti1', orderId: 'st1', ingredientId: 'ing1', systemQty: 10, actualQty: 9.5 },
      { id: 'sti2', orderId: 'st1', ingredientId: 'ing2', systemQty: 20, actualQty: 20 },
      { id: 'sti3', orderId: 'st1', ingredientId: 'ing3', systemQty: 5,  actualQty: 4 },
    ],
  },
]

const MOCK_SUPPLIERS = [
  { id: 'sup1', name: '鲜源食材配送', contact: '李经理', phone: '138-0000-1111', note: '每周一三五配送', createdAt: now - 60 * day },
  { id: 'sup2', name: '粮油批发市场', contact: '王老板', phone: '139-0000-2222', note: '大宗粮油采购', createdAt: now - 50 * day },
  { id: 'sup3', name: '饮料经销商',   contact: '张业务', phone: '137-0000-3333', note: '可口可乐授权经销', createdAt: now - 40 * day },
]

const MOCK_PURCHASE_ORDERS = [
  {
    id: 'po1', status: 'CONFIRMED', supplier: '鲜源食材配送', supplierId: 'sup1', note: '每周常规采购',
    createdAt: now - 20 * day, confirmedAt: now - 20 * day,
    items: [
      { id: 'poi1', orderId: 'po1', ingredientId: 'ing1', qty: 10, unitCost: 3800 },
      { id: 'poi2', orderId: 'po1', ingredientId: 'ing2', qty: 5,  unitCost: 2200 },
      { id: 'poi3', orderId: 'po1', ingredientId: 'ing3', qty: 8,  unitCost: 600  },
    ],
  },
  {
    id: 'po2', status: 'CONFIRMED', supplier: '粮油批发市场', supplierId: 'sup2', note: '大米食用油补货',
    createdAt: now - 8 * day, confirmedAt: now - 8 * day,
    items: [
      { id: 'poi4', orderId: 'po2', ingredientId: 'ing4', qty: 25, unitCost: 580  },
      { id: 'poi5', orderId: 'po2', ingredientId: 'ing6', qty: 5,  unitCost: 1500 },
    ],
  },
  {
    id: 'po3', status: 'DRAFT', supplier: '饮料经销商', supplierId: 'sup3', note: '待确认',
    createdAt: now - 1 * day, confirmedAt: undefined,
    items: [
      { id: 'poi6', orderId: 'po3', ingredientId: 'ing7', qty: 48, unitCost: 350  },
      { id: 'poi7', orderId: 'po3', ingredientId: 'ing8', qty: 10, unitCost: 1200 },
    ],
  },
]

const MOCK_STOCK_MOVEMENTS = [
  { id: 'mv1',  ingredientId: 'ing1', type: 'IN',     qty: 10,  note: '采购入库',   createdAt: now - 20 * day },
  { id: 'mv2',  ingredientId: 'ing1', type: 'OUT',    qty: 1.5, note: '红烧肉出库', createdAt: now - 15 * day },
  { id: 'mv3',  ingredientId: 'ing1', type: 'OUT',    qty: 2,   note: '日常消耗',   createdAt: now - 7  * day },
  { id: 'mv4',  ingredientId: 'ing1', type: 'IN',     qty: 2,   note: '补货入库',   createdAt: now - 2  * day },
  { id: 'mv5',  ingredientId: 'ing2', type: 'IN',     qty: 5,   note: '采购入库',   createdAt: now - 10 * day },
  { id: 'mv6',  ingredientId: 'ing2', type: 'OUT',    qty: 3,   note: '清蒸鱼出库', createdAt: now - 5  * day },
  { id: 'mv7',  ingredientId: 'ing2', type: 'ADJUST', qty: 0,   note: '盘点调整 -1 kg (损耗)', createdAt: now - 1 * day },
  { id: 'mv8',  ingredientId: 'ing4', type: 'IN',     qty: 25,  note: '采购入库',   createdAt: now - 12 * day },
  { id: 'mv9',  ingredientId: 'ing4', type: 'OUT',    qty: 10,  note: '日常消耗',   createdAt: now - 6  * day },
  { id: 'mv10', ingredientId: 'ing6', type: 'IN',     qty: 5,   note: '采购入库',   createdAt: now - 8  * day },
  { id: 'mv11', ingredientId: 'ing6', type: 'OUT',    qty: 3.5, note: '日常消耗',   createdAt: now - 3  * day },
]

const MOCK_SECTIONS = [
  { id: 's1', name: '室内区', sortOrder: 0 },
  { id: 's2', name: '室外露台', sortOrder: 1 },
  { id: 's3', name: '包厢', sortOrder: 2 },
]

const MOCK_FLOOR_TABLES = [
  { id: 'table-1', name: '1 号桌', sectionId: 's1', capacity: 4, shape: 'square', x: 60, y: 420, w: 120, h: 120 },
  { id: 't1', name: 'T1',   sectionId: 's1', capacity: 4,  shape: 'square', x: 60,  y: 60,  w: 120, h: 120 },
  { id: 't2', name: 'T2',   sectionId: 's1', capacity: 4,  shape: 'square', x: 240, y: 60,  w: 120, h: 120 },
  { id: 't3', name: 'T3',   sectionId: 's1', capacity: 6,  shape: 'round',  x: 420, y: 60,  w: 120, h: 120 },
  { id: 't4', name: 'T4',   sectionId: 's1', capacity: 8,  shape: 'rect',   x: 600, y: 60,  w: 180, h: 120 },
  { id: 't5', name: 'T5',   sectionId: 's1', capacity: 4,  shape: 'square', x: 60,  y: 240, w: 120, h: 120 },
  { id: 't6', name: 'T6',   sectionId: 's1', capacity: 4,  shape: 'square', x: 240, y: 240, w: 120, h: 120 },
  { id: 't7', name: 'P1',   sectionId: 's2', capacity: 4,  shape: 'round',  x: 60,  y: 60,  w: 120, h: 120 },
  { id: 't8', name: 'P2',   sectionId: 's2', capacity: 4,  shape: 'round',  x: 240, y: 60,  w: 120, h: 120 },
  { id: 't9', name: 'VIP1', sectionId: 's3', capacity: 10, shape: 'rect',   x: 60,  y: 60,  w: 200, h: 120 },
]

function mockCustomerUrl(code: string) {
  return `${window.location.origin}/qr/?code=${encodeURIComponent(code)}`
}

function mockTableBinding(tableId: string) {
  const table = MOCK_FLOOR_TABLES.find(t => t.id === tableId)
  if (!table) return null
  const section = MOCK_SECTIONS.find(s => s.id === table.sectionId)
  const tableCodes = MOCK_QR_CODES
    .filter(qr => qr.scope === 'TABLE' && qr.tableId === table.id)
    .sort((a, b) => b.updatedAt - a.updatedAt)
  const currentQr = tableCodes.find(qr => qr.enabled) ?? null
  return {
    tableId: table.id,
    tableName: table.name,
    sectionId: table.sectionId,
    sectionName: section?.name ?? table.sectionId,
    capacity: table.capacity,
    status: 'AVAILABLE',
    currentQr,
    activeCodeCount: tableCodes.filter(qr => qr.enabled).length,
    disabledCodeCount: tableCodes.filter(qr => !qr.enabled).length,
    customerUrl: currentQr ? mockCustomerUrl(currentQr.code) : null,
  }
}

function mockTableBindings() {
  return {
    sections: MOCK_SECTIONS.map(section => ({
      id: section.id,
      name: section.name,
      tables: MOCK_FLOOR_TABLES
        .filter(table => table.sectionId === section.id)
        .map(table => mockTableBinding(table.id))
        .filter(Boolean),
    })),
    unassignedCodes: MOCK_QR_CODES.filter(qr => qr.scope === 'TABLE' && (!qr.tableId || !MOCK_FLOOR_TABLES.some(table => table.id === qr.tableId))),
  }
}

function mockCreateTableQr(tableId: string, reset = false) {
  const table = MOCK_FLOOR_TABLES.find(t => t.id === tableId)
  if (!table) return null
  const nowMs = Date.now()
  if (reset) {
    MOCK_QR_CODES = MOCK_QR_CODES.map(qr => qr.scope === 'TABLE' && qr.tableId === tableId ? { ...qr, enabled: false, updatedAt: nowMs } : qr)
  } else {
    const current = MOCK_QR_CODES.find(qr => qr.scope === 'TABLE' && qr.tableId === tableId && qr.enabled)
    if (current) return mockTableBinding(tableId)
    const disabled = MOCK_QR_CODES.find(qr => qr.scope === 'TABLE' && qr.tableId === tableId && !qr.enabled)
    if (disabled) {
      MOCK_QR_CODES = MOCK_QR_CODES.map(qr => qr.code === disabled.code ? { ...qr, enabled: true, expiresAt: null, updatedAt: nowMs } : qr)
      return mockTableBinding(tableId)
    }
  }
  const code = `table-${tableId}-${Math.random().toString(36).slice(2, 8)}`
  MOCK_QR_CODES = [{
    code,
    scope: 'TABLE',
    tableId,
    enabled: true,
    expiresAt: null,
    createdAt: nowMs,
    updatedAt: nowMs,
  }, ...MOCK_QR_CODES]
  return mockTableBinding(tableId)
}

// BOM: keyed by menuItemId, each entry is an array of { id, ingredientId, qty }
const MOCK_BOM: Record<string, { id: string; ingredientId: string; qty: number }[]> = {
  'm1': [ // 红烧肉: 五花肉300g + 生抽酱油30mL + 食用油20mL
    { id: 'bom1', ingredientId: 'ing1', qty: 0.3 },
    { id: 'bom2', ingredientId: 'ing5', qty: 0.03 },
    { id: 'bom3', ingredientId: 'ing6', qty: 0.02 },
  ],
  'm2': [ // 清蒸鱼: 草鱼400g + 生抽酱油20mL
    { id: 'bom4', ingredientId: 'ing2', qty: 0.4 },
    { id: 'bom5', ingredientId: 'ing5', qty: 0.02 },
  ],
  'm3': [ // 炒青菜: 青江菜200g + 食用油15mL
    { id: 'bom6', ingredientId: 'ing3', qty: 0.2 },
    { id: 'bom7', ingredientId: 'ing6', qty: 0.015 },
  ],
  'm4': [ // 白米饭: 大米150g
    { id: 'bom8', ingredientId: 'ing4', qty: 0.15 },
  ],
  'm5': [ // 可乐: 可口可乐1罐
    { id: 'bom9', ingredientId: 'ing7', qty: 1 },
  ],
  'm6': [ // 茉莉花茶: 茶包1个
    { id: 'bom10', ingredientId: 'ing8', qty: 1 },
  ],
}

const MOCK_SETTINGS = [
  { key: 'regionConfig', value: JSON.stringify({
    country: 'CN',
    currencyCode: 'CNY', currencySymbol: '¥', minorDigits: 2,
    decimalSeparator: '.', groupingSeparator: ',',
    taxRates: [], serviceChargePermille: 0,
    timeZone: 'Asia/Shanghai', locale: 'zh-CN',
    terminalId: 'pos-demo-1',
    receiptConfig: { headerLines: ['示范餐厅', '地址：示范路1号'], footerLines: ['感谢光临'], showTaxId: false, taxId: '' },
  })},
]

// ── Route matcher ─────────────────────────────────────────────────────────────

function mockResponse<T>(data: T, status = 200): AxiosResponse<T> {
  return { data, status, statusText: 'OK', headers: {}, config: { headers: {} } as AxiosRequestConfig }
}

function handleRequest(config: AxiosRequestConfig): AxiosResponse {
  const method = (config.method ?? 'get').toLowerCase()
  const url = config.url ?? ''

  // Auth
  if (url.includes('/auth/login')) {
    return mockResponse({ token: 'mock-token', userId: 'u1', role: 'ADMIN', displayName: '管理员' })
  }

  // Settings — distinguish list vs single key
  if (url.match(/\/admin\/settings\/(.+)/)) {
    const key = url.split('/admin/settings/')[1]
    if (method === 'get') {
      const item = MOCK_SETTINGS.find(s => s.key === key)
      if (!item) return mockResponse({ status: 404 }, 404)
      return mockResponse(item)
    }
    // PUT — actually persist the update
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    const idx = MOCK_SETTINGS.findIndex(s => s.key === key)
    if (idx >= 0) MOCK_SETTINGS[idx] = { key, value: body.value ?? body }
    else MOCK_SETTINGS.push({ key, value: body.value ?? body })
    return mockResponse({ ok: true })
  }
  if (url.match(/\/admin\/settings$/)) {
    return mockResponse(MOCK_SETTINGS)
  }

  // Purchase orders
  if (url.match(/\/admin\/purchase-orders\/(.+)\/confirm/)) {
    const id = url.split('/admin/purchase-orders/')[1].split('/confirm')[0]
    const po = MOCK_PURCHASE_ORDERS.find(p => p.id === id)
    if (po && po.status === 'DRAFT') {
      po.status = 'CONFIRMED'
      po.confirmedAt = Date.now()
      // Create stock movements; apply purchaseUnitFactor when converting to base unit
      for (const item of po.items) {
        const ing = MOCK_INGREDIENTS.find(i => i.id === item.ingredientId)
        const factor = ing?.purchaseUnitFactor ?? 1
        const baseQty = item.qty * factor
        const newMov = { id: `mv${Date.now()}_${item.id}`, ingredientId: item.ingredientId, type: 'IN', qty: baseQty, note: `入库单 #${po.id} ${po.supplier ? '· ' + po.supplier : ''}`.trim(), createdAt: Date.now() }
        MOCK_STOCK_MOVEMENTS.push(newMov)
        if (ing) ing.currentStock = Math.round((ing.currentStock + baseQty) * 1000) / 1000
      }
    }
    return mockResponse(po ?? { ok: false })
  }
  if (url.match(/\/admin\/purchase-orders\/(.+)/) && !url.includes('/confirm')) {
    const id = url.split('/admin/purchase-orders/')[1]
    if (method === 'delete') {
      const i = MOCK_PURCHASE_ORDERS.findIndex(p => p.id === id)
      if (i >= 0) MOCK_PURCHASE_ORDERS.splice(i, 1)
    }
    return mockResponse({ ok: true })
  }
  if (url.match(/\/admin\/purchase-orders$/)) {
    if (method === 'get') return mockResponse([...MOCK_PURCHASE_ORDERS].sort((a, b) => b.createdAt - a.createdAt))
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
      const id = `po${Date.now()}`
      const newPO = {
        id, status: 'DRAFT', supplier: body.supplier ?? '', supplierId: body.supplierId ?? '', note: body.note ?? '',
        createdAt: Date.now(), confirmedAt: undefined,
        items: (body.items ?? []).map((item: { ingredientId: string; qty: number; unitCost: number }, idx: number) => ({
          id: `poi${Date.now()}_${idx}`, orderId: id, ...item,
        })),
      }
      MOCK_PURCHASE_ORDERS.push(newPO)
      return mockResponse(newPO)
    }
    return mockResponse({ ok: true })
  }

  // Stock movements
  if (url.match(/\/admin\/stock-movements$/) || url.match(/\/admin\/stock-movements\?/)) {
    if (method === 'get') {
      const ingredientId = new URLSearchParams(url.split('?')[1] ?? '').get('ingredientId')
      const list = ingredientId
        ? MOCK_STOCK_MOVEMENTS.filter(m => m.ingredientId === ingredientId)
        : [...MOCK_STOCK_MOVEMENTS]
      return mockResponse([...list].sort((a, b) => b.createdAt - a.createdAt))
    }
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
      const newMov = { ...body, id: `mv${Date.now()}`, createdAt: Date.now() }
      MOCK_STOCK_MOVEMENTS.push(newMov)
      // Update ingredient currentStock
      const ing = MOCK_INGREDIENTS.find(i => i.id === body.ingredientId)
      if (ing) {
        if (body.type === 'IN') ing.currentStock = Math.round((ing.currentStock + body.qty) * 100) / 100
        else if (body.type === 'OUT') ing.currentStock = Math.round((ing.currentStock - body.qty) * 100) / 100
        else if (body.type === 'ADJUST') ing.currentStock = body.qty  // qty = new absolute value for ADJUST
      }
      return mockResponse(newMov)
    }
  }

  // Ingredients
  if (url.match(/\/admin\/ingredients\/(.+)/)) {
    const id = url.split('/admin/ingredients/')[1]
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    if (method === 'patch') {
      const idx = MOCK_INGREDIENTS.findIndex(i => i.id === id)
      if (idx >= 0) Object.assign(MOCK_INGREDIENTS[idx], body)
    } else if (method === 'delete') {
      const i = MOCK_INGREDIENTS.findIndex(i => i.id === id)
      if (i >= 0) MOCK_INGREDIENTS.splice(i, 1)
    }
    return mockResponse({ ok: true })
  }
  if (url.match(/\/admin\/ingredients$/)) {
    if (method === 'get') return mockResponse([...MOCK_INGREDIENTS])
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
      const newItem = { ...body, id: `ing${Date.now()}`, createdAt: Date.now() }
      MOCK_INGREDIENTS.push(newItem)
      return mockResponse(newItem)
    }
    return mockResponse({ ok: true })
  }

  // Suppliers
  // Outbound orders
  if (url.match(/\/admin\/outbound-orders\/([^/]+)\/confirm/)) {
    const id = url.match(/\/admin\/outbound-orders\/([^/]+)\/confirm/)![1]
    const ob = MOCK_OUTBOUND_ORDERS.find(o => o.id === id)
    if (ob && ob.status === 'DRAFT') {
      ob.status = 'CONFIRMED'
      ob.confirmedAt = Date.now()
      for (const item of ob.items) {
        const ing = MOCK_INGREDIENTS.find(i => i.id === item.ingredientId)
        if (ing) {
          ing.currentStock = Math.max(0, ing.currentStock - item.qty)
          MOCK_STOCK_MOVEMENTS.push({
            id: `mov-ob-${id}-${item.id}`,
            ingredientId: item.ingredientId,
            type: 'OUT',
            qty: item.qty,
            note: `出库（${ob.type}）${ob.note ? '- ' + ob.note : ''}`,
            createdAt: Date.now(),
          })
        }
      }
    }
    return mockResponse(ob ?? { ok: true })
  }
  if (url.match(/\/admin\/outbound-orders\/([^/]+)$/)) {
    const id = url.match(/\/admin\/outbound-orders\/([^/]+)$/)![1]
    if (method === 'delete') {
      const i = MOCK_OUTBOUND_ORDERS.findIndex(o => o.id === id)
      if (i >= 0 && MOCK_OUTBOUND_ORDERS[i].status === 'DRAFT') MOCK_OUTBOUND_ORDERS.splice(i, 1)
      return mockResponse({ ok: true })
    }
  }
  if (url.match(/\/admin\/outbound-orders$/)) {
    if (method === 'get') return mockResponse([...MOCK_OUTBOUND_ORDERS].sort((a, b) => b.createdAt - a.createdAt))
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
      const id = `ob${Date.now()}`
      const items = (body.items ?? []).map((it: { ingredientId: string; qty: number }, i: number) => ({
        id: `obi-${id}-${i}`, orderId: id, ingredientId: it.ingredientId, qty: it.qty,
      }))
      const newOb = { id, status: 'DRAFT' as const, type: body.type ?? 'ISSUE', note: body.note ?? '', createdAt: Date.now(), items }
      MOCK_OUTBOUND_ORDERS.unshift(newOb)
      return mockResponse(newOb)
    }
  }

  // Stocktakes
  if (url.match(/\/admin\/stocktakes\/([^/]+)\/items\/([^/]+)/)) {
    const [, orderId, itemId] = url.match(/\/admin\/stocktakes\/([^/]+)\/items\/([^/]+)/)!
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    const st = MOCK_STOCKTAKES.find(s => s.id === orderId)
    if (st && method === 'patch') {
      const item = st.items.find(i => i.id === itemId)
      if (item) item.actualQty = Number(body.actualQty ?? item.actualQty)
    }
    return mockResponse({ ok: true })
  }
  if (url.match(/\/admin\/stocktakes\/([^/]+)\/submit/)) {
    const id = url.match(/\/admin\/stocktakes\/([^/]+)\/submit/)![1]
    const st = MOCK_STOCKTAKES.find(s => s.id === id)
    if (st && st.status === 'DRAFT') {
      st.status = 'SUBMITTED'
      st.submittedAt = Date.now()
      // Apply ADJUST movements for each item with a diff
      for (const item of st.items) {
        const diff = item.actualQty - item.systemQty
        if (diff === 0) continue
        const ing = MOCK_INGREDIENTS.find(i => i.id === item.ingredientId)
        if (!ing) continue
        MOCK_STOCK_MOVEMENTS.push({
          id: `mov-st-${id}-${item.id}`,
          ingredientId: item.ingredientId,
          type: 'ADJUST',
          qty: Math.abs(diff),
          note: `盘点调整 (${st.note || id})`,
          createdAt: Date.now(),
        })
        ing.currentStock = item.actualQty
      }
    }
    return mockResponse(st ?? { ok: true })
  }
  if (url.match(/\/admin\/stocktakes\/([^/]+)$/)) {
    const id = url.match(/\/admin\/stocktakes\/([^/]+)$/)![1]
    const st = MOCK_STOCKTAKES.find(s => s.id === id)
    return mockResponse(st ?? null)
  }
  if (url.match(/\/admin\/stocktakes$/)) {
    if (method === 'get') return mockResponse([...MOCK_STOCKTAKES].sort((a, b) => b.createdAt - a.createdAt))
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
      const id = `st${Date.now()}`
      const items = MOCK_INGREDIENTS.map((ing, i) => ({
        id: `sti-${id}-${i}`, orderId: id,
        ingredientId: ing.id, systemQty: ing.currentStock, actualQty: ing.currentStock,
      }))
      const newSt = { id, status: 'DRAFT' as const, note: body.note ?? '', createdAt: Date.now(), items }
      MOCK_STOCKTAKES.unshift(newSt)
      return mockResponse(newSt)
    }
  }

  if (url.match(/\/admin\/suppliers\/(.+)/)) {
    const id = url.split('/admin/suppliers/')[1]
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    if (method === 'patch') {
      const idx = MOCK_SUPPLIERS.findIndex(s => s.id === id)
      if (idx >= 0) Object.assign(MOCK_SUPPLIERS[idx], body)
    } else if (method === 'delete') {
      const i = MOCK_SUPPLIERS.findIndex(s => s.id === id)
      if (i >= 0) MOCK_SUPPLIERS.splice(i, 1)
    }
    return mockResponse({ ok: true })
  }
  if (url.match(/\/admin\/suppliers$/)) {
    if (method === 'get') return mockResponse([...MOCK_SUPPLIERS])
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
      const newSup = { ...body, id: `sup${Date.now()}`, createdAt: Date.now() }
      MOCK_SUPPLIERS.push(newSup)
      return mockResponse(newSup)
    }
  }

  // Inventory report
  if (url.match(/\/admin\/inventory\/report/)) {
    const params = new URLSearchParams(url.split('?')[1] ?? '')
    const from = Number(params.get('from') ?? 0)
    const to = Number(params.get('to') ?? Date.now())

    // Aggregate movements per ingredient within time window
    const movementsInRange = MOCK_STOCK_MOVEMENTS.filter(m => m.createdAt >= from && m.createdAt <= to)
    const movements = MOCK_INGREDIENTS.map(ing => {
      const ingMov = movementsInRange.filter(m => m.ingredientId === ing.id)
      const totalIn     = ingMov.filter(m => m.type === 'IN').reduce((s, m) => s + m.qty, 0)
      const totalOut    = ingMov.filter(m => m.type === 'OUT').reduce((s, m) => s + m.qty, 0)
      const totalAdjust = ingMov.filter(m => m.type === 'ADJUST').reduce((s, m) => s + m.qty, 0)
      return {
        ingredientId: ing.id,
        name: ing.name,
        unit: ing.unit,
        openingStock: Math.round((ing.currentStock - totalIn + totalOut) * 1000) / 1000,
        totalIn,
        totalOut,
        totalAdjust,
        closingStock: ing.currentStock,
        safetyStock: ing.safetyStock,
      }
    })

    // Aggregate confirmed POs by supplier within time window
    const confirmedPOs = MOCK_PURCHASE_ORDERS.filter(
      p => p.status === 'CONFIRMED' && p.createdAt >= from && p.createdAt <= to
    )
    const supplierMap: Record<string, { orderCount: number; totalMinorUnit: number }> = {}
    for (const po of confirmedPOs) {
      const s = po.supplier || '（未填写）'
      if (!supplierMap[s]) supplierMap[s] = { orderCount: 0, totalMinorUnit: 0 }
      supplierMap[s].orderCount++
      supplierMap[s].totalMinorUnit += po.items.reduce((sum: number, i: { qty: number; unitCost: number }) => sum + i.qty * i.unitCost, 0)
    }
    const purchaseBySupplier = Object.entries(supplierMap).map(([supplier, v]) => ({ supplier, ...v }))
      .sort((a, b) => b.totalMinorUnit - a.totalMinorUnit)

    return mockResponse({
      from, to,
      movements,
      purchaseBySupplier,
      lowStockCount: MOCK_INGREDIENTS.filter(i => i.currentStock < i.safetyStock).length,
    })
  }

  // BOM
  if (url.match(/\/admin\/bom\/(.+)/)) {
    const menuItemId = url.split('/admin/bom/')[1]
    if (method === 'get') {
      // Build a map: ingredientId → lastUnitCost from the most recent confirmed PO
      const confirmedPos = MOCK_PURCHASE_ORDERS.filter(po => po.status === 'CONFIRMED')
        .sort((a, b) => (b.confirmedAt ?? b.createdAt) - (a.confirmedAt ?? a.createdAt))
      const lastUnitCostMap: Record<string, number> = {}
      for (const po of confirmedPos) {
        for (const item of po.items) {
          if (!(item.ingredientId in lastUnitCostMap)) {
            lastUnitCostMap[item.ingredientId] = item.unitCost
          }
        }
      }
      const lines = (MOCK_BOM[menuItemId] ?? []).map(l => ({
        ...l, menuItemId, lastUnitCost: lastUnitCostMap[l.ingredientId],
      }))
      const estimatedCostMinorUnit = lines.reduce((sum, l) => {
        return sum + (l.lastUnitCost != null ? l.qty * l.lastUnitCost : 0)
      }, 0)
      return mockResponse({ menuItemId, lines, estimatedCostMinorUnit: Math.round(estimatedCostMinorUnit) })
    }
    if (method === 'put') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
      const lines = (body.lines ?? []).map((l: { ingredientId: string; qty: number }, i: number) => ({
        id: `bom-${menuItemId}-${i}-${Date.now()}`, ingredientId: l.ingredientId, qty: l.qty,
      }))
      MOCK_BOM[menuItemId] = lines
      return mockResponse({ menuItemId, lines: lines.map((l: { id: string; ingredientId: string; qty: number }) => ({ ...l, menuItemId })) })
    }
  }

  // Sections
  if (url.match(/\/admin\/sections\/(.+)/)) {
    const id = url.split('/admin/sections/')[1]
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    if (method === 'patch') {
      const idx = MOCK_SECTIONS.findIndex(s => s.id === id)
      if (idx >= 0) Object.assign(MOCK_SECTIONS[idx], body)
    } else if (method === 'delete') {
      const i = MOCK_SECTIONS.findIndex(s => s.id === id)
      if (i >= 0) MOCK_SECTIONS.splice(i, 1)
      MOCK_FLOOR_TABLES.filter(t => t.sectionId === id).forEach(t => { MOCK_FLOOR_TABLES.splice(MOCK_FLOOR_TABLES.indexOf(t), 1) })
    }
    return mockResponse({ ok: true })
  }
  if (url.match(/\/admin\/sections$/) || url.match(/\/admin\/sections\?/)) {
    if (method === 'get') return mockResponse([...MOCK_SECTIONS])
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
      MOCK_SECTIONS.push({ ...body, id: `s${Date.now()}` })
    }
    return mockResponse({ ok: true })
  }

  // Tables
  if (url.match(/\/admin\/tables\/(.+)/)) {
    const id = url.split('/admin/tables/')[1]
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    if (method === 'patch') {
      const idx = MOCK_FLOOR_TABLES.findIndex(t => t.id === id)
      if (idx >= 0) Object.assign(MOCK_FLOOR_TABLES[idx], body)
    } else if (method === 'delete') {
      const i = MOCK_FLOOR_TABLES.findIndex(t => t.id === id)
      if (i >= 0) MOCK_FLOOR_TABLES.splice(i, 1)
    }
    return mockResponse({ ok: true })
  }
  if (url.match(/\/admin\/tables$/) || url.match(/\/admin\/tables\?/)) {
    if (method === 'get') {
      const enriched = MOCK_FLOOR_TABLES.map(t => ({
        ...t,
        section: MOCK_SECTIONS.find(s => s.id === t.sectionId)?.name ?? t.sectionId ?? '',
        active: true,
      }))
      return mockResponse(enriched)
    }
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
      MOCK_FLOOR_TABLES.push({ ...body, id: `t${Date.now()}` })
    }
    return mockResponse({ ok: true })
  }

  // Payment methods
  if (url.match(/\/admin\/payment-methods\/([^/]+)$/)) {
    const id = url.match(/\/admin\/payment-methods\/([^/]+)$/)![1]
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    if (method === 'patch') {
      const idx = MOCK_PAYMENT_METHODS.findIndex(p => p.id === id)
      if (idx >= 0) {
        if (MOCK_PAYMENT_METHODS[idx].code === 'CASH' && body.isActive === false) {
          return mockResponse({ error: 'Cash payment method cannot be deactivated' }, 400)
        }
        Object.assign(MOCK_PAYMENT_METHODS[idx], body)
      }
    } else if (method === 'delete') {
      const i = MOCK_PAYMENT_METHODS.findIndex(p => p.id === id)
      if (i >= 0) {
        if (MOCK_PAYMENT_METHODS[i].code === 'CASH') {
          return mockResponse({ error: 'Cash payment method cannot be deleted' }, 400)
        }
        MOCK_PAYMENT_METHODS.splice(i, 1)
      }
    }
    return mockResponse({ ok: true })
  }
  if (url.match(/\/admin\/payment-methods$/)) {
    if (method === 'get') return mockResponse([...MOCK_PAYMENT_METHODS].sort((a, b) => a.sortOrder - b.sortOrder))
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
      const newPm = {
        id: `pm-${Date.now()}`,
        code: body.code ?? '',
        baseType: body.baseType ?? 'OTHER',
        displayName: body.displayName ?? '',
        color: body.color ?? 'gray',
        sortOrder: body.sortOrder ?? MOCK_PAYMENT_METHODS.length * 10 + 10,
        isActive: body.isActive ?? true,
      }
      MOCK_PAYMENT_METHODS.push(newPm)
      return mockResponse({ id: newPm.id })
    }
  }

  // Menu categories
  if (url.match(/\/admin\/menu-categories\/([^/]+)$/)) {
    const id = url.match(/\/admin\/menu-categories\/([^/]+)$/)![1]
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    if (method === 'patch') {
      const idx = MOCK_CATEGORIES.findIndex(c => c.id === id)
      if (idx >= 0) Object.assign(MOCK_CATEGORIES[idx], body)
    } else if (method === 'delete') {
      const i = MOCK_CATEGORIES.findIndex(c => c.id === id)
      if (i >= 0) MOCK_CATEGORIES.splice(i, 1)
    }
    return mockResponse({ ok: true })
  }
  if (url.match(/\/admin\/menu-categories$/)) {
    if (method === 'get') return mockResponse([...MOCK_CATEGORIES].sort((a, b) => a.sortOrder - b.sortOrder))
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
      const newCat = { id: `cat-${Date.now()}`, name: body.name ?? '', sortOrder: body.sortOrder ?? MOCK_CATEGORIES.length + 1 }
      MOCK_CATEGORIES.push(newCat)
      return mockResponse(newCat)
    }
  }

  // Menu
  if (url.match(/\/admin\/menu$/) && method === 'get') return mockResponse(MOCK_MENU)
  if (url.includes('/admin/menu/bulk')) return mockResponse({ ok: true })
  if (url.match(/\/admin\/menu\/.+/) && method === 'patch') {
    const id = url.match(/\/admin\/menu\/(.+)/)![1]
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    const idx = MOCK_MENU.findIndex(m => m.id === id)
    if (idx >= 0) Object.assign(MOCK_MENU[idx], body, { updatedAt: Date.now() })
    return mockResponse({ ok: true })
  }
  if (url.match(/\/admin\/menu\/.+/) && method === 'delete') return mockResponse({ ok: true })
  if (url.includes('/admin/menu') && method === 'post') {
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
    MOCK_MENU.push({ ...body, id: `m${Date.now()}`, isSoldOut: false, updatedAt: Date.now() })
    return mockResponse({ ok: true })
  }

  // Combos
  if (url.includes('/admin/combos') && method === 'get') return mockResponse(MOCK_COMBOS)
  if (url.includes('/admin/combos')) return mockResponse({ ok: true })

  // Customers
  if (url.match(/\/admin\/customers\/([^/]+)\/adjust-points$/) && method === 'post') {
    const id = url.match(/\/admin\/customers\/([^/]+)\/adjust-points$/)![1]
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    const cust = MOCK_CUSTOMERS.find(c => c.id === id)
    if (cust) {
      cust.loyaltyPoints += body.delta ?? 0
      MOCK_LOYALTY_TX.unshift({ id: `ltx${Date.now()}`, customerId: id, orderId: undefined, type: 'ADJUST', points: body.delta ?? 0, description: body.description ?? '', createdAt: Date.now() })
    }
    return mockResponse({ ok: true })
  }
  if (url.match(/\/admin\/customers\/([^/]+)\/loyalty-transactions$/)) {
    const id = url.match(/\/admin\/customers\/([^/]+)\/loyalty-transactions$/)![1]
    return mockResponse(MOCK_LOYALTY_TX.filter(t => t.customerId === id).slice(0, 20))
  }
  if (url.match(/\/admin\/customers\/([^/]+)$/)) {
    const id = url.match(/\/admin\/customers\/([^/]+)$/)![1]
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    if (method === 'get') return mockResponse(MOCK_CUSTOMERS.find(c => c.id === id) ?? null)
    if (method === 'patch') { const i = MOCK_CUSTOMERS.findIndex(c => c.id === id); if (i >= 0) Object.assign(MOCK_CUSTOMERS[i], body) }
    if (method === 'delete') { const i = MOCK_CUSTOMERS.findIndex(c => c.id === id); if (i >= 0) MOCK_CUSTOMERS.splice(i, 1) }
    return mockResponse({ ok: true })
  }
  if (url.match(/\/admin\/customers(\?.*)?$/)) {
    const qs = new URLSearchParams(url.split('?')[1] ?? '')
    const q = qs.get('q')?.toLowerCase() ?? ''
    const tierId = qs.get('tierId') ?? ''
    let list = [...MOCK_CUSTOMERS]
    if (q) list = list.filter(c => c.name.includes(q) || c.phone.includes(q))
    if (tierId) list = list.filter(c => c.membershipTierId === tierId)
    if (method === 'get') return mockResponse(list)
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
      const newCust = { ...body, id: `cust${Date.now()}`, registeredAt: Date.now(), lastVisitAt: Date.now(), totalVisits: 0, totalSpendMinorUnit: 0, loyaltyPoints: 0, membershipTierId: body.membershipTierId ?? 'tier1', tags: body.tags ?? [] }
      MOCK_CUSTOMERS.push(newCust)
      return mockResponse(newCust)
    }
  }

  // Loyalty Tiers
  if (url.match(/\/admin\/loyalty\/tiers\/([^/]+)$/)) {
    const id = url.match(/\/admin\/loyalty\/tiers\/([^/]+)$/)![1]
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    if (method === 'patch') { const i = MOCK_TIERS.findIndex(t => t.id === id); if (i >= 0) Object.assign(MOCK_TIERS[i], body) }
    if (method === 'delete') { const i = MOCK_TIERS.findIndex(t => t.id === id); if (i >= 0) MOCK_TIERS.splice(i, 1) }
    return mockResponse({ ok: true })
  }
  if (url.match(/\/admin\/loyalty\/tiers$/)) {
    if (method === 'get') return mockResponse([...MOCK_TIERS])
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
      const nt = { ...body, id: `tier${Date.now()}` }; MOCK_TIERS.push(nt); return mockResponse(nt)
    }
  }

  // Campaigns
  if (url.match(/\/admin\/campaigns\/([^/]+)\/send$/) && method === 'post') {
    const id = url.match(/\/admin\/campaigns\/([^/]+)\/send$/)![1]
    const camp = MOCK_CAMPAIGNS.find(c => c.id === id)
    if (camp) { camp.status = 'SENT'; camp.sentCount = MOCK_CUSTOMERS.filter(c => camp.targetSegment === 'ALL' || (camp.targetSegment === 'TIER' && c.membershipTierId === camp.targetTierId)).length }
    return mockResponse({ ok: true })
  }
  if (url.match(/\/admin\/campaigns\/([^/]+)$/)) {
    const id = url.match(/\/admin\/campaigns\/([^/]+)$/)![1]
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    if (method === 'patch') { const i = MOCK_CAMPAIGNS.findIndex(c => c.id === id); if (i >= 0) Object.assign(MOCK_CAMPAIGNS[i], body) }
    if (method === 'delete') { const i = MOCK_CAMPAIGNS.findIndex(c => c.id === id); if (i >= 0) MOCK_CAMPAIGNS.splice(i, 1) }
    return mockResponse({ ok: true })
  }
  if (url.match(/\/admin\/campaigns$/)) {
    if (method === 'get') return mockResponse([...MOCK_CAMPAIGNS])
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
      const nc = { ...body, id: `camp${Date.now()}`, sentCount: 0, createdAt: Date.now(), status: body.status ?? 'DRAFT' }
      MOCK_CAMPAIGNS.push(nc); return mockResponse(nc)
    }
  }

  // Shifts availability
  if (url.match(/\/admin\/shifts\/([^/]+)\/availability/)) {
    const shiftId = url.match(/\/admin\/shifts\/([^/]+)\/availability/)![1]
    const qs = new URLSearchParams(url.split('?')[1] ?? '')
    const date = qs.get('date') ?? new Date().toISOString().split('T')[0]
    const shift = MOCK_SHIFTS.find(s => s.id === shiftId)
    if (!shift) return mockResponse([])
    const slots: { time: string; covers: number; capacity: number; available: boolean }[] = []
    const startH = parseInt(shift.startTime.split(':')[0]), startM = parseInt(shift.startTime.split(':')[1])
    const endH = parseInt(shift.endTime.split(':')[0]), endM = parseInt(shift.endTime.split(':')[1])
    const startMin = startH * 60 + startM, endMin = endH * 60 + endM
    const interval = shift.slotIntervalMinutes
    const numSlots = Math.floor((endMin - startMin) / interval)
    const slotCap = Math.round(shift.maxCovers / numSlots)
    for (let m = startMin; m < endMin; m += interval) {
      const h = Math.floor(m / 60), min = m % 60
      const timeStr = `${String(h).padStart(2,'0')}:${String(min).padStart(2,'0')}`
      const covers = MOCK_RESERVATIONS
        .filter(r => r.date === date && r.shiftId === shiftId && r.time === timeStr && ['PENDING','CONFIRMED','SEATED'].includes(r.status))
        .reduce((s, r) => s + r.partySize, 0)
      slots.push({ time: timeStr, covers, capacity: slotCap, available: covers < slotCap })
    }
    return mockResponse(slots)
  }

  // Shifts
  if (url.match(/\/admin\/shifts\/([^/]+)$/)) {
    const id = url.match(/\/admin\/shifts\/([^/]+)$/)![1]
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    if (method === 'patch') { const i = MOCK_SHIFTS.findIndex(s => s.id === id); if (i >= 0) Object.assign(MOCK_SHIFTS[i], body) }
    if (method === 'delete') { const i = MOCK_SHIFTS.findIndex(s => s.id === id); if (i >= 0) MOCK_SHIFTS.splice(i, 1) }
    return mockResponse({ ok: true })
  }
  if (url.match(/\/admin\/shifts(\?.*)?$/)) {
    if (method === 'get') return mockResponse([...MOCK_SHIFTS])
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
      const ns = { ...body, id: `sh${Date.now()}` }
      MOCK_SHIFTS.push(ns); return mockResponse(ns)
    }
  }

  // Special days
  if (url.match(/\/admin\/special-days\/([^/]+)$/)) {
    const id = url.match(/\/admin\/special-days\/([^/]+)$/)![1]
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    if (method === 'patch') { const i = MOCK_SPECIAL_DAYS.findIndex(s => s.id === id); if (i >= 0) Object.assign(MOCK_SPECIAL_DAYS[i], body) }
    if (method === 'delete') { const i = MOCK_SPECIAL_DAYS.findIndex(s => s.id === id); if (i >= 0) MOCK_SPECIAL_DAYS.splice(i, 1) }
    return mockResponse({ ok: true })
  }
  if (url.match(/\/admin\/special-days(\?.*)?$/)) {
    if (method === 'get') return mockResponse([...MOCK_SPECIAL_DAYS])
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
      const ns = { ...body, id: `sd${Date.now()}` }
      MOCK_SPECIAL_DAYS.push(ns); return mockResponse(ns)
    }
  }

  // Waitlist actions
  if (url.match(/\/admin\/waitlist\/([^/]+)\/(notify|seat|cancel)$/)) {
    const [, id, action] = url.match(/\/admin\/waitlist\/([^/]+)\/(notify|seat|cancel)$/)!
    const entry = MOCK_WAITLIST.find(w => w.id === id)
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    if (entry) {
      if (action === 'notify') { entry.status = 'NOTIFIED'; entry.notifiedAt = Date.now(); entry.expiresAt = Date.now() + 10*60*1000 }
      if (action === 'seat') { entry.status = 'SEATED'; entry.seatedAt = Date.now(); if (body.tableId) entry.tableId = String(body.tableId) }
      if (action === 'cancel') { entry.status = 'CANCELLED' }
    }
    return mockResponse({ ok: true })
  }
  if (url.match(/\/admin\/waitlist\/([^/]+)$/)) {
    const id = url.match(/\/admin\/waitlist\/([^/]+)$/)![1]
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    if (method === 'patch') { const i = MOCK_WAITLIST.findIndex(w => w.id === id); if (i >= 0) Object.assign(MOCK_WAITLIST[i], body) }
    if (method === 'delete') { const i = MOCK_WAITLIST.findIndex(w => w.id === id); if (i >= 0) MOCK_WAITLIST.splice(i, 1) }
    return mockResponse({ ok: true })
  }
  if (url.match(/\/admin\/waitlist(\?.*)?$/)) {
    const qs = new URLSearchParams(url.split('?')[1] ?? '')
    const status = qs.get('status') ?? ''
    let list = [...MOCK_WAITLIST]
    if (status) list = list.filter(w => w.status === status)
    if (method === 'get') return mockResponse(list)
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
      const nw = { ...body, id: `wl${Date.now()}` }
      MOCK_WAITLIST.push(nw); return mockResponse(nw)
    }
  }

  // Reservations
  if (url.match(/\/admin\/reservations\/([^/]+)$/)) {
    const id = url.match(/\/admin\/reservations\/([^/]+)$/)![1]
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    if (method === 'patch') { const i = MOCK_RESERVATIONS.findIndex(r => r.id === id); if (i >= 0) Object.assign(MOCK_RESERVATIONS[i], body) }
    if (method === 'delete') { const i = MOCK_RESERVATIONS.findIndex(r => r.id === id); if (i >= 0) MOCK_RESERVATIONS.splice(i, 1) }
    return mockResponse({ ok: true })
  }
  if (url.match(/\/admin\/reservations(\?.*)?$/)) {
    const qs = new URLSearchParams(url.split('?')[1] ?? '')
    const date = qs.get('date') ?? ''
    const status = qs.get('status') ?? ''
    const shiftId = qs.get('shiftId') ?? ''
    let list = [...MOCK_RESERVATIONS]
    if (date) list = list.filter(r => r.date === date)
    if (status) list = list.filter(r => r.status === status)
    if (shiftId) list = list.filter(r => r.shiftId === shiftId)
    if (method === 'get') return mockResponse(list)
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
      const nr = { ...body, id: `rsv${Date.now()}`, createdAt: Date.now(), status: body.status ?? 'PENDING' }
      MOCK_RESERVATIONS.push(nr); return mockResponse(nr)
    }
  }

  // Coupons
  if (url.match(/\/admin\/coupons\/([^/]+)$/)) {
    const id = url.match(/\/admin\/coupons\/([^/]+)$/)![1]
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    if (method === 'patch') {
      const idx = MOCK_COUPONS.findIndex(c => c.id === id)
      if (idx >= 0) Object.assign(MOCK_COUPONS[idx], body)
    } else if (method === 'delete') {
      const i = MOCK_COUPONS.findIndex(c => c.id === id)
      if (i >= 0) MOCK_COUPONS.splice(i, 1)
    }
    return mockResponse({ ok: true })
  }
  if (url.match(/\/admin\/coupons$/)) {
    if (method === 'get') return mockResponse([...MOCK_COUPONS])
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
      const newCoupon = { id: `cp${Date.now()}`, ...body, isActive: true }
      MOCK_COUPONS.push(newCoupon)
      return mockResponse(newCoupon)
    }
  }

  // Modifier Groups
  if (url.match(/\/admin\/modifier-groups\/([^/]+)$/)) {
    const id = url.match(/\/admin\/modifier-groups\/([^/]+)$/)![1]
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    if (method === 'patch') {
      const idx = MOCK_MODIFIER_GROUPS.findIndex(g => g.id === id)
      if (idx >= 0) Object.assign(MOCK_MODIFIER_GROUPS[idx], body)
    } else if (method === 'delete') {
      const i = MOCK_MODIFIER_GROUPS.findIndex(g => g.id === id)
      if (i >= 0) MOCK_MODIFIER_GROUPS.splice(i, 1)
      // Remove from any menu items that embed this group by id
      for (const item of MOCK_MENU) {
        if (item.modifierGroups) {
          item.modifierGroups = item.modifierGroups.filter(g => g.id !== id)
        }
      }
    }
    return mockResponse({ ok: true })
  }
  if (url.match(/\/admin\/modifier-groups$/)) {
    if (method === 'get') return mockResponse([...MOCK_MODIFIER_GROUPS])
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
      const newGroup = { ...body, id: `mg${Date.now()}` }
      MOCK_MODIFIER_GROUPS.push(newGroup)
      return mockResponse(newGroup)
    }
  }

  // Menu Profiles
  if (url.match(/\/admin\/menu-profiles\/([^/]+)$/)) {
    const id = url.match(/\/admin\/menu-profiles\/([^/]+)$/)![1]
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    if (method === 'patch') {
      const idx = MOCK_MENU_PROFILES.findIndex(p => p.id === id)
      if (idx >= 0) Object.assign(MOCK_MENU_PROFILES[idx], body)
    }
    if (method === 'delete') {
      const i = MOCK_MENU_PROFILES.findIndex(p => p.id === id)
      if (i >= 0) MOCK_MENU_PROFILES.splice(i, 1)
      for (const item of MOCK_MENU) {
        if (item.menuProfileIds) {
          (item as any).menuProfileIds = (item as any).menuProfileIds.filter((pid: string) => pid !== id)
        }
      }
    }
    return mockResponse({ ok: true })
  }
  if (url.match(/\/admin\/menu-profiles$/)) {
    if (method === 'get') return mockResponse([...MOCK_MENU_PROFILES])
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
      const newProfile = { ...body, id: `mp${Date.now()}` }
      MOCK_MENU_PROFILES.push(newProfile)
      return mockResponse(newProfile)
    }
  }

  // Reports
  if (url.includes('/admin/reports/staff')) {
    return mockResponse([
      { operatorId: 'u1', operatorName: '张三', orderCount: 38, revenueMinorUnit: 152000, avgOrderValueMinorUnit: 4000, tipMinorUnit: 3800, discountMinorUnit: 1200, refundMinorUnit: 0 },
      { operatorId: 'u2', operatorName: '李四', orderCount: 27, revenueMinorUnit: 108000, avgOrderValueMinorUnit: 4000, tipMinorUnit: 2700, discountMinorUnit: 800,  refundMinorUnit: 2000 },
    ])
  }

  if (url.includes('/admin/reports/hourly')) {
    const hours = Array.from({ length: 24 }, (_, h) => {
      const peak = (h >= 11 && h <= 13) || (h >= 17 && h <= 20)
      const orders = peak ? Math.round(5 + Math.random() * 8) : Math.round(Math.random() * 2)
      return { hour: h, orderCount: orders, revenueMinorUnit: orders * 4500 }
    })
    return mockResponse(hours)
  }

  if (url.includes('/admin/reports/order-type')) {
    return mockResponse([
      { orderType: 'DINE_IN',  orderCount: 68, revenueMinorUnit: 272000, sharePermille: 640 },
      { orderType: 'TAKEAWAY', orderCount: 22, revenueMinorUnit:  88000, sharePermille: 207 },
      { orderType: 'DELIVERY', orderCount:  8, revenueMinorUnit:  32000, sharePermille: 153 },
    ])
  }

  if (url.includes('/admin/reports/category')) {
    return mockResponse([
      { categoryId: 'main',      categoryName: '主食 / 主菜', orderCount: 52, quantitySold: 87,  revenueMinorUnit: 261000 },
      { categoryId: 'drink',     categoryName: '饮品',        orderCount: 44, quantitySold: 91,  revenueMinorUnit:  91000 },
      { categoryId: 'appetizer', categoryName: '前菜 / 小食', orderCount: 31, quantitySold: 48,  revenueMinorUnit:  72000 },
    ])
  }

  if (url.includes('/admin/reports/items')) {
    return mockResponse([
      { menuItemId: 'i1', menuItemName: '招牌牛肉面', categoryId: 'main',      categoryName: '主食',  quantitySold: 34, ordersCount: 34, revenueMinorUnit: 102000 },
      { menuItemId: 'i2', menuItemName: '珍珠奶茶',   categoryId: 'drink',     categoryName: '饮品',  quantitySold: 51, ordersCount: 44, revenueMinorUnit:  76500 },
      { menuItemId: 'i3', menuItemName: '炸鸡翅',     categoryId: 'appetizer', categoryName: '前菜',  quantitySold: 28, ordersCount: 26, revenueMinorUnit:  56000 },
    ])
  }

  if (url.includes('/admin/timecards/report')) {
    return mockResponse({
      summaries: [
        { operatorId: 'u1', operatorName: '张三', totalMinutes: 480, shiftsCount: 1 },
        { operatorId: 'u2', operatorName: '李四', totalMinutes: 360, shiftsCount: 1 },
      ],
      totalMinutes: 840,
    })
  }

  if (url.includes('/admin/timecards')) {
    return mockResponse({ timecards: [], total: 0 })
  }

  if (url.includes('/admin/reports/peak')) {
    // Generate 7×24 mock peak hours data
    const cells: import('./reports').PeakHourCell[] = []
    for (let dow = 1; dow <= 7; dow++) {
      for (let hour = 0; hour < 24; hour++) {
        // Realistic restaurant pattern: lunch 11-13, dinner 17-20
        const lunchBoost = (hour >= 11 && hour <= 13) ? 3 : 1
        const dinnerBoost = (hour >= 17 && hour <= 20) ? 4 : 1
        const nightOff = (hour >= 0 && hour <= 5) ? 0 : 1
        const weekendBoost = (dow >= 6) ? 1.3 : 1.0
        const base = Math.round((2 + Math.random() * 3) * lunchBoost * dinnerBoost * nightOff * weekendBoost)
        cells.push({ dayOfWeek: dow, hour, orderCount: base })
      }
    }
    const maxOrderCount = Math.max(...cells.map(c => c.orderCount))
    const totalOrders = cells.reduce((s, c) => s + c.orderCount, 0)
    return mockResponse({ cells, maxOrderCount, totalOrders })
  }
  if (url.includes('/admin/reports/trend')) {
    const qs = new URLSearchParams(url.split('?')[1] ?? '')
    const fromDate = qs.get('fromDate') ?? ''
    const days = fromDate ? Math.max(1, Math.round((Date.now() - new Date(fromDate).getTime()) / 86_400_000)) : 7
    return mockResponse(buildTrendReport(days))
  }
  if (url.includes('/admin/reports/shift')) return mockResponse(MOCK_SHIFT)

  if (url.includes('/admin/reports/payment-methods')) {
    return mockResponse([
      { method: 'CASH',   paymentCount: 42, paymentAmountMinorUnit: 168000, refundCount: 1, refundAmountMinorUnit: 2000,  netAmountMinorUnit: 166000 },
      { method: 'CARD',   paymentCount: 87, paymentAmountMinorUnit: 348000, refundCount: 2, refundAmountMinorUnit: 6000,  netAmountMinorUnit: 342000 },
      { method: 'WECHAT', paymentCount: 31, paymentAmountMinorUnit: 124000, refundCount: 0, refundAmountMinorUnit: 0,     netAmountMinorUnit: 124000 },
      { method: 'ALIPAY', paymentCount: 18, paymentAmountMinorUnit:  72000, refundCount: 0, refundAmountMinorUnit: 0,     netAmountMinorUnit:  72000 },
    ])
  }

  if (url.includes('/admin/reports/tax')) {
    return mockResponse({
      taxableSalesMinorUnit: 590000,
      nonTaxableSalesMinorUnit: 122000,
      totalNetSalesMinorUnit: 712000,
      lines: [
        { taxRateId: 'gst',  taxRateName: 'GST',  ratePermille: 100, taxableSalesMinorUnit: 590000, taxAmountMinorUnit: 59000 },
      ],
    })
  }

  if (url.includes('/admin/reports/modifiers')) {
    return mockResponse([
      { optionId: 'm1', optionName: '去冰',   groupId: 'g1', groupName: '冰量',   quantitySold: 54, revenueMinorUnit: 0 },
      { optionId: 'm2', optionName: '少冰',   groupId: 'g1', groupName: '冰量',   quantitySold: 38, revenueMinorUnit: 0 },
      { optionId: 'm3', optionName: '加芝士', groupId: 'g2', groupName: '附加料', quantitySold: 27, revenueMinorUnit: 8100 },
      { optionId: 'm4', optionName: '加培根', groupId: 'g2', groupName: '附加料', quantitySold: 19, revenueMinorUnit: 9500 },
    ])
  }

  // QR ordering
  if (url.includes('/admin/qr-ordering/config')) {
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    if (method === 'put') {
      MOCK_QR_ORDERING_CONFIG = { ...MOCK_QR_ORDERING_CONFIG, ...body }
    }
    return mockResponse(MOCK_QR_ORDERING_CONFIG)
  }
  if (url.match(/\/admin\/qr-ordering\/table-bindings$/)) {
    return mockResponse(mockTableBindings())
  }
  if (url.match(/\/admin\/qr-ordering\/tables\/([^/]+)\/qr\/reset$/)) {
    const tableId = decodeURIComponent(url.match(/\/admin\/qr-ordering\/tables\/([^/]+)\/qr\/reset$/)![1])
    const binding = mockCreateTableQr(tableId, true)
    return binding ? mockResponse(binding) : mockResponse({ message: 'Table not found' }, 404)
  }
  if (url.match(/\/admin\/qr-ordering\/tables\/([^/]+)\/qr$/)) {
    const tableId = decodeURIComponent(url.match(/\/admin\/qr-ordering\/tables\/([^/]+)\/qr$/)![1])
    const binding = mockCreateTableQr(tableId, false)
    return binding ? mockResponse(binding) : mockResponse({ message: 'Table not found' }, 404)
  }
  if (url.match(/\/admin\/qr-ordering\/codes\/([^/]+)\/rebind$/)) {
    const code = decodeURIComponent(url.match(/\/admin\/qr-ordering\/codes\/([^/]+)\/rebind$/)![1])
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    const tableId = body.tableId
    if (!MOCK_FLOOR_TABLES.some(t => t.id === tableId)) return mockResponse({ message: 'Table not found' }, 404)
    const idx = MOCK_QR_CODES.findIndex(qr => qr.code === code)
    if (idx < 0) return mockResponse({ message: 'QR code not found' }, 404)
    const nowMs = Date.now()
    MOCK_QR_CODES = MOCK_QR_CODES.map(qr => (
      qr.scope === 'TABLE' && qr.tableId === tableId && qr.code !== code
        ? { ...qr, enabled: false, updatedAt: nowMs }
        : qr
    ))
    MOCK_QR_CODES[idx] = { ...MOCK_QR_CODES[idx], scope: 'TABLE', tableId, enabled: true, expiresAt: null, updatedAt: nowMs }
    return mockResponse(mockTableBinding(tableId))
  }
  if (url.match(/\/admin\/qr-ordering\/codes\/([^/]+)$/)) {
    const code = decodeURIComponent(url.match(/\/admin\/qr-ordering\/codes\/([^/]+)$/)![1])
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    if (method === 'patch') {
      const idx = MOCK_QR_CODES.findIndex(qr => qr.code === code)
      if (idx >= 0) {
        MOCK_QR_CODES[idx] = { ...MOCK_QR_CODES[idx], ...body, updatedAt: Date.now() }
        return mockResponse(MOCK_QR_CODES[idx])
      }
    }
    if (method === 'delete') {
      MOCK_QR_CODES = MOCK_QR_CODES.filter(qr => qr.code !== code)
      return mockResponse({ deleted: true })
    }
    return mockResponse({ message: 'QR code not found' }, 404)
  }
  if (url.match(/\/admin\/qr-ordering\/codes$/)) {
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    if (method === 'post') {
      const nowMs = Date.now()
      const next = {
        code: body.code,
        scope: body.scope,
        tableId: body.tableId ?? null,
        enabled: body.enabled ?? true,
        expiresAt: body.expiresAt ?? null,
        createdAt: nowMs,
        updatedAt: nowMs,
      }
      MOCK_QR_CODES = [next, ...MOCK_QR_CODES.filter(qr => qr.code !== next.code)]
      return mockResponse(next, 201)
    }
    return mockResponse([...MOCK_QR_CODES])
  }

  // Orders list
  if (url.match(/\/admin\/orders\?/) || url === '/admin/orders') {
    const sp = new URLSearchParams(url.split('?')[1] ?? '')
    const page = Number(sp.get('page') ?? 0)
    const pageSize = 10
    const operatorId = sp.get('operatorId')
    const paymentMethod = sp.get('paymentMethod')
    let filtered = MOCK_ORDERS
    if (operatorId) filtered = filtered.filter(o => o.operatorId === operatorId)
    if (paymentMethod) filtered = filtered.filter(o => o.paymentMethod === paymentMethod)
    const slice = filtered.slice(page * pageSize, (page + 1) * pageSize)
    return mockResponse({ orders: slice, total: filtered.length, page, pageSize })
  }

  // Order settle — auto-deduct stock via BOM
  if (url.match(/\/admin\/orders\/.+\/settle/) && method === 'post') {
    const orderId = url.split('/admin/orders/')[1].split('/settle')[0]
    const order = MOCK_ORDERS.find(o => o.id === orderId)
    if (order && order.status !== 'SETTLED') {
      // Use hardcoded items for demo (same as order detail mock)
      const orderItems = [
        { menuItemId: 'm1', quantity: 1 },
        { menuItemId: 'm4', quantity: 2 },
      ]
      const ts = Date.now()
      let bomMovements = 0
      for (const item of orderItems) {
        const bomLines = MOCK_BOM[item.menuItemId] ?? []
        for (const line of bomLines) {
          const ing = MOCK_INGREDIENTS.find(i => i.id === line.ingredientId)
          if (!ing) continue
          const deductQty = Math.round(line.qty * item.quantity * 1000) / 1000
          ing.currentStock = Math.round((ing.currentStock - deductQty) * 1000) / 1000
          MOCK_STOCK_MOVEMENTS.unshift({
            id: `mv-auto-${ts}-${bomMovements++}`,
            ingredientId: line.ingredientId,
            type: 'OUT',
            qty: deductQty,
            note: `订单 ${orderId.slice(0, 8)} 自动扣减`,
            createdAt: ts,
          })
        }
      }
      order.status = 'SETTLED'
    }
    return mockResponse({ ok: true })
  }

  // Refund a payment
  if (url.match(/\/admin\/orders\/payments\/.+\/refund/) && method === 'post') {
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    const paymentId = url.split('/admin/orders/payments/')[1].split('/refund')[0]
    let foundOrderId: string | undefined
    let result: { error: string } | null = null
    for (const [orderId, payments] of MOCK_ORDER_PAYMENTS.entries()) {
      const original = payments.find(p => p.id === paymentId)
      if (!original) continue
      foundOrderId = orderId
      if (original.status !== 'PAID') { result = { error: 'not_paid' }; break }
      const alreadyRefunded = payments
        .filter(p => p.refundedPaymentId === paymentId)
        .reduce((s, p) => s + p.amountMinorUnit, 0)
      const remaining = original.amountMinorUnit - alreadyRefunded
      const amount = Number((body as { amountMinorUnit?: number } | undefined)?.amountMinorUnit ?? 0)
      if (amount <= 0 || amount > remaining) { result = { error: 'invalid_amount' }; break }
      payments.push({
        id: `refund-${Date.now()}`,
        orderId,
        amountMinorUnit: amount,
        method: original.method,
        status: 'REFUNDED',
        operatorId: 'u1',
        refundedPaymentId: paymentId,
        createdAt: Date.now(),
      })
      if (amount === remaining) original.status = 'REFUNDED'
      break
    }
    if (!foundOrderId || result) return mockResponse({ ok: false, error: result?.error ?? 'not_found' }, 400)
    return mockResponse(buildOrderDetail(foundOrderId))
  }

  // Order detail
  if (url.match(/\/admin\/orders\/.+/)) {
    const id = url.split('/').pop()!
    return mockResponse(buildOrderDetail(id))
  }

  // Users
  if (url.includes('/admin/users') && method === 'get') return mockResponse(MOCK_USERS)
  if (url.includes('/admin/users')) return mockResponse({ ok: true })

  // Gift cards
  if (url.match(/\/admin\/gift-cards\/redeem$/)) {
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    const card = MOCK_GIFT_CARDS.find(c => c.code === String(body.code ?? '').toUpperCase())
    if (!card) return mockResponse({ error: '卡号不存在' }, 404)
    if (!card.isActive) return mockResponse({ error: '该卡已停用' }, 400)
    if (card.balanceMinorUnit < body.amountMinorUnit) return mockResponse({ error: '余额不足' }, 400)
    card.balanceMinorUnit -= body.amountMinorUnit
    card.updatedAt = Date.now()
    return mockResponse({ ok: true, remainingBalanceMinorUnit: card.balanceMinorUnit })
  }
  if (url.match(/\/admin\/gift-cards\/([^/]+)\/transactions$/)) {
    const id = url.match(/\/admin\/gift-cards\/([^/]+)\/transactions$/)![1]
    return mockResponse(MOCK_GIFT_CARD_TRANSACTIONS[id] ?? [])
  }
  if (url.match(/\/admin\/gift-cards\/([^/]+)\/topup$/)) {
    const id = url.match(/\/admin\/gift-cards\/([^/]+)\/topup$/)![1]
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    const card = MOCK_GIFT_CARDS.find(c => c.id === id)
    if (!card) return mockResponse({ error: 'Gift card not found' }, 404)
    const newBalance = card.balanceMinorUnit + body.amountMinorUnit
    if (newBalance < 0) return mockResponse({ error: '余额不足' }, 400)
    card.balanceMinorUnit = newBalance
    card.updatedAt = Date.now()
    const txns = MOCK_GIFT_CARD_TRANSACTIONS[id] ?? (MOCK_GIFT_CARD_TRANSACTIONS[id] = [])
    txns.unshift({
      id: `gct${Date.now()}`, giftCardId: id,
      type: body.amountMinorUnit >= 0 ? 'TOPUP' : 'ADJUST',
      amountMinorUnit: body.amountMinorUnit, orderId: null, operatorId: '', note: body.note ?? '', createdAt: Date.now(),
    })
    return mockResponse({ ok: true })
  }
  if (url.match(/\/admin\/gift-cards\/([^/]+)$/)) {
    const id = url.match(/\/admin\/gift-cards\/([^/]+)$/)![1]
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    if (method === 'patch') {
      const card = MOCK_GIFT_CARDS.find(c => c.id === id)
      if (card && typeof body.isActive === 'boolean') card.isActive = body.isActive
      return mockResponse({ updated: true })
    }
    if (method === 'get') {
      const card = MOCK_GIFT_CARDS.find(c => c.id === id)
      if (!card) return mockResponse({ error: 'Gift card not found' }, 404)
      return mockResponse(card)
    }
  }
  if (url.match(/\/admin\/gift-cards$/)) {
    if (method === 'get') {
      const code = new URL(url, 'http://x').searchParams.get('code')
      const list = code ? MOCK_GIFT_CARDS.filter(c => c.code.includes(code.toUpperCase())) : MOCK_GIFT_CARDS
      return mockResponse([...list])
    }
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
      const code = String(body.code ?? '').trim().toUpperCase()
      if (MOCK_GIFT_CARDS.some(c => c.code === code)) return mockResponse({ error: `卡号「${code}」已存在` }, 400)
      const id = `gc${Date.now()}`
      const newCard = { id, code, balanceMinorUnit: body.initialBalanceMinorUnit ?? 0, customerId: body.customerId ?? null, isActive: true, createdAt: Date.now(), updatedAt: Date.now() }
      MOCK_GIFT_CARDS.unshift(newCard)
      if (newCard.balanceMinorUnit) {
        MOCK_GIFT_CARD_TRANSACTIONS[id] = [{ id: `gct${Date.now()}`, giftCardId: id, type: 'ISSUE', amountMinorUnit: newCard.balanceMinorUnit, orderId: null, operatorId: '', note: '开卡', createdAt: Date.now() }]
      }
      return mockResponse({ id, code }, 201)
    }
  }

  // Schedules (排班 + 工时成本)
  if (url.match(/\/admin\/schedules\/labor-cost/)) {
    const summaries = MOCK_USERS.filter(u => u.isActive).map(u => {
      const wage = (u as any).hourlyWageMinorUnit ?? 3000
      const scheduledMinutes = MOCK_SCHEDULES.filter(s => s.operatorId === u.id)
        .reduce((sum, s) => sum + minutesBetween(s.startTime, s.endTime), 0)
      const actualMinutes = Math.round(scheduledMinutes * 0.95)
      return {
        operatorId: u.id,
        operatorName: u.displayName,
        hourlyWageMinorUnit: wage,
        scheduledMinutes,
        actualMinutes,
        scheduledCostMinorUnit: Math.round(scheduledMinutes / 60 * wage),
        actualCostMinorUnit: Math.round(actualMinutes / 60 * wage),
      }
    })
    return mockResponse({
      summaries,
      totalScheduledCostMinorUnit: summaries.reduce((s, x) => s + x.scheduledCostMinorUnit, 0),
      totalActualCostMinorUnit: summaries.reduce((s, x) => s + x.actualCostMinorUnit, 0),
    })
  }
  if (url.match(/\/admin\/schedules\/([^/]+)$/)) {
    const id = url.match(/\/admin\/schedules\/([^/]+)$/)![1]
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
    if (method === 'patch') {
      const s = MOCK_SCHEDULES.find(x => x.id === id)
      if (s) Object.assign(s, body)
      return mockResponse({ ok: true })
    }
    if (method === 'delete') {
      const i = MOCK_SCHEDULES.findIndex(x => x.id === id)
      if (i >= 0) MOCK_SCHEDULES.splice(i, 1)
      return mockResponse({ ok: true })
    }
  }
  if (url.match(/\/admin\/schedules$/)) {
    if (method === 'get') {
      const u = new URL(url, 'http://x')
      const from = u.searchParams.get('from')
      const to = u.searchParams.get('to')
      const operatorId = u.searchParams.get('operatorId')
      let list = [...MOCK_SCHEDULES]
      if (from) list = list.filter(s => s.date >= from)
      if (to) list = list.filter(s => s.date <= to)
      if (operatorId) list = list.filter(s => s.operatorId === operatorId)
      return mockResponse(list)
    }
    if (method === 'post') {
      const body = typeof config.data === 'string' ? JSON.parse(config.data) : (config.data ?? {})
      const operator = MOCK_USERS.find(u => u.id === body.operatorId)
      const id = `sch${Date.now()}`
      MOCK_SCHEDULES.push({
        id, operatorId: body.operatorId, operatorName: operator?.displayName ?? '',
        date: body.date, startTime: body.startTime, endTime: body.endTime, notes: body.notes ?? '',
      })
      return mockResponse({ id }, 201)
    }
  }

  // Dashboard
  if (url.includes('/admin/dashboard')) return mockResponse(MOCK_SHIFT)

  return mockResponse({ ok: true })
}

// ── Install mock adapter ──────────────────────────────────────────────────────

export function installMockAdapter() {
  apiClient.defaults.adapter = async (config: AxiosRequestConfig) => {
    await new Promise(r => setTimeout(r, 120)) // simulate ~120ms latency
    return handleRequest(config)
  }
}
