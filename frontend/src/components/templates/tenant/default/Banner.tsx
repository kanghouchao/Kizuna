interface BannerProps {
  tenantName: string;
  bannerUrl?: string;
  description?: string;
}

function sanitizeBannerUrl(url: string | undefined): string | undefined {
  if (!url) {
    return undefined;
  }
  const trimmed = url.trim();

  // Allow relative paths (served from the same origin / CDN path)
  if (trimmed.startsWith('/')) {
    return trimmed;
  }

  try {
    const parsed = new URL(trimmed);
    const allowedProtocols = ['http:', 'https:'];
    if (allowedProtocols.includes(parsed.protocol)) {
      return parsed.toString();
    }
  } catch {
    // Invalid URL, fall through to return undefined
  }

  return undefined;
}

export default function Banner({ tenantName, bannerUrl, description }: BannerProps) {
  const safeBannerUrl = sanitizeBannerUrl(bannerUrl);

  return (
    <section className="relative h-[500px] bg-gradient-to-r from-indigo-600 to-purple-600">
      {/* Background Image */}
      {safeBannerUrl && (
        <div
          className="absolute inset-0 bg-cover bg-center"
          style={{ backgroundImage: `url("${safeBannerUrl}")` }}
        >
          <div className="absolute inset-0 bg-black/50" aria-hidden="true" />
        </div>
      )}

      {/* Content */}
      <div className="relative z-10 flex flex-col items-center justify-center h-full text-white text-center px-4">
        <h1 className="text-4xl md:text-6xl font-bold mb-4">{tenantName}</h1>
        <p className="text-xl md:text-2xl mb-8 max-w-2xl">
          {description || 'ようこそ、最高のおもてなしをお約束します'}
        </p>
        <a
          href="#cast"
          className="bg-white text-indigo-600 px-8 py-3 rounded-full font-semibold hover:bg-gray-100 transition-colors"
        >
          キャストを見る
        </a>
      </div>
    </section>
  );
}
