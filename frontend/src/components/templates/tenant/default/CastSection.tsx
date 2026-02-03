interface Cast {
  id: string;
  name: string;
  imageUrl?: string;
  status?: string;
}

interface CastSectionProps {
  casts?: Cast[];
}

// Placeholder cast data for demo
const placeholderCasts: Cast[] = [
  { id: '1', name: 'あいり', status: '出勤中' },
  { id: '2', name: 'みく', status: '出勤中' },
  { id: '3', name: 'さくら', status: '本日出勤' },
  { id: '4', name: 'ゆい', status: '本日出勤' },
  { id: '5', name: 'りな', status: '本日出勤' },
  { id: '6', name: 'まい', status: '本日出勤' },
];

export default function CastSection({ casts }: CastSectionProps) {
  const displayCasts = casts && casts.length > 0 ? casts : placeholderCasts;

  return (
    <section id="cast" className="py-16 bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <h2 className="text-3xl font-bold text-center mb-4 text-gray-800">キャスト紹介</h2>
        <p className="text-center text-gray-600 mb-12">当店自慢のキャストをご紹介します</p>

        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-6">
          {displayCasts.map(cast => (
            <div
              key={cast.id}
              className="bg-white rounded-lg shadow-md overflow-hidden hover:shadow-lg transition-shadow"
            >
              {/* Cast Image */}
              <div className="aspect-[3/4] bg-gradient-to-br from-pink-100 to-purple-100 relative">
                {cast.imageUrl ? (
                  <img src={cast.imageUrl} alt={cast.name} className="w-full h-full object-cover" />
                ) : (
                  <div className="absolute inset-0 flex items-center justify-center">
                    <svg
                      className="w-16 h-16 text-gray-300"
                      fill="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
                    </svg>
                  </div>
                )}

                {/* Status Badge */}
                {cast.status && (
                  <div className="absolute top-2 right-2 bg-pink-500 text-white text-xs px-2 py-1 rounded-full">
                    {cast.status}
                  </div>
                )}
              </div>

              {/* Cast Name */}
              <div className="p-3 text-center">
                <h3 className="font-semibold text-gray-800">{cast.name}</h3>
              </div>
            </div>
          ))}
        </div>

        {/* View More Button */}
        <div className="text-center mt-10">
          <a
            href="/login"
            className="inline-block bg-indigo-600 text-white px-8 py-3 rounded-full font-semibold hover:bg-indigo-700 transition-colors"
          >
            全てのキャストを見る
          </a>
        </div>
      </div>
    </section>
  );
}
