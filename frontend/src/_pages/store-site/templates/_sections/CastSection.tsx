'use client';

import { useRef } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import { ChevronLeftIcon, ChevronRightIcon } from '@heroicons/react/24/outline';

interface Cast {
  id: string;
  name: string;
  photo_url?: string;
  age?: number;
  height?: number;
  bust?: number;
  waist?: number;
  hip?: number;
}

interface CastSectionProps {
  casts?: Cast[];
}

export default function CastSection({ casts }: CastSectionProps) {
  const scrollRef = useRef<HTMLDivElement>(null);

  if (!casts || casts.length === 0) return null;

  const scroll = (direction: 'left' | 'right') => {
    if (!scrollRef.current) return;
    scrollRef.current.scrollBy({
      left: direction === 'left' ? -240 : 240,
      behavior: 'smooth',
    });
  };

  return (
    <section
      id="cast"
      className="py-12 md:py-20 px-5 sm:px-6"
      style={{ background: 'var(--storefront-bg)' }}
    >
      <div className="max-w-7xl mx-auto">
        {/* セクションヘッダー */}
        <div className="text-center mb-10 md:mb-14">
          <p className="text-[color-mix(in_srgb,var(--storefront-accent)_40%,transparent)] text-[9px] tracking-[0.6em] uppercase mb-3">
            Our Cast
          </p>
          <h2
            className="text-xl md:text-2xl font-light tracking-[0.2em] text-[color-mix(in_srgb,var(--storefront-fg)_85%,transparent)]"
            style={{ fontFamily: 'var(--storefront-font-display)' }}
          >
            キャスト紹介
          </h2>
          <div className="flex items-center justify-center gap-3 mt-4 md:mt-5 mb-3">
            <div className="h-px w-10 md:w-12 bg-linear-to-r from-transparent to-[color-mix(in_srgb,var(--storefront-accent)_35%,transparent)]" />
            <span className="text-[color-mix(in_srgb,var(--storefront-accent)_40%,transparent)] text-[10px]">
              ◆
            </span>
            <div className="h-px w-10 md:w-12 bg-linear-to-l from-transparent to-[color-mix(in_srgb,var(--storefront-accent)_35%,transparent)]" />
          </div>
          <p
            className="text-[color-mix(in_srgb,var(--storefront-fg)_30%,transparent)] text-[11px] md:text-xs tracking-wider"
            style={{ fontFamily: 'var(--storefront-font-display)' }}
          >
            当店自慢のキャストをご紹介します
          </p>
        </div>

        {/* カルーセルコンテナ */}
        <div className="relative group">
          {/* 左矢印（md以上でホバー時表示） */}
          <button
            onClick={() => scroll('left')}
            className="absolute left-0 top-1/2 -translate-y-1/2 z-10 w-8 h-8 md:w-9 md:h-9 border border-[color-mix(in_srgb,var(--storefront-accent)_30%,transparent)] flex items-center justify-center md:opacity-0 md:group-hover:opacity-100 transition-all duration-300 -ml-3 md:-ml-4 hover:bg-[color-mix(in_srgb,var(--storefront-accent)_10%,transparent)]"
            style={{ background: 'color-mix(in srgb, var(--storefront-bg) 90%, transparent)' }}
            aria-label="前へ"
          >
            <ChevronLeftIcon className="h-3.5 w-3.5 md:h-4 md:w-4 text-[var(--storefront-accent)]" />
          </button>

          {/* スクロールエリア */}
          <div
            ref={scrollRef}
            className="flex gap-4 md:gap-5 overflow-x-auto scroll-smooth snap-x snap-mandatory pb-2 px-1"
            style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
          >
            {casts.map(cast => (
              <div
                key={cast.id}
                className="shrink-0 w-36 sm:w-40 md:w-48 snap-start group/card cursor-pointer"
              >
                {/* 写真エリア */}
                <div
                  className="aspect-3/4 relative overflow-hidden mb-2 md:mb-3"
                  style={{
                    background: 'var(--storefront-surface-3)',
                    boxShadow:
                      '0 0 0 1px color-mix(in srgb, var(--storefront-accent) 10%, transparent)',
                  }}
                >
                  {cast.photo_url ? (
                    <Image
                      src={cast.photo_url}
                      alt={cast.name}
                      fill
                      className="object-cover transition-transform duration-700 group-hover/card:scale-105"
                      sizes="(max-width: 640px) 144px, (max-width: 768px) 160px, 192px"
                    />
                  ) : (
                    <div className="absolute inset-0 flex items-center justify-center">
                      <svg
                        className="w-10 h-10 md:w-14 md:h-14"
                        fill="color-mix(in srgb, var(--storefront-accent) 15%, transparent)"
                        viewBox="0 0 24 24"
                      >
                        <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
                      </svg>
                    </div>
                  )}
                  {/* ホバーオーバーレイ */}
                  <div
                    className="absolute inset-0 opacity-0 group-hover/card:opacity-100 transition-opacity duration-300"
                    style={{
                      background:
                        'linear-gradient(to top, color-mix(in srgb, var(--storefront-bg) 80%, transparent) 0%, transparent 50%)',
                    }}
                  />
                  {/* ゴールドボーダー（ホバー） */}
                  <div
                    className="absolute inset-0 opacity-0 group-hover/card:opacity-100 transition-opacity duration-300"
                    style={{
                      boxShadow:
                        'inset 0 0 0 1px color-mix(in srgb, var(--storefront-accent) 40%, transparent)',
                    }}
                  />
                </div>

                {/* キャスト情報 */}
                <div className="text-center px-1">
                  <h3
                    className="text-[color-mix(in_srgb,var(--storefront-fg)_85%,transparent)] text-xs md:text-sm font-light tracking-wider mb-1"
                    style={{ fontFamily: 'var(--storefront-font-display)' }}
                  >
                    {cast.name}
                  </h3>
                  <div className="text-[9px] md:text-[10px] text-[color-mix(in_srgb,var(--storefront-accent)_50%,transparent)] space-x-1.5">
                    {cast.age && <span>{cast.age}歳</span>}
                    {cast.height && <span>T{cast.height}</span>}
                  </div>
                  {cast.bust && cast.waist && cast.hip && (
                    <p className="text-[8px] md:text-[9px] text-[color-mix(in_srgb,var(--storefront-fg)_20%,transparent)] mt-0.5">
                      B{cast.bust} · W{cast.waist} · H{cast.hip}
                    </p>
                  )}
                </div>
              </div>
            ))}
          </div>

          {/* 右矢印 */}
          <button
            onClick={() => scroll('right')}
            className="absolute right-0 top-1/2 -translate-y-1/2 z-10 w-8 h-8 md:w-9 md:h-9 border border-[color-mix(in_srgb,var(--storefront-accent)_30%,transparent)] flex items-center justify-center md:opacity-0 md:group-hover:opacity-100 transition-all duration-300 -mr-3 md:-mr-4 hover:bg-[color-mix(in_srgb,var(--storefront-accent)_10%,transparent)]"
            style={{ background: 'color-mix(in srgb, var(--storefront-bg) 90%, transparent)' }}
            aria-label="次へ"
          >
            <ChevronRightIcon className="h-3.5 w-3.5 md:h-4 md:w-4 text-[var(--storefront-accent)]" />
          </button>
        </div>

        {/* もっと見るボタン */}
        <div className="text-center mt-10 md:mt-14">
          <Link
            href="/platform/login"
            className="inline-block border border-[color-mix(in_srgb,var(--storefront-accent)_40%,transparent)] text-[var(--storefront-accent)] text-[9px] tracking-[0.4em] uppercase px-8 md:px-10 py-3 hover:bg-[var(--storefront-accent)] hover:text-[var(--storefront-bg)] transition-all duration-300"
            style={{ fontFamily: 'var(--storefront-font-display)' }}
          >
            全てのキャストを見る
          </Link>
        </div>
      </div>
    </section>
  );
}
