-- Per-user AI provider settings (enable/disable, Ollama or OpenAI-compatible API key).
CREATE TABLE user_ai_settings (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    provider VARCHAR(32) NOT NULL DEFAULT 'OLLAMA',
    model_name VARCHAR(200) NOT NULL DEFAULT 'llama3.2:1b',
    base_url VARCHAR(500),
    api_key TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT user_ai_settings_provider_chk CHECK (provider IN ('OLLAMA', 'OPENAI_COMPATIBLE'))
);
