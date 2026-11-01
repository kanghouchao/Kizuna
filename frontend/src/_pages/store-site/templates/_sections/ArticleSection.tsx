import Link from 'next/link';
import { PublicArticle } from '../../model/types';
import PublishedOn from './PublishedOn';

/**
 * 記事の本文（Server Component）。
 * 本文はテキストとして出力し（HTML や記法として解釈しない）、書いたとおりの改行を保って表示する。
 */
export default function ArticleSection({ article }: { article: PublicArticle }) {
  return (
    <section className="py-12 md:py-20 px-5 sm:px-6" style={{ background: 'var(--storefront-bg)' }}>
      <article className="max-w-3xl mx-auto">
        {article.publish_from && (
          <p className="mb-6 text-xs md:text-sm tracking-wider text-[color-mix(in_srgb,var(--storefront-accent)_70%,transparent)]">
            <PublishedOn value={article.publish_from} />
          </p>
        )}
        <p className="whitespace-pre-wrap break-words text-sm md:text-base leading-loose text-[color-mix(in_srgb,var(--storefront-fg)_85%,transparent)]">
          {article.body}
        </p>
        <div className="mt-12 text-center">
          <Link
            href="/news"
            className="text-xs tracking-[0.2em] text-[var(--storefront-accent)] px-5 py-2 border border-[color-mix(in_srgb,var(--storefront-accent)_40%,transparent)] hover:bg-[var(--storefront-accent)] hover:text-[var(--storefront-bg)] transition-colors duration-300"
          >
            お知らせ一覧へ
          </Link>
        </div>
      </article>
    </section>
  );
}
