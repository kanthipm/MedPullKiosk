import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'
import fs from 'node:fs'
import path from 'node:path'

// Read GROK_API_KEY from the Android local.properties so `npm run dev`
// automatically uses the same key as the kiosk app — no manual config needed.
function readLocalProperties(): Record<string, string> {
  const localProps = path.resolve(__dirname, '../MedPullKiosk/local.properties')
  if (!fs.existsSync(localProps)) return {}
  return Object.fromEntries(
    fs.readFileSync(localProps, 'utf-8')
      .split('\n')
      .filter(l => l.includes('=') && !l.startsWith('#'))
      .map(l => l.split('=').map(s => s.trim()) as [string, string])
  )
}

// https://vite.dev/config/
export default defineConfig(({ command }) => {
  // Only inject the API key during `vite dev` — never baked into production builds
  // (the APK bridge handles auth in production; no key should appear in committed assets)
  const localProps = command === 'serve' ? readLocalProperties() : {}
  const grokKey = localProps['GROK_API_KEY'] ?? localProps['GROQ_API_KEY'] ?? ''

  return {
    plugins: [react()],
    base: './',  // relative paths — required for Android WebViewAssetLoader
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    define: {
      __LOCAL_API_KEY__: JSON.stringify(grokKey),
    },
  }
})
