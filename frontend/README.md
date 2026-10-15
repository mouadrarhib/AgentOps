# AgentOps frontend

The React workspace for AgentOps. The AI assistant is the primary screen; overview, orders, and support tickets use the same authenticated Spring Boot API. The interface uses navy surfaces, cyan actions, violet assistant accents, responsive record cards, and a beUI motion button.

## Technology

- React 19, TypeScript, Vite 6, and React Router 7
- TanStack Query for API loading, caching, and mutation state
- Tailwind CSS 4 and a small set of shared workspace styles
- Phosphor icons and the beUI motion button (`src/components/motion/button/base.tsx`; license in `BEUI-LICENSE.txt`)

## Project structure

```text
src/
├── App.tsx                 # Routes, authentication guard, query client
├── components/             # Shared states, badges, buttons, confirmation dialog
├── features/
│   ├── auth/               # Login and session state
│   ├── shell/              # Navigation and account controls
│   ├── overview/           # Recent records from the API
│   ├── agent/              # Conversations and assistant chat
│   ├── orders/             # Order pages, API, hooks, components, types
│   └── tickets/            # Ticket pages, API, hooks, components, types
├── lib/api.ts              # Shared typed endpoint client
├── lib/http.ts             # Cookie, CSRF, refresh, and Problem Details handling
└── index.css               # Design tokens and responsive layouts
```

## Run locally

Start Spring Boot and PostgreSQL from the repository root. The simplest path is Docker Compose:

```powershell
Copy-Item .env.example .env
# Set DB_PASSWORD and JWT_SECRET in .env; set DEEPSEEK_API_KEY for assistant chat.
docker compose up --build -d
```

The Compose file starts the API and database; the frontend runs separately. In a second terminal:

```powershell
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The Vite proxy forwards `/api`, `/agent`, `/orders`, `/tickets`, and `/health` to `http://localhost:8080`. To run Spring Boot outside Docker instead, configure PostgreSQL and the root `.env`, then run `.\mvnw.cmd spring-boot:run` from the repository root. See the [root README](../README.md#getting-started) for prerequisites and a JWT secret command.

## Environment and API connection

`VITE_API_BASE_URL` is the frontend's only environment variable. Leave it unset (or use `/` as in `.env.example`) for the local Vite proxy. For a separately hosted frontend, set it to the Spring Boot base URL and add the exact frontend origin to the backend's `CORS_ALLOWED_ORIGINS`.

Browser authentication uses HttpOnly access and refresh cookies. The shared HTTP client sends credentials, obtains `GET /api/auth/csrf` before writes, and refreshes an expired access session. The browser does not store tokens in JavaScript or call the AI provider directly. Cookies use `SameSite=Strict`, so a separately deployed cookie-authenticated frontend must be on the same site as the API. Set the provider key only in the backend environment.

The frontend has a **login page only**. Sign in with an existing account. The backend still provides `POST /api/auth/register` for account creation through the API; there is no registration route in this UI. Spring Boot is responsible for ownership checks: customers see their own orders and tickets, while SUPPORT and ADMIN can update tickets. UI role checks only control which actions are displayed.

## Current screens

- **AI assistant:** owned conversation history, paginated messages, live chat, and real API error states.
- **Overview:** counts and recent orders/tickets from live API responses, with the assistant as the main action.
- **Orders:** paginated list and detail pages for records visible to the signed-in account.
- **Support tickets:** list, backend-supported filters, detail pages, ticket creation, and staff-only updates with confirmation.

The backend does not expose order editing, deletion, or list filtering. Ticket responses include IDs for customers and assignees, not their display names. The current repository has no up-to-date screenshot of the dark-theme application, so this README does not present the older light-theme image as a current screen.
