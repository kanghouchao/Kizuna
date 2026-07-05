import Image from 'next/image';
import Link from 'next/link';
import { Cast } from '../../model/types';

interface CastDetailSectionProps {
  cast: Cast;
}

/**
 * キャスト詳細（Server Component）。写真 + プロフィール + 自己紹介文。
 * null のプロフィール項目は行ごと非表示にする。
 */
export default function CastDetailSection({ cast }: CastDetailSectionProps) {
  const profile: { label: string; value: string }[] = [];
  if (cast.age) profile.push({ label: '年齢', value: `${cast.age}歳` });
  if (cast.height) profile.push({ label: '身長', value: `${cast.height}cm` });
  if (cast.bust) profile.push({ label: 'バスト', value: `${cast.bust}` });
  if (cast.waist) profile.push({ label: 'ウエスト', value: `${cast.waist}` });
  if (cast.hip) profile.push({ label: 'ヒップ', value: `${cast.hip}` });

  return (
    <section className="py-12 md:py-20 px-5 sm:px-6" style={{ background: '#080808' }}>
      <div className="max-w-4xl mx-auto grid grid-cols-1 md:grid-cols-2 gap-8 md:gap-12">
        {/* 写真 */}
        <div
          className="aspect-3/4 relative overflow-hidden"
          style={{ background: '#0F0F0F', boxShadow: '0 0 0 1px rgba(201,168,76,0.15)' }}
        >
          {cast.photo_url ? (
            <Image
              src={cast.photo_url}
              alt={cast.name}
              fill
              className="object-cover"
              sizes="(max-width: 768px) 100vw, 400px"
            />
          ) : (
            <div className="absolute inset-0 flex items-center justify-center">
              <svg className="w-16 h-16" fill="rgba(201,168,76,0.15)" viewBox="0 0 24 24">
                <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
              </svg>
            </div>
          )}
        </div>

        {/* プロフィール + 自己紹介 */}
        <div>
          {profile.length > 0 && (
            <dl className="space-y-3 mb-8">
              {profile.map(({ label, value }) => (
                <div
                  key={label}
                  className="flex items-center justify-between border-b border-[#C9A84C]/10 pb-2"
                >
                  <dt className="text-[#C9A84C]/60 text-[10px] tracking-[0.3em] uppercase">
                    {label}
                  </dt>
                  <dd
                    className="text-[#F8F4F0]/80 text-sm tracking-wider"
                    style={{ fontFamily: "'Noto Serif JP', serif" }}
                  >
                    {value}
                  </dd>
                </div>
              ))}
            </dl>
          )}

          {cast.introduction && (
            <p
              className="text-[#F8F4F0]/50 text-sm leading-loose whitespace-pre-line"
              style={{ fontFamily: "'Noto Serif JP', serif" }}
            >
              {cast.introduction}
            </p>
          )}
        </div>
      </div>

      <div className="text-center mt-12 md:mt-16">
        <Link
          href="/casts"
          className="inline-block border border-[#C9A84C]/40 text-[#C9A84C] text-[9px] tracking-[0.4em] uppercase px-8 md:px-10 py-3 hover:bg-[#C9A84C] hover:text-[#080808] transition-all duration-300"
          style={{ fontFamily: "'Noto Serif JP', serif" }}
        >
          ← キャスト一覧へ
        </Link>
      </div>
    </section>
  );
}
