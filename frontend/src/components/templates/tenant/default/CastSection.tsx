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
      left: direction === 'left' ? -280 : 280,
      behavior: 'smooth',
    });
  };

  return (
    <section id="cast" className="py-20 px-6" style={{ background: '#080808' }}>
      <div className="max-w-7xl mx-auto">
        {/* セクションヘッダー */}
        <div className="text-center mb-14">
          <p className="text-[#C9A84C]/40 text-[9px] tracking-[0.6em] uppercase mb-3">Our Cast</p>
          <h2
            className="text-2xl font-light tracking-[0.2em] text-[#F8F4F0]/85"
            style={{ fontFamily: "'Noto Serif JP', 'Hiragino Mincho Pro', serif" }}
          >
            キャスト紹介
          </h2>
          <div className="flex items-center justify-center gap-3 mt-5 mb-4">
            <div className="h-px w-12 bg-gradient-to-r from-transparent to-[#C9A84C]/35" />
            <span className="text-[#C9A84C]/40 text-[10px]">◆</span>
            <div className="h-px w-12 bg-gradient-to-l from-transparent to-[#C9A84C]/35" />
          </div>
          <p
            className="text-[#F8F4F0]/30 text-xs tracking-wider"
            style={{ fontFamily: "'Noto Serif JP', serif" }}
          >
            当店自慢のキャストをご紹介します
          </p>
        </div>

        {/* カルーセルコンテナ */}
        <div className="relative group">
          {/* 左矢印 */}
          <button
            onClick={() => scroll('left')}
            className="absolute left-0 top-1/2 -translate-y-1/2 z-10 w-9 h-9 border border-[#C9A84C]/30 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all duration-300 -ml-4 hover:bg-[#C9A84C]/10"
            style={{ background: 'rgba(8,8,8,0.9)' }}
            aria-label="前へ"
          >
            <ChevronLeftIcon className="h-4 w-4 text-[#C9A84C]" />
          </button>

          {/* スクロールエリア */}
          <div
            ref={scrollRef}
            className="flex gap-5 overflow-x-auto scroll-smooth snap-x snap-mandatory pb-2"
            style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
          >
            {casts.map(cast => (
              <div key={cast.id} className="shrink-0 w-48 snap-start group/card cursor-pointer">
                {/* 写真エリア */}
                <div
                  className="aspect-[3/4] relative overflow-hidden mb-3"
                  style={{
                    background: '#0F0F0F',
                    boxShadow: '0 0 0 1px rgba(201,168,76,0.1)',
                  }}
                >
                  {cast.photo_url ? (
                    <Image
                      src={cast.photo_url}
                      alt={cast.name}
                      fill
                      className="object-cover transition-transform duration-700 group-hover/card:scale-105"
                      sizes="192px"
                    />
                  ) : (
                    <div className="absolute inset-0 flex items-center justify-center">
                      <svg className="w-14 h-14" fill="rgba(201,168,76,0.15)" viewBox="0 0 24 24">
                        <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
                      </svg>
                    </div>
                  )}
                  {/* ホバーオーバーレイ */}
                  <div
                    className="absolute inset-0 opacity-0 group-hover/card:opacity-100 transition-opacity duration-300"
                    style={{
                      background: 'linear-gradient(to top, rgba(8,8,8,0.8) 0%, transparent 50%)',
                    }}
                  />
                  {/* ゴールドボーダー（ホバー） */}
                  <div
                    className="absolute inset-0 opacity-0 group-hover/card:opacity-100 transition-opacity duration-300"
                    style={{ boxShadow: 'inset 0 0 0 1px rgba(201,168,76,0.4)' }}
                  />
                </div>

                {/* キャスト情報 */}
                <div className="text-center px-1">
                  <h3
                    className="text-[#F8F4F0]/85 text-sm font-light tracking-wider mb-1"
                    style={{ fontFamily: "'Noto Serif JP', serif" }}
                  >
                    {cast.name}
                  </h3>
                  <div className="text-[10px] text-[#C9A84C]/50 space-x-2">
                    {cast.age && <span>{cast.age}歳</span>}
                    {cast.height && <span>T{cast.height}</span>}
                  </div>
                  {cast.bust && cast.waist && cast.hip && (
                    <p className="text-[9px] text-[#F8F4F0]/20 mt-0.5">
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
            className="absolute right-0 top-1/2 -translate-y-1/2 z-10 w-9 h-9 border border-[#C9A84C]/30 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all duration-300 -mr-4 hover:bg-[#C9A84C]/10"
            style={{ background: 'rgba(8,8,8,0.9)' }}
            aria-label="次へ"
          >
            <ChevronRightIcon className="h-4 w-4 text-[#C9A84C]" />
          </button>
        </div>

        {/* もっと見るボタン */}
        <div className="text-center mt-14">
          <Link
            href="/login"
            className="inline-block border border-[#C9A84C]/40 text-[#C9A84C] text-[9px] tracking-[0.4em] uppercase px-10 py-3 hover:bg-[#C9A84C] hover:text-[#080808] transition-all duration-300"
            style={{ fontFamily: "'Noto Serif JP', serif" }}
          >
            全てのキャストを見る
          </Link>
        </div>
      </div>
    </section>
  );
}
