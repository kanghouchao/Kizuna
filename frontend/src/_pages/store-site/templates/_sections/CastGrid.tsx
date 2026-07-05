import Image from 'next/image';
import Link from 'next/link';
import { Cast } from '../../model/types';

interface CastGridProps {
  casts: Cast[];
}

/**
 * キャスト一覧グリッド（Server Component）。各カードは詳細ページへのリンク。
 * 既存 CastSection のカード意匠を踏襲しつつ、レスポンシブグリッドで並べる。
 */
export default function CastGrid({ casts }: CastGridProps) {
  return (
    <section className="py-12 md:py-20 px-5 sm:px-6" style={{ background: '#080808' }}>
      <div className="max-w-7xl mx-auto">
        {casts.length === 0 ? (
          <p
            className="text-center text-[#F8F4F0]/30 text-sm tracking-wider py-16"
            style={{ fontFamily: "'Noto Serif JP', serif" }}
          >
            キャスト情報は準備中です
          </p>
        ) : (
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4 md:gap-6">
            {casts.map(cast => (
              <Link key={cast.id} href={`/casts/${cast.id}`} className="group/card block">
                <div
                  className="aspect-3/4 relative overflow-hidden mb-2 md:mb-3"
                  style={{ background: '#0F0F0F', boxShadow: '0 0 0 1px rgba(201,168,76,0.1)' }}
                >
                  {cast.photo_url ? (
                    <Image
                      src={cast.photo_url}
                      alt={cast.name}
                      fill
                      className="object-cover transition-transform duration-700 group-hover/card:scale-105"
                      sizes="(max-width: 768px) 50vw, (max-width: 1024px) 33vw, 25vw"
                    />
                  ) : (
                    <div className="absolute inset-0 flex items-center justify-center">
                      <svg
                        className="w-10 h-10 md:w-14 md:h-14"
                        fill="rgba(201,168,76,0.15)"
                        viewBox="0 0 24 24"
                      >
                        <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
                      </svg>
                    </div>
                  )}
                  <div
                    className="absolute inset-0 opacity-0 group-hover/card:opacity-100 transition-opacity duration-300"
                    style={{ boxShadow: 'inset 0 0 0 1px rgba(201,168,76,0.4)' }}
                  />
                </div>
                <div className="text-center px-1">
                  <h3
                    className="text-[#F8F4F0]/85 text-xs md:text-sm font-light tracking-wider mb-1"
                    style={{ fontFamily: "'Noto Serif JP', serif" }}
                  >
                    {cast.name}
                  </h3>
                  {cast.age && (
                    <p className="text-[9px] md:text-[10px] text-[#C9A84C]/50">{cast.age}歳</p>
                  )}
                </div>
              </Link>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}
