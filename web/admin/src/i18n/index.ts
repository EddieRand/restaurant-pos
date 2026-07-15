import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import zhCN from './locales/zh-CN'
import zhTW from './locales/zh-TW'
import enUS from './locales/en-US'
import jaJP from './locales/ja-JP'
import koKR from './locales/ko-KR'
import thTH from './locales/th-TH'
import viVN from './locales/vi-VN'
import msMY from './locales/ms-MY'
import arSA from './locales/ar-SA'

// Adding a new language:
// 1. Create src/i18n/locales/<locale>.ts satisfying `Translations` type — TypeScript will error on missing keys
// 2. Add it to `resources` below
// 3. Add it to the LOCALES array in SettingsPage.tsx

const RTL_LOCALES = new Set(['ar-SA'])

function applyDir(locale: string) {
  document.documentElement.dir = RTL_LOCALES.has(locale) ? 'rtl' : 'ltr'
  document.documentElement.lang = locale
}

const SUPPORTED = ['zh-CN', 'zh-TW', 'en-US', 'en-GB', 'ja-JP', 'ko-KR', 'th-TH', 'vi-VN', 'ms-MY', 'ar-SA']

// Browser may return 'zh-Hans-CN', 'zh', 'en', 'ar', etc. — map to nearest supported locale.
function detectBrowserLocale(): string {
  for (const lang of navigator.languages ?? [navigator.language]) {
    // Exact match first
    if (SUPPORTED.includes(lang)) return lang
    // Prefix match: 'zh-Hans' → 'zh-CN', 'ar-EG' → 'ar-SA', 'en-AU' → 'en-US', etc.
    const prefix = lang.split('-')[0].toLowerCase()
    const match = SUPPORTED.find(s => s.toLowerCase().startsWith(prefix))
    if (match) return match
  }
  return 'en-US'
}

const savedLocale = localStorage.getItem('pos_locale') ?? detectBrowserLocale()

i18n
  .use(initReactI18next)
  .init({
    resources: {
      'zh-CN': { translation: zhCN },
      'zh-TW': { translation: zhTW },
      'en-US': { translation: enUS },
      'en-GB': { translation: enUS },
      'ja-JP': { translation: jaJP },
      'ko-KR': { translation: koKR },
      'th-TH': { translation: thTH },
      'vi-VN': { translation: viVN },
      'ms-MY': { translation: msMY },
      'ar-SA': { translation: arSA },
    },
    lng: savedLocale,
    fallbackLng: 'en-US',
    interpolation: { escapeValue: false },
  })

applyDir(savedLocale)
i18n.on('languageChanged', applyDir)

export default i18n
