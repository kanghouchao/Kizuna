import Image from 'next/image';

interface MVSectionProps {
  mvUrl?: string;
  mvType?: 'image' | 'video';
}

export default function MVSection({ mvUrl, mvType = 'image' }: MVSectionProps) {
  // Placeholder content when no MV is configured
  const placeholderContent = (
    <div className="aspect-video bg-gradient-to-br from-gray-100 to-gray-200 rounded-lg flex items-center justify-center">
      <div className="text-center text-gray-400">
        <svg
          className="w-16 h-16 mx-auto mb-4"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={1.5}
            d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z"
          />
        </svg>
        <p>メインビジュアル</p>
      </div>
    </div>
  );

  return (
    <section className="py-16 bg-white">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <h2 className="text-3xl font-bold text-center mb-8 text-gray-800">Main Visual</h2>

        {mvUrl ? (
          mvType === 'video' ? (
            <div className="aspect-video rounded-lg overflow-hidden shadow-xl">
              <video
                src={mvUrl}
                controls
                className="w-full h-full object-cover"
                title="メインビジュアル動画"
                aria-label="メインビジュアル動画"
              >
                お使いのブラウザは動画再生に対応していません。
              </video>
            </div>
          ) : (
            <div className="aspect-video rounded-lg overflow-hidden shadow-xl relative">
              <Image src={mvUrl} alt="Main Visual" fill className="object-cover" sizes="100vw" />
            </div>
          )
        ) : (
          placeholderContent
        )}
      </div>
    </section>
  );
}
