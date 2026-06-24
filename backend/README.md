# MindIsle AI Backend

This service keeps the AI provider key outside the Android application and exposes:

```text
POST /api/chat
```

## Setup

1. Copy `.env.example` to `.env`.
2. Configure `AI_API_URL`, `AI_API_KEY`, and `AI_MODEL`.
3. Install and start:

```powershell
npm.cmd install
npm.cmd start
```

The provider endpoint must accept an OpenAI-compatible chat-completions request.

## Android connection

The emulator default is:

```text
http://10.0.2.2:3000
```

To override it, add this to the Android project's ignored `local.properties`:

```text
AI_BACKEND_BASE_URL=https://your-mindisle-backend.example
```

For a physical device during local development, use an HTTPS development tunnel or
`adb reverse tcp:3000 tcp:3000` with `AI_BACKEND_BASE_URL=http://127.0.0.1:3000`.

Never put `AI_API_KEY` in the Android project.
