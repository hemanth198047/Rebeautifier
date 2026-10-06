import { useEditor, EditorContent } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import Placeholder from '@tiptap/extension-placeholder'
import Underline from '@tiptap/extension-underline'
import Link from '@tiptap/extension-link'
import { useEffect } from 'react'

type Props = {
  value: string
  onChange: (html: string) => void
  editable?: boolean
  placeholder?: string
}

export function RichTextEditor({
  value,
  onChange,
  editable = true,
  placeholder = 'Start writing…',
}: Props) {
  const editor = useEditor({
    immediatelyRender: false,
    extensions: [
      StarterKit.configure({
        heading: { levels: [2, 3] },
      }),
      Underline,
      Link.configure({
        openOnClick: false,
        HTMLAttributes: { class: 'editor-link' },
      }),
      Placeholder.configure({ placeholder }),
    ],
    content: value || '',
    editable,
    onUpdate: ({ editor: ed }) => {
      onChange(ed.getHTML())
    },
    editorProps: {
      attributes: {
        class: 'tiptap-surface',
        spellcheck: 'true',
      },
    },
  })

  useEffect(() => {
    if (!editor) return
    editor.setEditable(editable)
  }, [editor, editable])

  useEffect(() => {
    if (!editor) return
    const current = editor.getHTML()
    const next = value || ''
    if (next !== current) {
      editor.commands.setContent(next, { emitUpdate: false })
    }
  }, [editor, value])

  if (!editor) {
    return <div className="editor-shell editor-loading">Loading editor…</div>
  }

  return (
    <div className={`editor-shell ${editable ? '' : 'is-readonly'}`}>
      {editable && (
        <div className="editor-toolbar" role="toolbar" aria-label="Formatting">
          <ToolbarButton
            label="Bold"
            active={editor.isActive('bold')}
            onClick={() => editor.chain().focus().toggleBold().run()}
          />
          <ToolbarButton
            label="Italic"
            active={editor.isActive('italic')}
            onClick={() => editor.chain().focus().toggleItalic().run()}
          />
          <ToolbarButton
            label="Underline"
            active={editor.isActive('underline')}
            onClick={() => editor.chain().focus().toggleUnderline().run()}
          />
          <span className="toolbar-sep" />
          <ToolbarButton
            label="H2"
            active={editor.isActive('heading', { level: 2 })}
            onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}
          />
          <ToolbarButton
            label="H3"
            active={editor.isActive('heading', { level: 3 })}
            onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()}
          />
          <span className="toolbar-sep" />
          <ToolbarButton
            label="Bullets"
            active={editor.isActive('bulletList')}
            onClick={() => editor.chain().focus().toggleBulletList().run()}
          />
          <ToolbarButton
            label="Numbered"
            active={editor.isActive('orderedList')}
            onClick={() => editor.chain().focus().toggleOrderedList().run()}
          />
          <ToolbarButton
            label="Quote"
            active={editor.isActive('blockquote')}
            onClick={() => editor.chain().focus().toggleBlockquote().run()}
          />
          <span className="toolbar-sep" />
          <ToolbarButton
            label="Link"
            active={editor.isActive('link')}
            onClick={() => {
              const prev = editor.getAttributes('link').href as string | undefined
              const url = window.prompt('Link URL', prev ?? 'https://')
              if (url === null) return
              if (url.trim() === '') {
                editor.chain().focus().unsetLink().run()
                return
              }
              editor.chain().focus().extendMarkRange('link').setLink({ href: url.trim() }).run()
            }}
          />
          <ToolbarButton label="Clear" onClick={() => editor.chain().focus().unsetAllMarks().clearNodes().run()} />
        </div>
      )}
      <EditorContent editor={editor} />
    </div>
  )
}

function ToolbarButton({
  label,
  active,
  onClick,
}: {
  label: string
  active?: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      className={`toolbar-btn ${active ? 'is-active' : ''}`}
      onClick={onClick}
      aria-pressed={active ?? false}
    >
      {label}
    </button>
  )
}
