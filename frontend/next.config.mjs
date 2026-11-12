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
  // 試作の目視確認用: QA スタックの gateway（kizuna.test）越しに next dev を開く
  allowedDevOrigins: ['kizuna.test'],
  async rewrites() {
    return [];
  },
};

export default nextConfig;
