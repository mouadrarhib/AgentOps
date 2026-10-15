# AgentOps frontend · Task 1

Standalone React 19 + TypeScript + Vite app for the Spring Boot backend at the repository root. The backend files and CI workflow remain independent.

## Run locally

From the repository root, start the backend after configuring `.env`:

```powershell
docker compose up --build -d
# Or, with local PostgreSQL and JDK 25:
.\mvnw.cmd spring-boot:run
```

In a second terminal:

```powershell
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The Vite dev server proxies `/api` to `http://localhost:8080`. To verify the frontend:

```powershell
npm run lint
npm run typecheck
npm test
npm run build
```

## Authentication

The browser uses the backend's HttpOnly `AGENTOPS_ACCESS` and `AGENTOPS_REFRESH` cookies. It never saves the token values returned in JSON. `GET /api/auth/me` restores a session after reload. A 401 triggers one cookie-based refresh request, then retries the original request once. Refresh rotates the server-side token and cookie. Every unsafe request first obtains `GET /api/auth/csrf` and sends its `X-XSRF-TOKEN` header. Logout asks the backend to revoke the current session and clear both cookies.

For an independently hosted frontend, configure `VITE_API_BASE_URL` and the backend's `CORS_ALLOWED_ORIGINS`. Cookie deployments must use an origin arrangement compatible with the backend's `SameSite=Strict` cookies and HTTPS with `AUTH_COOKIE_SECURE=true`.

Orders, Tickets, and AI Assistant are protected route placeholders for later tasks.

The animated button in `src/components/motion/button/base.tsx` comes from [beUI](https://beui.dev/components/motion/button). Its MIT notice is in `BEUI-LICENSE.txt`.
