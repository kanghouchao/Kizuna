import Image from 'next/image';
import Link from 'next/link';
import { pageHref } from '../../lib/paging';
import type { StoreSitePageProps } from '../../model/types';
import { publishedDate } from './format';
import Frame from './Frame';
import Notice from './Notice';
import PageSection from './PageSection';
import Pager from './Pager';

const BASE_PATH = '/news';

/** お知らせ一覧ページ（classic テンプレート）。日付・表題・小さな表紙の行で並べ、本文は記事ページで読む。 */
export default function ClassicNewsPage({
  store,
  siteConfig,
  articles,
  page,
}: StoreSitePageProps<'news'>) {
  return (
    <Frame store={store} siteConfig={siteConfig} current={BASE_PATH}>
      <PageSection title="お知らせ">
        {articles === null ? (
          <Notice
            alert
            message="お知らせを読み込めませんでした"
            link={{ href: pageHref(BASE_PATH, page), label: '再読み込み' }}
          />
        ) : articles.content.length > 0 ? (
          <>
            <ul className="c-news">
              {articles.content.map(article => (
                <li key={article.id}>
                  <Link href={`${BASE_PATH}/${article.id}`} className="c-news-item">
                    <div className="c-news-text">
                      {article.publish_from && (
                        <time className="c-news-date" dateTime={article.publish_from}>
                          {publishedDate(article.publish_from)}
                        </time>
                      )}
                      <h2 className="c-news-title c-display">{article.title}</h2>
                    </div>
                    {/* 表題がリンクの名前を担うので、表紙は読み上げない */}
                    {article.cover_image_url && (
                      <div className="c-frame c-news-cover">
                        <div className="c-frame-in">
                          <Image
                            src={article.cover_image_url}
                            alt=""
                            fill
                            sizes="(min-width: 768px) 168px, 104px"
                            className="c-photo"
                          />
                        </div>
                      </div>
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
      </PageSection>
    </Frame>
  );
}
