import { Link } from 'react-router-dom'
import type { ReactNode } from 'react'

type Props = {
  mode: 'login' | 'register'
  children: ReactNode
}

const FEATURES = [
  {
    title: 'Structured library',
    body: 'Books, chapters, and sections keep long-form reading notes in a hierarchy that still feels light.',
  },
  {
    title: 'Working notes',
    body: 'Draft freely, keep originals intact, and mark saved work when a thought is ready to keep.',
  },
  {
    title: 'Local AI refine',
    body: 'Grammar, organize, summary, takeaways, and Q&A — optionally powered by Ollama on your machine.',
  },
  {
    title: 'Free cover art',
    body: 'Browse Openverse, Lorem Picsum, and Wikimedia Commons, then store covers with the book.',
  },
  {
    title: 'Search & backup',
    body: 'Find notes across the library and export Markdown or JSON — including covers — for recovery.',
  },
  {
    title: 'Share carefully',
    body: 'Invite editors or viewers per book, transfer ownership, and keep installs private by default.',
  },
]

const STEPS = [
  { n: '01', title: 'Create a book', body: 'Give the project a title, optional description, and a cover.' },
  { n: '02', title: 'Outline chapters', body: 'Drag to reorder. Nest sections when a chapter grows.' },
  { n: '03', title: 'Capture notes', body: 'Attach thoughts to book, chapter, or section with tags and pages.' },
  { n: '04', title: 'Refine when ready', body: 'Run AI polish offline, review revisions, accept what stays.' },
]

const CAPABILITIES = [
  'Self-hosted Compose stack',
  'Session auth + CSRF',
  'PostgreSQL persistence',
  'Ollama or OpenAI-compatible',
  'Rate-limited AI & auth',
  'JSON / Markdown export',
]

