import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: { alias: { '@': path.resolve(__dirname, 'src') } },
  test: { setupFiles: ['./src/setupTests.ts'] },
  server: { proxy: Object.fromEntries(['/api', '/agent', '/orders', '/tickets', '/health'].map(route => [route, 'http://localhost:8080'])) },
})
