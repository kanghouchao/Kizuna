import Image from 'next/image';
import Link from 'next/link';
import { pageHref } from '../../lib/paging';
import type { StoreSitePageProps } from '../../model/types';
import { publishedOn } from './format';
import Frame from './Frame';
import { Notice, Pager } from './Paging';

const BASE_PATH = '/news';

/** お知らせ一覧ページ（default テンプレート）。本文は記事ページだけで読む。 */
export default function DefaultNewsPage({
  store,
  siteConfig,
  articles,
  page,
}: StoreSitePageProps<'news'>) {
  return (
    <Frame store={store} siteConfig={siteConfig}>
      <header className="sfd-pagehead">
        <p className="sfd-label">News</p>
        <h1 className="sfd-h1">お知らせ</h1>
      </header>

      <div className="sfd-page">
        {articles === null ? (
          <Notice
            alert
            message="お知らせを読み込めませんでした"
            link={{ href: pageHref(BASE_PATH, page), label: '再読み込み' }}
          />
        ) : articles.content.length > 0 ? (
          <>
            <ul className="sfd-news">
              {articles.content.map(article => (
                <li key={article.id}>
                  <Link href={`${BASE_PATH}/${article.id}`} className="sfd-news-item">
                    <div className="sfd-news-text">
                      {article.publish_from && (
                        <time dateTime={article.publish_from} className="sfd-date">
                          {publishedOn(article.publish_from)}
                        </time>
                      )}
                      <h2 className="sfd-news-title">{article.title}</h2>
                    </div>
                    {/* 表題がリンクの名前を担うので、表紙は飾りとして読み上げない */}
                    {article.cover_image_url && (
                      <span className="sfd-news-cover">
                        <Image
                          src={article.cover_image_url}
                          alt=""
                          fill
                          sizes="(min-width: 768px) 200px, 112px"
                          className="sfd-cover"
                        />
                      </span>
                    )}
                  </Link>
                </li>
              ))}
            </ul>
            {articles.total_pages > 1 && <Pager basePath={BASE_PATH} result={articles} />}
          </>
        ) : articles.total_elements > 0 ? (
          // 掲出が減ってページ数が縮むと、開いていたページだけが空になる
          <Notice
            message="このページにお知らせはありません"
            link={{ href: pageHref(BASE_PATH, 0), label: '最初のページへ' }}
          />
        ) : (
          <Notice message="掲載中のお知らせはありません" />
        )}
      </div>
    </Frame>
  );
}
