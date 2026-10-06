import { useEffect, useState } from 'react'
import { api, type AiStatus } from '../api'

export const AI_STATUS_REFRESH_EVENT = 'rebeautifier:ai-status-refresh'

export function notifyAiStatusRefresh() {
  window.dispatchEvent(new Event(AI_STATUS_REFRESH_EVENT))
}

type Props = {
  compact?: boolean
  /** Optional externally fetched status (e.g. Settings page) — still refreshes on its own. */
  statusOverride?: AiStatus | null
}

export function AiStatusChip({ compact = false, statusOverride = null }: Props) {
  const [status, setStatus] = useState<AiStatus | null>(statusOverride)

  async function refresh() {
    try {
      const data = await api<AiStatus>('/api/v1/system/ai-status')
      setStatus(data)
    } catch {
      setStatus(null)
    }
  }

  useEffect(() => {
    if (statusOverride) {
      setStatus(statusOverride)
    }
  }, [statusOverride])

  useEffect(() => {
    void refresh()
    const id = window.setInterval(() => void refresh(), 15_000)
    const onRefresh = () => void refresh()
    window.addEventListener(AI_STATUS_REFRESH_EVENT, onRefresh)
    window.addEventListener('focus', onRefresh)
    return () => {
      window.clearInterval(id)
      window.removeEventListener(AI_STATUS_REFRESH_EVENT, onRefresh)
      window.removeEventListener('focus', onRefresh)
    }
  }, [])

  const online = !!status?.available
  const disabled = status != null && status.enabled === false
  const needsModel = !!status?.enabled && !!status?.reachable && !status?.modelAvailable
  const label = !status
    ? 'AI…'
    : disabled
      ? compact
        ? 'AI off'
        : 'AI disabled'
      : online
        ? compact
          ? 'AI on'
          : `AI · ${status.model}`
        : needsModel
          ? compact
            ? 'AI · model'
            : 'AI · pull model'
          : compact
            ? 'AI off'
            : 'AI offline'

  return (
    <button
      type="button"
      className={`ai-chip ${online ? 'is-online' : needsModel ? 'is-waiting' : 'is-offline'}`}
      title={status?.message ?? 'Checking AI status'}
      onClick={() => void refresh()}
    >
      <span className="ai-chip-dot" aria-hidden />
      {label}
    </button>
  )
}
