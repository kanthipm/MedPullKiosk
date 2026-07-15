/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'sans-serif'],
      },
      // Design tokens ported from orthopedic-demo/assets/styles.css
      colors: {
        ink: '#0f1830',
        body: '#39435c',
        muted: '#6b7793',
        faint: '#8a93a8',
        line: '#eef1f7',
        soft: '#f5f7fc',
        oxy: { DEFAULT: '#2f80ed', light: '#56ccf2' },
        risk: {
          high: '#e5484d',
          'high-bg': '#fdecec',
          med: '#e07b00',
          'med-bg': '#fff4e5',
          missing: '#7c879e',
          'missing-bg': '#f0f2f7',
          low: '#0a9d57',
          'low-bg': '#e7f8ef',
        },
      },
      borderRadius: {
        card: '22px',
        row: '16px',
        btn: '14px',
      },
      boxShadow: {
        card: '0 10px 26px rgba(20,30,60,.06)',
        row: '0 6px 16px rgba(20,30,60,.05)',
        lift: '0 12px 26px rgba(20,30,60,.12)',
        glass: '0 14px 34px rgba(20,30,60,.16)',
        'high-row': '0 8px 22px rgba(229,72,77,.16)',
        segment: '0 3px 10px rgba(20,30,60,.12)',
      },
      keyframes: {
        rise: {
          from: { opacity: '0', transform: 'translateY(10px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        shimmer: {
          from: { backgroundPosition: '-900px 0' },
          to: { backgroundPosition: '900px 0' },
        },
        toastIn: {
          from: { opacity: '0', transform: 'translateY(14px) scale(.97)' },
          to: { opacity: '1', transform: 'translateY(0) scale(1)' },
        },
        modalIn: {
          from: { opacity: '0', transform: 'translateY(8px) scale(.97)' },
          to: { opacity: '1', transform: 'translateY(0) scale(1)' },
        },
        fadeIn: {
          from: { opacity: '0' },
          to: { opacity: '1' },
        },
      },
      animation: {
        // 'backwards' (not 'both'): a lingering identity transform would turn
        // every risen card into a containing block for fixed-position children.
        rise: 'rise .4s cubic-bezier(.22,.9,.35,1) backwards',
        shimmer: 'shimmer 1.6s linear infinite',
        toastIn: 'toastIn .25s cubic-bezier(.22,.9,.35,1) both',
        modalIn: 'modalIn .2s cubic-bezier(.22,.9,.35,1) both',
        fadeIn: 'fadeIn .2s ease-out both',
      },
    },
  },
  plugins: [],
}
