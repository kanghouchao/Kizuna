import Image from 'next/image';

interface MVSectionProps {
  mvUrl?: string;
  mvType?: 'image' | 'video';
}

export default function MVSection({ mvUrl, mvType = 'image' }: MVSectionProps) {
  const hasMedia = !!mvUrl;

  return (
    <section className="py-12 md:py-20 px-5 sm:px-6" style={{ background: '#0A0A0A' }}>
      <div className="max-w-5xl mx-auto">
        {/* セクションヘッダー */}
        <div className="text-center mb-8 md:mb-12">
          <p className="text-[#C9A84C]/40 text-[9px] tracking-[0.6em] uppercase mb-3">
            Main Visual
          </p>
          <h2
            className="text-xl md:text-2xl font-light tracking-[0.2em] text-[#F8F4F0]/80"
            style={{ fontFamily: "'Noto Serif JP', 'Hiragino Mincho Pro', serif" }}
          >
            メインビジュアル
          </h2>
          <div className="flex items-center justify-center gap-3 mt-4 md:mt-5">
            <div className="h-px w-10 md:w-12 bg-linear-to-r from-transparent to-[#C9A84C]/35" />
            <span className="text-[#C9A84C]/40 text-[10px]">◆</span>
            <div className="h-px w-10 md:w-12 bg-linear-to-l from-transparent to-[#C9A84C]/35" />
          </div>
        </div>

        {/* ビジュアルコンテナ（ゴールドフレーム） */}
        <div
          className="relative"
          style={{
            padding: '1px',
            background:
              'linear-gradient(135deg, rgba(201,168,76,0.25) 0%, rgba(201,168,76,0.05) 50%, rgba(201,168,76,0.25) 100%)',
          }}
        >
          <div className="relative" style={{ background: '#0F0F0F' }}>
            {hasMedia ? (
              mvType === 'video' ? (
                <div className="aspect-video">
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
                <div className="aspect-video relative">
                  <Image
                    src={mvUrl!}
                    alt="メインビジュアル"
                    fill
                    className="object-cover"
                    sizes="100vw"
                  />
                </div>
              )
            ) : (
              <div
                className="aspect-video flex items-center justify-center"
                style={{ background: '#0D0D0D' }}
              >
                <div className="text-center">
                  <div className="flex items-center justify-center gap-3 mb-4 md:mb-6">
                    <div className="h-px w-8 md:w-10 bg-linear-to-r from-transparent to-[#C9A84C]/25" />
                    <span className="text-[#C9A84C]/30 text-xs">◆</span>
                    <div className="h-px w-8 md:w-10 bg-linear-to-l from-transparent to-[#C9A84C]/25" />
                  </div>
                  <svg
                    className="w-10 h-10 md:w-12 md:h-12 mx-auto mb-3 md:mb-4"
                    fill="none"
                    stroke="rgba(201,168,76,0.2)"
                    viewBox="0 0 24 24"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={1}
                      d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z"
                    />
                  </svg>
                  <p
                    className="text-[#C9A84C]/25 text-[10px] md:text-xs tracking-widest"
                    style={{ fontFamily: "'Noto Serif JP', serif" }}
                  >
                    メインビジュアル
                  </p>
                </div>
              </div>
            )}
          </div>

          {/* コーナーオーナメント */}
          <div className="absolute top-2 left-2 md:top-3 md:left-3 w-5 h-5 md:w-6 md:h-6 border-t border-l border-[#C9A84C]/50" />
          <div className="absolute top-2 right-2 md:top-3 md:right-3 w-5 h-5 md:w-6 md:h-6 border-t border-r border-[#C9A84C]/50" />
          <div className="absolute bottom-2 left-2 md:bottom-3 md:left-3 w-5 h-5 md:w-6 md:h-6 border-b border-l border-[#C9A84C]/50" />
          <div className="absolute bottom-2 right-2 md:bottom-3 md:right-3 w-5 h-5 md:w-6 md:h-6 border-b border-r border-[#C9A84C]/50" />
        </div>
      </div>
    </section>
  );
}
