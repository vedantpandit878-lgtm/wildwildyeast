/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          black: '#1A1A1A',
          rust: '#8B3A1A',
          amber: '#C97B2A',
          gold: '#D4A020',
          cream: '#F5F0E8',
          charcoal: '#2C2C2A',
          white: '#FFFFFF',
        },
      },
      fontFamily: {
        display: ['"Playfair Display"', 'serif'],
        sans: ['Inter', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
