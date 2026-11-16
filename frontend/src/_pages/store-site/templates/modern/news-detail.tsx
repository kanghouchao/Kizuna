import { ArrowLeftIcon } from 'lucide-react';
import Image from 'next/image';
import Link from 'next/link';
import type { StoreSitePageProps } from '../../model/types';
import Frame from './Frame';
import { PublishedOn } from './pageParts';

const backLink =
  'm-bg-card m-border inline-flex items-center gap-1.5 rounded-full px-4 py-1.5 text-sm font-bold';

/** 記事ページ（modern テンプレート）。本文は文字のまま（HTML や記法として解釈しない）、改行を保って出す。 */
export default function ModernNewsDetailPage({
  store,
  siteConfig,
  article,
}: StoreSitePageProps<'news-detail'>) {
  return (
    <Frame store={store} siteConfig={siteConfig}>
      <article className="mx-auto max-w-3xl">
        <Link href="/news" className={backLink}>
          <ArrowLeftIcon className="size-4" aria-hidden />
          お知らせ一覧へ
        </Link>
        <header className="m-pop mt-5">
          {article.publish_from && (
            <p className="m-label">
              <PublishedOn value={article.publish_from} />
            </p>
          )}
          <h1 className="m-display mt-1 text-[clamp(1.75rem,6vw,3rem)]">{article.title}</h1>
        </header>
        {/* 表題が見出しにあるので、表紙は読み上げない */}
        {article.cover_image_url && (
          <div className="m-pop m-pop-2 relative mt-6 mr-3 mb-3">
            <span
              aria-hidden
              className="m-bg-yellow absolute inset-0 translate-x-3 translate-y-3 rotate-1 rounded-[var(--m-radius)]"
            />
            <div className="m-border relative aspect-video overflow-hidden rounded-[var(--m-radius)]">
              <Image
                src={article.cover_image_url}
                alt=""
                fill
                preload
                sizes="(min-width: 768px) 768px, 100vw"
                className="object-cover"
              />
            </div>
          </div>
        )}
        <p className="m-bg-card mt-8 rounded-[var(--m-radius)] border-2 border-[var(--m-line)] p-6 text-[15px] leading-8 break-words whitespace-pre-wrap sm:p-8 sm:text-base sm:leading-9">
          {article.body}
        </p>
      </article>
    </Frame>
  );
}
