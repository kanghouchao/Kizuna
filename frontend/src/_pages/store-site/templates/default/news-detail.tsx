import Image from 'next/image';
import Link from 'next/link';
import type { StoreSitePageProps } from '../../model/types';
import { publishedOn } from './format';
import Frame from './Frame';

/** 記事ページ（default テンプレート）。本文は文字として出し、書いたとおりの改行を保つ。 */
export default function DefaultNewsDetailPage({
  store,
  siteConfig,
  article,
}: StoreSitePageProps<'news-detail'>) {
  return (
    <Frame store={store} siteConfig={siteConfig}>
      <article className="sfd-article">
        <header className="sfd-article-head">
          <Link href="/news" className="sfd-textlink sfd-back">
            ← お知らせ一覧
          </Link>
          <p className="sfd-label">News</p>
          <h1 className="sfd-article-title">{article.title}</h1>
          {article.publish_from && (
            <time dateTime={article.publish_from} className="sfd-date">
              {publishedOn(article.publish_from)}
            </time>
          )}
        </header>

        {/* 表題が見出しにあるので、表紙は飾りとして読み上げない */}
        {article.cover_image_url && (
          <div className="sfd-article-cover">
            <Image
              src={article.cover_image_url}
              alt=""
              fill
              preload
              sizes="(min-width: 1024px) 880px, 100vw"
              className="sfd-cover"
            />
          </div>
        )}

        <p className="sfd-article-body">{article.body}</p>

        <Link href="/news" className="sfd-textlink">
          お知らせ一覧へ
        </Link>
      </article>
    </Frame>
  );
}
