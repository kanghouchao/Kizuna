interface BannerProps {
  tenantName: string;
  bannerUrl?: string;
  description?: string;
}

export default function Banner({
  tenantName,
  bannerUrl,
  description,
}: BannerProps) {
  return (
    <section className="relative h-[500px] bg-gradient-to-r from-indigo-600 to-purple-600">
      {/* Background Image */}
      {bannerUrl && (
        <div
          className="absolute inset-0 bg-cover bg-center"
          style={{ backgroundImage: `url(${bannerUrl})` }}
        >
          <div className="absolute inset-0 bg-black/40" />
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
