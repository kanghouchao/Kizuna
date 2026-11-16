import { ArrowRightIcon } from 'lucide-react';
import Image from 'next/image';
import Link from 'next/link';
import { pageHref } from '../../lib/paging';
import type { StoreSitePageProps } from '../../model/types';
import { toneAt } from './castParts';
import Frame from './Frame';
import { Notice, PageHead, Pager, PublishedOn } from './pageParts';

const BASE_PATH = '/news';

/** お知らせ一覧ページ（modern テンプレート）。表紙・公開日・表題を横に並べた行のカード。本文は記事ページだけで読む。 */
export default function ModernNewsPage({
  store,
  siteConfig,
  articles,
  page,
}: StoreSitePageProps<'news'>) {
  return (
    <Frame store={store} siteConfig={siteConfig}>
      <PageHead en="NEWS" title="お知らせ" />
      {articles === null ? (
        <Notice
          alert
          title="お知らせを読み込めませんでした"
          link={{ href: pageHref(BASE_PATH, page), label: '再読み込み' }}
        />
      ) : articles.content.length === 0 ? (
        // 掲出が減ってページ数が縮むと、開いていたページだけが空になる
        articles.total_elements > 0 ? (
          <Notice
            title="このページにお知らせはありません"
            link={{ href: pageHref(BASE_PATH, 0), label: '最初のページへ' }}
          />
        ) : (
          <Notice title="掲載中のお知らせはありません" />
        )
      ) : (
        <>
          <ul className="grid gap-4 lg:grid-cols-2 lg:gap-5">
            {articles.content.map((article, i) => (
              <li key={article.id} className="min-w-0">
                <Link
                  href={`${BASE_PATH}/${article.id}`}
                  className="m-bg-card m-sticker m-lift block h-full overflow-hidden rounded-[24px]"
                >
                  <article className="flex h-full">
                    {/* 表題がリンクの名前を担うので、表紙は読み上げない。表紙が無ければ色の帯だけを置く */}
                    {article.cover_image_url ? (
                      <span className="relative block min-h-28 w-1/3 max-w-52 shrink-0 border-r-2 border-[var(--m-ink)]">
                        <Image
                          src={article.cover_image_url}
                          alt=""
                          fill
                          sizes="208px"
                          className="object-cover"
                        />
                      </span>
                    ) : (
                      <span
                        aria-hidden
                        className={`${toneAt(i)} block w-4 shrink-0 border-r-2 border-[var(--m-ink)]`}
                      />
                    )}
                    <div className="flex min-w-0 grow flex-col justify-center p-4 sm:p-5">
                      {article.publish_from && (
                        <p className="m-label whitespace-nowrap">
                          <PublishedOn value={article.publish_from} />
                        </p>
                      )}
                      <h2 className="m-display mt-1 text-lg sm:text-xl">{article.title}</h2>
                    </div>
                    <span aria-hidden className="hidden shrink-0 place-items-center pr-4 sm:grid">
                      <span className="m-bg-yellow m-border grid size-8 place-items-center rounded-full">
                        <ArrowRightIcon className="size-4" />
                      </span>
                    </span>
                  </article>
                </Link>
              </li>
            ))}
          </ul>
          {articles.total_pages > 1 && <Pager basePath={BASE_PATH} result={articles} />}
        </>
      )}
    </Frame>
  );
}
