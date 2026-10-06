import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
  type FormEvent,
  type ReactNode,
} from 'react'

export type ConfirmOptions = {
  title: string
  message: string
  confirmLabel?: string
  cancelLabel?: string
  danger?: boolean
}

export type PromptOptions = {
  title: string
  message?: string
  defaultValue?: string
  placeholder?: string
  confirmLabel?: string
  cancelLabel?: string
}

type DialogApi = {
  confirm: (options: ConfirmOptions) => Promise<boolean>
  prompt: (options: PromptOptions) => Promise<string | null>
}

const DialogContext = createContext<DialogApi | null>(null)

type ConfirmState = ConfirmOptions & {
  resolve: (value: boolean) => void
}

type PromptState = PromptOptions & {
  resolve: (value: string | null) => void
}

export function DialogProvider({ children }: { children: ReactNode }) {
  const [confirmState, setConfirmState] = useState<ConfirmState | null>(null)
  const [promptState, setPromptState] = useState<PromptState | null>(null)
  const [promptValue, setPromptValue] = useState('')
  const confirmTitleId = useId()
  const promptTitleId = useId()
  const promptInputRef = useRef<HTMLInputElement>(null)
  const confirmPrimaryRef = useRef<HTMLButtonElement>(null)

  const confirm = useCallback((options: ConfirmOptions) => {
    return new Promise<boolean>((resolve) => {
      setConfirmState({ ...options, resolve })
    })
  }, [])

  const prompt = useCallback((options: PromptOptions) => {
    return new Promise<string | null>((resolve) => {
      setPromptValue(options.defaultValue ?? '')
      setPromptState({ ...options, resolve })
    })
  }, [])

  const api = useMemo(() => ({ confirm, prompt }), [confirm, prompt])

  useEffect(() => {
    if (confirmState) {
      confirmPrimaryRef.current?.focus()
    }
  }, [confirmState])

  useEffect(() => {
    if (promptState) {
      promptInputRef.current?.focus()
      promptInputRef.current?.select()
    }
  }, [promptState])

  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.key !== 'Escape') return
      if (promptState) {
        promptState.resolve(null)
        setPromptState(null)
      } else if (confirmState) {
        confirmState.resolve(false)
        setConfirmState(null)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [confirmState, promptState])

  function closeConfirm(value: boolean) {
    confirmState?.resolve(value)
    setConfirmState(null)
  }

  function closePrompt(value: string | null) {
    promptState?.resolve(value)
    setPromptState(null)
  }

  function onPromptSubmit(event: FormEvent) {
    event.preventDefault()
    const next = promptValue.trim()
    if (!next) return
    closePrompt(next)
  }

  return (
    <DialogContext.Provider value={api}>
      {children}
      {confirmState && (
        <div
          className="modal-backdrop"
          role="presentation"
          onClick={() => closeConfirm(false)}
        >
          <div
            className={`modal-sheet modal-sheet-sm ${confirmState.danger ? 'is-danger' : ''}`}
            role="alertdialog"
            aria-modal="true"
            aria-labelledby={confirmTitleId}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-header">
              <h2 id={confirmTitleId}>{confirmState.title}</h2>
            </div>
            <p className="modal-body">{confirmState.message}</p>
            <div className="modal-actions">
              <button className="btn btn-ghost" type="button" onClick={() => closeConfirm(false)}>
                {confirmState.cancelLabel ?? 'Cancel'}
              </button>
              <button
                ref={confirmPrimaryRef}
                className={`btn ${confirmState.danger ? 'btn-danger' : 'btn-primary'}`}
                type="button"
                onClick={() => closeConfirm(true)}
              >
                {confirmState.confirmLabel ?? 'Confirm'}
              </button>
            </div>
          </div>
        </div>
      )}
      {promptState && (
        <div
          className="modal-backdrop"
          role="presentation"
          onClick={() => closePrompt(null)}
        >
          <div
            className="modal-sheet modal-sheet-sm"
            role="dialog"
            aria-modal="true"
            aria-labelledby={promptTitleId}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-header">
              <h2 id={promptTitleId}>{promptState.title}</h2>
            </div>
            {promptState.message && <p className="modal-body">{promptState.message}</p>}
            <form className="modal-form" onSubmit={onPromptSubmit}>
              <label>
                <span className="sr-only">Value</span>
                <input
                  ref={promptInputRef}
                  value={promptValue}
                  onChange={(e) => setPromptValue(e.target.value)}
                  placeholder={promptState.placeholder}
                  required
                />
              </label>
              <div className="modal-actions">
                <button className="btn btn-ghost" type="button" onClick={() => closePrompt(null)}>
                  {promptState.cancelLabel ?? 'Cancel'}
                </button>
                <button className="btn btn-primary" type="submit">
                  {promptState.confirmLabel ?? 'Continue'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </DialogContext.Provider>
  )
}

export function useDialog(): DialogApi {
  const ctx = useContext(DialogContext)
  if (!ctx) {
    throw new Error('useDialog must be used within DialogProvider')
  }
  return ctx
}
