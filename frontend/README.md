# AgentOps frontend

React 19 + TypeScript + Vite support workspace for the Spring Boot API at the repository root. The UI follows the AgentOps design system: navy surfaces, cyan actions, violet agent accents, responsive layouts, and the existing beUI motion button.

## Run locally

Start the backend after configuring the root `.env`:

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

Open `http://localhost:5173`. Vite proxies `/api`, `/agent`, `/orders`, `/tickets`, and `/health` to `http://localhost:8080`.

## Features

- Cookie-based registration, sign-in, session restoration, refresh, and logout.
- Overview built from real order and ticket records.
- Paginated order and ticket lists with record details and ticket filters.
- Ticket creation for an order ID and category; staff can update ticket status, priority, and assignment.
- Assistant conversations, paginated message history, and live responses from `POST /agent/chat`.
- Loading, empty, authorization, network, and action feedback.

The browser keeps authentication in HttpOnly cookies and sends a CSRF token on writes. A tool-call count appears only when the chat API returns it; individual tool traces are not exposed by the current API.

## Configuration

For a separately hosted frontend, set `VITE_API_BASE_URL` to the backend base URL and add the frontend's exact origin to root `CORS_ALLOWED_ORIGINS`. The backend's auth cookies use `SameSite=Strict`; cross-site cookie authentication is therefore not supported. Use a same-site deployment for cookie auth. Local development on `localhost:5173` and `localhost:8080` works with the default CORS origin.

The browser never connects directly to the LLM provider. Configure the provider key on the backend only.

## Quality checks

```powershell
npm run lint
npm run typecheck
npm test
npm run build
```

The animated button in `src/components/motion/button/base.tsx` comes from [beUI](https://beui.dev/components/motion/button). Its MIT notice is in `BEUI-LICENSE.txt`.