export function AuthLandingLayout({ mode, children }: Props) {
  const formId = 'auth-panel'
  const isLogin = mode === 'login'

  return (
    <div className="landing">
      <header className="landing-nav">
        <a className="landing-brand" href="#top">
          Rebeautifier<span>.</span>
        </a>
        <nav className="landing-nav-links" aria-label="Landing">
          <a href="#features">Features</a>
          <a href="#workflow">Workflow</a>
          <a href="#platform">Platform</a>
          <a href={`#${formId}`}>{isLogin ? 'Sign in' : 'Create account'}</a>
        </nav>
        <div className="landing-nav-actions">
          {isLogin ? (
            <Link className="btn btn-ghost" to="/register">
              Create account
            </Link>
          ) : (
            <Link className="btn btn-ghost" to="/login">
              Sign in
            </Link>
          )}
          <a className="btn btn-primary" href={`#${formId}`}>
            {isLogin ? 'Open library' : 'Get started'}
          </a>
        </div>
      </header>

      <main id="top">
        <section className="landing-hero">
          <div className="landing-hero-copy">
            <p className="landing-eyebrow">Self-hosted reading workspace</p>
            <h1 className="landing-brand-hero">Rebeautifier</h1>
            <p className="landing-lead">
              A professional home for reading notes — structured like a library, writable like a journal, and
              refined with optional local AI when you are ready.
            </p>
            <div className="landing-hero-cta">
              <a className="btn btn-primary" href={`#${formId}`}>
                {isLogin ? 'Sign in to continue' : 'Create your account'}
              </a>
              <a className="btn btn-ghost landing-hero-secondary" href="#features">
                Explore the product
              </a>
            </div>
            <ul className="landing-hero-meta" aria-label="Highlights">
              <li>Books → chapters → sections</li>
              <li>Notes with drafts & versions</li>
              <li>Local-first AI refine</li>
              <li>Backup & share controls</li>
            </ul>
          </div>

          <div className="landing-hero-stage" aria-hidden="true">
            <div className="landing-stage-glow" />
            <div className="landing-stage-board">
              <div className="landing-stage-topbar">
                <span className="landing-stage-dot" />
                <span className="landing-stage-dot" />
                <span className="landing-stage-dot" />
                <span className="landing-stage-path">Library / Morning Pages / Notes</span>
              </div>
              <div className="landing-stage-body">
                <aside className="landing-stage-rail">
                  <div className="landing-stage-book is-active">
                    <span className="landing-stage-cover" />
                    <div>
                      <strong>Morning Pages</strong>
                      <em>Owner</em>
                    </div>
                  </div>
                  <div className="landing-stage-book">
                    <span className="landing-stage-cover alt" />
                    <div>
                      <strong>Field Notes</strong>
                      <em>Editor</em>
                    </div>
                  </div>
                  <div className="landing-stage-book">
                    <span className="landing-stage-cover alt2" />
                    <div>
                      <strong>Archive Shelf</strong>
                      <em>Viewer</em>
                    </div>
                  </div>
                </aside>
                <div className="landing-stage-main">
                  <div className="landing-stage-note-head">
                    <strong>Why this book exists</strong>
                    <span className="landing-stage-pill">Saved</span>
                  </div>
                  <p className="landing-stage-note-body">
                    Morning Pages is a soft landing for thoughts that arrive before coffee. Nothing here needs to
                    be finished on the first pass — keep the original, then refine when the idea settles.
                  </p>
                  <div className="landing-stage-chips">
                    <span>purpose</span>
                    <span>ritual</span>
                    <span>draft → saved</span>
                  </div>
                  <div className="landing-stage-refine">
                    <div className="landing-stage-refine-bar" />
                    <span>Refine · Organize · Local Ollama</span>
                  </div>
                </div>
              </div>
            </div>
            <div className="landing-stage-float landing-stage-float-a">
              <strong>Content version</strong>
              <span>v12 · conflict-safe edits</span>
            </div>
            <div className="landing-stage-float landing-stage-float-b">
              <strong>AI online</strong>
              <span>llama3.2:1b ready</span>
            </div>
          </div>
        </section>

        <section className="landing-strip" aria-label="Product signals">
          <div>
            <strong>Install once</strong>
            <span>Docker Compose + Postgres + optional Ollama</span>
          </div>
          <div>
            <strong>Own the data</strong>
            <span>Covers volume, SQL dumps, JSON book backup</span>
          </div>
          <div>
            <strong>Work offline-capable</strong>
            <span>Notes always work; AI is optional</span>
          </div>
          <div>
            <strong>Team-ready</strong>
            <span>Roles, invites, ownership transfer</span>
          </div>
        </section>

        <section id="features" className="landing-section">
          <div className="landing-section-head">
            <p className="landing-eyebrow">Capabilities</p>
            <h2>Built for serious reading notes — not another scratchpad.</h2>
            <p className="landing-section-lead">
              Rebeautifier keeps hierarchy, revision safety, and AI assist in one self-hosted product surface.
            </p>
          </div>
          <div className="landing-feature-grid">
            {FEATURES.map((feature) => (
              <article key={feature.title} className="landing-feature">
                <h3>{feature.title}</h3>
                <p>{feature.body}</p>
              </article>
            ))}
          </div>
        </section>

        <section id="workflow" className="landing-section landing-section-alt">
          <div className="landing-section-head">
            <p className="landing-eyebrow">Workflow</p>
            <h2>From empty shelf to refined takeaways.</h2>
            <p className="landing-section-lead">
              A clear path for daily capture without losing the long-term shape of a book.
            </p>
          </div>
          <ol className="landing-steps">
            {STEPS.map((step) => (
              <li key={step.n}>
                <span className="landing-step-n">{step.n}</span>
                <div>
                  <h3>{step.title}</h3>
                  <p>{step.body}</p>
                </div>
              </li>
            ))}
          </ol>
        </section>

        <section id="platform" className="landing-section">
          <div className="landing-platform">
            <div>
              <p className="landing-eyebrow">Platform</p>
              <h2>Professional ops without a SaaS tax.</h2>
              <p className="landing-section-lead">
                Designed for operators who want a polished reading-notes product on their own stack — with CSRF
                sessions, rate limits, health checks, and install scripts that behave on macOS, Linux, and Windows.
              </p>
              <ul className="landing-capability-list">
                {CAPABILITIES.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </div>
            <div className="landing-platform-panel">
              <h3>Demo credentials</h3>
              <p className="muted">Seeded when demo data is enabled on this install.</p>
              <dl className="landing-demo-dl">
                <div>
                  <dt>Reader</dt>
                  <dd>
                    <code>reader</code> / <code>password123</code>
                  </dd>
                </div>
                <div>
                  <dt>Editor</dt>
                  <dd>
                    <code>editor</code> / <code>password123</code>
                  </dd>
                </div>
              </dl>
              <p className="landing-platform-note">
                Turn seeding off for production installs. AI stays off until you enable it in Settings.
              </p>
            </div>
          </div>
        </section>

        <section id={formId} className="landing-auth-section">
          <div className="landing-auth-layout">
            <div className="landing-auth-copy">
              <p className="landing-eyebrow">{isLogin ? 'Welcome back' : 'Join this install'}</p>
              <h2>{isLogin ? 'Sign in and open your library.' : 'Create an account on this installation.'}</h2>
              <p className="landing-section-lead">
                {isLogin
                  ? 'Your session stays on this host. CSRF-protected sign-in keeps mutating requests honest.'
                  : 'Accounts are local to this deployment — no cloud identity provider required.'}
              </p>
              <ul className="landing-auth-bullets">
                <li>Library, search, settings, and backup in one shell</li>
                <li>Covers, notes, and revisions persist in your volumes</li>
                <li>Optional Ollama refine after you enable AI</li>
              </ul>
            </div>
            <div className="landing-auth-card">{children}</div>
          </div>
        </section>
      </main>

      <footer className="landing-footer">
        <div>
          <strong className="landing-brand">
            Rebeautifier<span>.</span>
          </strong>
          <p>Read carefully. Write freely. Refine on your terms.</p>
        </div>
        <div className="landing-footer-links">
          <a href="#features">Features</a>
          <a href="#workflow">Workflow</a>
          <a href="#platform">Platform</a>
          <Link to={isLogin ? '/register' : '/login'}>{isLogin ? 'Create account' : 'Sign in'}</Link>
        </div>
      </footer>
    </div>
  )
}
