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
  // custom_fields はサーバ側で公開・生存・値ありの定義のみを表示順に整形済みのため、そのまま追加する
  cast.custom_fields?.forEach(field => profile.push({ label: field.label, value: field.value }));

  return (
    <section className="py-12 md:py-20 px-5 sm:px-6" style={{ background: 'var(--storefront-bg)' }}>
      <div className="max-w-4xl mx-auto grid grid-cols-1 md:grid-cols-2 gap-8 md:gap-12">
        {/* 写真 */}
        <div
          className="aspect-3/4 relative overflow-hidden"
          style={{
            background: 'var(--storefront-surface-3)',
            boxShadow: '0 0 0 1px color-mix(in srgb, var(--storefront-accent) 15%, transparent)',
          }}
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
              <svg
                className="w-16 h-16"
                fill="color-mix(in srgb, var(--storefront-accent) 15%, transparent)"
                viewBox="0 0 24 24"
              >
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
                  className="flex items-center justify-between border-b border-[color-mix(in_srgb,var(--storefront-accent)_10%,transparent)] pb-2"
                >
                  <dt className="text-[color-mix(in_srgb,var(--storefront-accent)_60%,transparent)] text-[10px] tracking-[0.3em] uppercase">
                    {label}
                  </dt>
                  <dd
                    className="text-[color-mix(in_srgb,var(--storefront-fg)_80%,transparent)] text-sm tracking-wider"
                    style={{ fontFamily: 'var(--storefront-font-display)' }}
                  >
                    {value}
                  </dd>
                </div>
              ))}
            </dl>
          )}

          {cast.introduction && (
            <p
              className="text-[color-mix(in_srgb,var(--storefront-fg)_50%,transparent)] text-sm leading-loose whitespace-pre-line"
              style={{ fontFamily: 'var(--storefront-font-display)' }}
            >
              {cast.introduction}
            </p>
          )}
        </div>
      </div>

      <div className="text-center mt-12 md:mt-16">
        <Link
          href="/casts"
          className="inline-block border border-[color-mix(in_srgb,var(--storefront-accent)_40%,transparent)] text-[var(--storefront-accent)] text-[9px] tracking-[0.4em] uppercase px-8 md:px-10 py-3 hover:bg-[var(--storefront-accent)] hover:text-[var(--storefront-bg)] transition-all duration-300"
          style={{ fontFamily: 'var(--storefront-font-display)' }}
        >
          ← キャスト一覧へ
        </Link>
      </div>
    </section>
  );
}
