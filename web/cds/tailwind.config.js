/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        // CDS-specific warm light palette — aligned to the POS brand but its own tokens.
        background: '#FAF8F5',
        surface: '#FFFFFF',
        cdsborder: '#E8DED2',
        textPrimary: '#0F172A',
        textSecondary: '#5F6368',
        accent: {
          DEFAULT: '#A9652B',
          soft: '#F4E8DC',
          dark: '#8A4B24',
        },
        cdssuccess: '#2E7D32',
        discount: '#C96A2B',
      },
      borderRadius: {
        cds: '24px',
        'cds-lg': '28px',
        'cds-sm': '20px',
      },
      boxShadow: {
        cds: '0 1px 2px rgba(15, 23, 42, 0.04), 0 4px 16px rgba(15, 23, 42, 0.04)',
      },
      fontFamily: {
        sans: ['DM Sans', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
