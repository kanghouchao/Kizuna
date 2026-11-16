import { ChevronLeftIcon } from 'lucide-react';
import Image from 'next/image';
import Link from 'next/link';
import type { StoreSitePageProps } from '../../model/types';
import { publishedDate } from './format';
import Frame from './Frame';

/**
 * 記事ページ（classic テンプレート）。表題は長さが決まらないので縦書きにせず、横書きで折り返す。
 * 本文は文字として出し（HTML や記法として解釈しない）、書いたとおりの改行を保つ。
 */
export default function ClassicNewsDetailPage({
  store,
  siteConfig,
  article,
}: StoreSitePageProps<'news-detail'>) {
  return (
    <Frame store={store} siteConfig={siteConfig} current="/news">
      <nav className="c-crumb" aria-label="現在地">
        <Link href="/">TOP</Link>
        <span aria-hidden="true">／</span>
        <Link href="/news">お知らせ</Link>
        <span aria-hidden="true">／</span>
        <span aria-current="page">{article.title}</span>
      </nav>

      <article className="c-article">
        <header className="c-article-head">
          <p className="c-article-kind">お知らせ</p>
          <h1 className="c-display">{article.title}</h1>
          {article.publish_from && (
            <time className="c-news-date" dateTime={article.publish_from}>
              {publishedDate(article.publish_from)}
            </time>
          )}
        </header>

        {/* 表題は見出しにあるので、表紙は読み上げない */}
        {article.cover_image_url && (
          <div className="c-frame c-article-cover">
            <div className="c-frame-in">
              <Image
                src={article.cover_image_url}
                alt=""
                fill
                preload
                sizes="(min-width: 1024px) 720px, 100vw"
                className="c-photo"
              />
            </div>
          </div>
        )}

        <p className="c-article-body">{article.body}</p>
      </article>

      <div className="c-book-foot">
        <Link href="/news" className="c-textlink">
          <ChevronLeftIcon size={14} strokeWidth={1.25} aria-hidden="true" />
          お知らせ一覧へ
        </Link>
      </div>
    </Frame>
  );
}
