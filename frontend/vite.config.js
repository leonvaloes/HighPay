import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/actuator': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/v3': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/swagger-ui.html': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
