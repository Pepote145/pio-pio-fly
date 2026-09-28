import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // En desarrollo, las llamadas a /api van al backend de Spring Boot
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
