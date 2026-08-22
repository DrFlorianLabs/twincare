import { fileURLToPath, URL } from 'node:url'
import Vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const environment = loadEnv(mode, process.cwd(), '')
  const developmentApiTarget = environment.VITE_DEV_API_TARGET?.trim()

  return {
    plugins: [Vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('src', import.meta.url)),
      },
      extensions: ['.js', '.json', '.mjs', '.vue'],
    },
    server: {
      port: 3000,
      proxy: developmentApiTarget
        ? {
            '/api': {
              target: developmentApiTarget,
              changeOrigin: false,
            },
          }
        : undefined,
    },
  }
})
