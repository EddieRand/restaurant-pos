import { useCallback, useEffect, useState } from 'react'
import { settingsApi, normalizeRegionConfig, type RegionConfig } from '../api/admin'

export function useRegionConfig() {
  const [config, setConfig] = useState<RegionConfig | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    setLoading(true)
    setError(null)
    settingsApi.getRegionConfig()
      .then(cfg => {
        localStorage.setItem('pos_region_config', JSON.stringify(cfg))
        localStorage.setItem('pos_menu_primary_language', cfg.menuPrimaryLanguage)
        setConfig(cfg)
      })
      .catch(err => setError(err instanceof Error ? err.message : 'Failed to load region config'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => { load() }, [load])

  const updateConfig = useCallback((updater: RegionConfig | ((prev: RegionConfig) => RegionConfig)) => {
    setConfig(prev => {
      if (!prev) return prev
      const next = typeof updater === 'function' ? updater(prev) : updater
      return normalizeRegionConfig(next)
    })
  }, [])

  const save = useCallback(async () => {
    if (!config) return
    setSaving(true)
    setSaved(false)
    setError(null)
    try {
      const normalized = normalizeRegionConfig(config)
      await settingsApi.setRegionConfig(normalized)
      localStorage.setItem('pos_region_config', JSON.stringify(normalized))
      localStorage.setItem('pos_menu_primary_language', normalized.menuPrimaryLanguage)
      setConfig(normalized)
      setSaved(true)
      window.setTimeout(() => setSaved(false), 2500)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save region config')
    } finally {
      setSaving(false)
    }
  }, [config])

  return {
    config,
    loading,
    saving,
    saved,
    error,
    load,
    save,
    setConfig: updateConfig,
    setError,
  }
}
