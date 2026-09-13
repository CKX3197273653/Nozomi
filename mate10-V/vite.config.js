import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  assetsInclude: ['**/*.glb'],
  resolve:{
    alias:{
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  optimizeDeps: {
    force: true,
    include: [
      'echarts',
      'element-plus',
      '@element-plus/icons-vue',
      'axios',
      'pinia',
      'vue-router',
      'three'
    ]
  },
  server:{
    port:5173,
    proxy:{
      '/blocker': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/api':{
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/rag':{
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }


})
