/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          DEFAULT: '#FF5C00',
          50:  '#FFF3EC',
          100: '#FFE4D0',
          200: '#FFCAAA',
          300: '#FF9F6B',
          400: '#FF7A33',
          500: '#FF5C00',
          600: '#D94D00',
          700: '#B03C00',
          800: '#8C2F00',
          900: '#6B2400',
        },
      },
      fontFamily: {
        sans: ['DM Sans', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'monospace'],
      },
    },
  },
  plugins: [],
}
