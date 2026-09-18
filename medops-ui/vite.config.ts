import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'
import { viteStaticCopy } from 'vite-plugin-static-copy'
import generateSitemap from 'vite-plugin-sitemap'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    viteStaticCopy({
      targets: [
        { src: 'public/robots.txt', dest: '.' },
        { src: 'public/manifest.webmanifest', dest: '.' },
      ],
    }),
    generateSitemap({
      hostname: 'https://medops.ai',
      dynamicRoutes: [
        '/login',
        '/register',
        '/forgot-password',
        '/verify-otp',
        '/reset-password',
        '/password-reset-success',
        '/patient/dashboard',
        '/patient/appointments',
        '/patient/book',
        '/patient/prescriptions',
        '/patient/labs',
        '/patient/records',
        '/patient/billing',
        '/patient/profile',
        '/patient/notifications',
        '/patient/help',
        '/doctor/dashboard',
        '/doctor/appointments',
        '/doctor/patients',
        '/doctor/prescriptions',
        '/doctor/labs',
        '/settings',
      ],
      changefreq: 'weekly',
      priority: 0.7,
      lastmod: new Date(),
      generateRobotsTxt: false,
    }),
  ],
  server: {
    host: true,
    port: 5173,
    strictPort: true,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    restoreMocks: true,
  },
})
