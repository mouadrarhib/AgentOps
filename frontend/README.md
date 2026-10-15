# AgentOps frontend

Standalone React 19 + TypeScript + Vite app for the Spring Boot API at the repository root. The apps have separate builds. The frontend uses the existing REST endpoints; it does not need changes to Maven, Docker Compose, or the backend CI workflow.

## Run locally

Start the backend on port 8080, then:

```powershell
npm install
npm run dev
```

Vite proxies API paths to the backend. Open the URL shown by Vite, register or sign in, and use Orders, Tickets, and AI assistant. Chat needs `DEEPSEEK_API_KEY` configured on the backend.

```powershell
npm run build
npm run preview
```

`VITE_API_BASE_URL` defaults to `/`. Use a same-origin API proxy in production, or set it to the backend origin and configure cross-origin access at deployment. The client holds Bearer and refresh tokens in memory, so reloading the page requires signing in again.

The animated button in `src/components/motion/button/base.tsx` comes from [beUI](https://beui.dev/components/motion/button). Its MIT notice is included in `BEUI-LICENSE.txt`.
