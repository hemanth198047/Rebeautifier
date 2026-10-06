import { useEffect, useState } from 'react'
import { api, type AiStatus } from '../api'

type Props = {
  compact?: boolean
}

export function AiStatusChip({ compact = false }: Props) {
  const [status, setStatus] = useState<AiStatus | null>(null)

  async function refresh() {
    try {
      const data = await api<AiStatus>('/api/v1/system/ai-status')
      setStatus(data)
    } catch {
      setStatus(null)
    }
  }

  useEffect(() => {
    void refresh()
    const id = window.setInterval(() => void refresh(), 30_000)
    return () => window.clearInterval(id)
  }, [])

  const online = !!status?.available
  const disabled = status != null && status.enabled === false
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
        : compact
          ? 'AI off'
          : 'AI offline'

  return (
    <button
      type="button"
      className={`ai-chip ${online ? 'is-online' : 'is-offline'}`}
      title={status?.message ?? 'Checking AI status'}
      onClick={() => void refresh()}
    >
      <span className="ai-chip-dot" aria-hidden />
      {label}
    </button>
  )
}
