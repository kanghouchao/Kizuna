import Image from 'next/image';

interface Ad {
  id: string;
  title: string;
  description?: string;
  imageUrl?: string;
  linkUrl?: string;
}

interface AdvertisementProps {
  ads?: Ad[];
}

// Placeholder advertisement data
const placeholderAds: Ad[] = [
  {
    id: '1',
    title: '新人割引キャンペーン',
    description: '新人キャストご指名で20%OFF！',
  },
  {
    id: '2',
    title: '平日限定イベント',
    description: '平日のご利用でポイント2倍！',
  },
  {
    id: '3',
    title: 'LINE友達追加特典',
    description: 'LINE登録で初回1000円OFF！',
  },
];

export default function Advertisement({ ads }: AdvertisementProps) {
  const displayAds = ads && ads.length > 0 ? ads : placeholderAds;

  return (
    <section className="py-16 bg-gradient-to-r from-pink-50 to-purple-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <h2 className="text-3xl font-bold text-center mb-4 text-gray-800">キャンペーン情報</h2>
        <p className="text-center text-gray-600 mb-12">お得な情報をお見逃しなく！</p>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          {displayAds.map(ad => (
            <div
              key={ad.id}
              className="bg-white rounded-xl shadow-lg overflow-hidden hover:shadow-xl transition-shadow"
            >
              {/* Ad Image */}
              <div className="h-48 bg-gradient-to-br from-indigo-400 to-purple-500 relative">
                {ad.imageUrl ? (
                  <Image
                    src={ad.imageUrl}
                    alt={ad.title}
                    fill
                    className="object-cover"
                    sizes="400px"
                  />
                ) : (
                  <div className="absolute inset-0 flex items-center justify-center">
                    <svg
                      className="w-16 h-16 text-white/50"
                      fill="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V5h14v14zm-5-7l-3 3.72L9 13l-3 4h12l-4-5z" />
                    </svg>
                  </div>
                )}
              </div>

              {/* Ad Content */}
              <div className="p-6">
                <h3 className="text-xl font-bold text-gray-800 mb-2">{ad.title}</h3>
                {ad.description && <p className="text-gray-600 mb-4">{ad.description}</p>}
                {ad.linkUrl && (
                  <a
                    href={ad.linkUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-block text-indigo-600 font-semibold hover:text-indigo-800 transition-colors"
                  >
                    詳細を見る →
                  </a>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
