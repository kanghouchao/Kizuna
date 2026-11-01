import Image from 'next/image';
import Link from 'next/link';
import type { Page } from '@/shared/api';
import { PublicArticleSummary } from '../../model/types';
import PublishedOn from './PublishedOn';
import { StorefrontNotice, StorefrontPager, pageHref } from './StorefrontPaging';

export const ARTICLES_PER_PAGE = 10;

const BASE_PATH = '/news';

interface NewsSectionProps {
  /** 取得に失敗したときは null。 */
  articles: Page<PublicArticleSummary> | null;
  /** 要求したページ（0 起点）。 */
  page: number;
}

/** 掲出中の記事一覧（Server Component）。本文は詳細ページだけで読む。 */
export default function NewsSection({ articles, page }: NewsSectionProps) {
  if (articles === null) {
    return (
      <StorefrontNotice
        alert
        message="お知らせを読み込めませんでした"
        link={{ href: pageHref(BASE_PATH, page), label: '再読み込み' }}
      />
    );
  }
  if (articles.content.length === 0) {
    // 掲出が減ってページ数が縮むと、開いていたページだけが空になる
    return articles.total_elements > 0 ? (
      <StorefrontNotice
        message="このページにお知らせはありません"
        link={{ href: pageHref(BASE_PATH, 0), label: '最初のページへ' }}
      />
    ) : (
      <StorefrontNotice message="掲載中のお知らせはありません" />
    );
  }

  return (
    <section className="py-12 md:py-20 px-5 sm:px-6" style={{ background: 'var(--storefront-bg)' }}>
      <div className="max-w-3xl mx-auto">
        <ul className="space-y-4 md:space-y-6">
          {articles.content.map(article => (
            <li key={article.id}>
              <Link
                href={`${BASE_PATH}/${article.id}`}
                className="block p-5 md:p-7 transition-colors duration-300 hover:bg-[color-mix(in_srgb,var(--storefront-accent)_8%,transparent)]"
                style={{
                  background: 'var(--storefront-surface-3)',
                  boxShadow:
                    '0 0 0 1px color-mix(in srgb, var(--storefront-accent) 10%, transparent)',
                }}
              >
                <article>
                  {/* 表題がリンク名を担うので、表紙は装飾として読み上げない */}
                  {article.cover_image_url && (
                    <div className="relative aspect-video mb-4 md:mb-5 overflow-hidden">
                      <Image
                        src={article.cover_image_url}
                        alt=""
                        fill
                        className="object-cover"
                        sizes="(max-width: 768px) 100vw, 768px"
                      />
                    </div>
                  )}
                  <h2
                    className="break-words text-base md:text-lg tracking-wider text-[var(--storefront-fg)]"
                    style={{ fontFamily: 'var(--storefront-font-display)' }}
                  >
                    {article.title}
                  </h2>
                  {article.publish_from && (
                    <p className="mt-3 text-[11px] md:text-xs tracking-wider text-[color-mix(in_srgb,var(--storefront-accent)_70%,transparent)]">
                      <PublishedOn value={article.publish_from} />
                    </p>
                  )}
                </article>
              </Link>
            </li>
          ))}
        </ul>
        {articles.total_pages > 1 && <StorefrontPager basePath={BASE_PATH} result={articles} />}
      </div>
    </section>
  );
}
