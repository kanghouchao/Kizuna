interface Ad {
  id: string;
  imageUrl?: string;
  title: string;
  description?: string;
  linkUrl?: string;
}

interface AdvertisementProps {
  ads?: Ad[];
}

// Placeholder ads for demo
const placeholderAds: Ad[] = [
  {
    id: '1',
    title: '新人割引キャンペーン',
    description: '初回ご利用のお客様限定！20%OFF',
  },
  {
    id: '2',
    title: '平日限定サービス',
    description: '平日14:00-18:00はお得な料金でご案内',
  },
  {
    id: '3',
    title: 'ポイント2倍デー',
    description: '毎週金曜日はポイント2倍！',
  },
];

export default function Advertisement({ ads }: AdvertisementProps) {
  const displayAds = ads && ads.length > 0 ? ads : placeholderAds;

  return (
    <section className="py-16 bg-white">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <h2 className="text-3xl font-bold text-center mb-4 text-gray-800">キャンペーン情報</h2>
        <p className="text-center text-gray-600 mb-12">お得なキャンペーン・イベント情報</p>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          {displayAds.map(ad => (
            <div
              key={ad.id}
              className="bg-gradient-to-br from-indigo-50 to-purple-50 rounded-xl p-6 shadow-md hover:shadow-lg transition-shadow"
            >
              {/* Ad Image or Placeholder */}
              {ad.imageUrl ? (
                <div className="aspect-video rounded-lg overflow-hidden mb-4">
                  <img src={ad.imageUrl} alt={ad.title} className="w-full h-full object-cover" />
                </div>
              ) : (
                <div className="aspect-video bg-gradient-to-r from-indigo-400 to-purple-400 rounded-lg mb-4 flex items-center justify-center">
                  <svg
                    className="w-12 h-12 text-white"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M11 5.882V19.24a1.76 1.76 0 01-3.417.592l-2.147-6.15M18 13a3 3 0 100-6M5.436 13.683A4.001 4.001 0 017 6h1.832c4.1 0 7.625-1.234 9.168-3v14c-1.543-1.766-5.067-3-9.168-3H7a3.988 3.988 0 01-1.564-.317z"
                    />
                  </svg>
                </div>
              )}

              <h3 className="text-xl font-bold text-gray-800 mb-2">{ad.title}</h3>
              {ad.description && <p className="text-gray-600">{ad.description}</p>}

              {ad.linkUrl && (
                <a
                  href={ad.linkUrl}
                  className="inline-block mt-4 text-indigo-600 hover:text-indigo-800 font-medium"
                >
                  詳細を見る →
                </a>
              )}
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
