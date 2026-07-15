import { useEffect } from 'react'

/** Runs immediately while the page is visible, then repeats at the given interval. */
export function useVisiblePolling(callback: () => void, intervalMs = 5_000) {
  useEffect(() => {
    let timer: number | undefined

    const stop = () => {
      if (timer !== undefined) window.clearInterval(timer)
      timer = undefined
    }

    const start = () => {
      stop()
      if (document.visibilityState !== 'visible') return
      callback()
      timer = window.setInterval(callback, intervalMs)
    }

    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') start()
      else stop()
    }

    start()
    document.addEventListener('visibilitychange', onVisibilityChange)
    return () => {
      stop()
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  }, [callback, intervalMs])
}
