/** @type {import('postcss').Config} */
// eslint-disable-next-line no-undef
const isProduction = process.env.NODE_ENV === 'production';

const config = {
  plugins: {
    '@tailwindcss/postcss': {},
    autoprefixer: {},
    ...(isProduction ? { cssnano: { preset: 'default' } } : {}),
  },
};

export default config;
