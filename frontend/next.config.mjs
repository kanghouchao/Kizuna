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
  async rewrites() {
    return [];
  },
};

export default nextConfig;
