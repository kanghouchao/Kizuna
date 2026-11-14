import process from 'node:process';

/** @type {import('next').NextConfig} */
const nextConfig = {
  logging: {
    fetches: {
      fullUrl: true,
    },
  },
  images: {
    unoptimized: true,
  },
  output: 'standalone',
  // 開発モードだけに効く。目視用スタックは localhost ではなく店舗のホスト名で開くため、
  // 許可しないと HMR の接続が拒否され、編集が画面に反映されない
  allowedDevOrigins: process.env.APP_DOMAIN
    ? [process.env.APP_DOMAIN, `*.${process.env.APP_DOMAIN}`]
    : [],
  async rewrites() {
    return [];
  },
};

export default nextConfig;
