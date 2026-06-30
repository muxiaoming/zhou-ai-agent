import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8123',
        changeOrigin: true,
      },
      '/engine': {
        target: 'http://localhost:8182',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/engine/, '/api'),
      },
    },
  },
})
