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

  if (!casts || casts.length === 0) {
    return null;
  }

  const scroll = (direction: 'left' | 'right') => {
    if (!scrollRef.current) return;
    const scrollAmount = 280;
    scrollRef.current.scrollBy({
      left: direction === 'left' ? -scrollAmount : scrollAmount,
      behavior: 'smooth',
    });
  };

  return (
    <section id="cast" className="py-16 bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <h2 className="text-3xl font-bold text-center mb-4 text-gray-800">キャスト紹介</h2>
        <p className="text-center text-gray-600 mb-12">当店自慢のキャストをご紹介します</p>

        {/* カルーセルコンテナ */}
        <div className="relative group">
          {/* 左矢印ボタン */}
          <button
            onClick={() => scroll('left')}
            className="absolute left-0 top-1/2 -translate-y-1/2 z-10 bg-white/80 hover:bg-white shadow-lg rounded-full p-2 opacity-0 group-hover:opacity-100 transition-opacity -ml-4"
            aria-label="前へ"
          >
            <ChevronLeftIcon className="h-6 w-6 text-gray-700" />
          </button>

          {/* スクロールエリア */}
          <div
            ref={scrollRef}
            className="flex gap-6 overflow-x-auto scroll-smooth snap-x snap-mandatory scrollbar-hide pb-4"
            style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
          >
            {casts.map(cast => (
              <div
                key={cast.id}
                className="shrink-0 w-48 snap-start bg-white rounded-lg shadow-md overflow-hidden hover:shadow-lg transition-shadow"
              >
                {/* キャスト画像 */}
                <div className="aspect-3/4 bg-linear-to-br from-pink-100 to-purple-100 relative">
                  {cast.photo_url ? (
                    <Image
                      src={`/api${cast.photo_url}`}
                      alt={cast.name}
                      fill
                      className="object-cover"
                      sizes="192px"
                    />
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
                </div>

                {/* キャスト情報 */}
                <div className="p-3 text-center">
                  <h3 className="font-semibold text-gray-800">{cast.name}</h3>
                  {cast.age && <p className="text-sm text-gray-500 mt-1">{cast.age}歳</p>}
                  {cast.height && <p className="text-xs text-gray-400">T{cast.height}</p>}
                  {cast.bust && cast.waist && cast.hip && (
                    <p className="text-xs text-gray-400">
                      B{cast.bust} W{cast.waist} H{cast.hip}
                    </p>
                  )}
                </div>
              </div>
            ))}
          </div>

          {/* 右矢印ボタン */}
          <button
            onClick={() => scroll('right')}
            className="absolute right-0 top-1/2 -translate-y-1/2 z-10 bg-white/80 hover:bg-white shadow-lg rounded-full p-2 opacity-0 group-hover:opacity-100 transition-opacity -mr-4"
            aria-label="次へ"
          >
            <ChevronRightIcon className="h-6 w-6 text-gray-700" />
          </button>
        </div>

        {/* もっと見るボタン */}
        <div className="text-center mt-10">
          <Link
            href="/login"
            className="inline-block bg-indigo-600 text-white px-8 py-3 rounded-full font-semibold hover:bg-indigo-700 transition-colors"
          >
            全てのキャストを見る
          </Link>
        </div>
      </div>
    </section>
  );
}
