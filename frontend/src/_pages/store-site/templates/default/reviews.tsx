import { pageHref } from '../../lib/paging';
import type { StoreSitePageProps } from '../../model/types';
import { localDate } from './format';
import Frame from './Frame';
import { Notice, Pager } from './Paging';

const BASE_PATH = '/reviews';

/** 口コミページ（default テンプレート）。本文は文字として出し、受け付けたときの改行を保つ。 */
export default function DefaultReviewsPage({
  store,
  siteConfig,
  reviews,
  page,
}: StoreSitePageProps<'reviews'>) {
  return (
    <Frame store={store} siteConfig={siteConfig}>
      <header className="sfd-pagehead">
        <p className="sfd-label">Reviews</p>
        <h1 className="sfd-h1">口コミ</h1>
        {!!reviews?.content.length && <p className="sfd-count">全 {reviews.total_elements} 件</p>}
      </header>

      <div className="sfd-page">
        {reviews === null ? (
          <Notice
            alert
            message="口コミを読み込めませんでした"
            link={{ href: pageHref(BASE_PATH, page), label: '再読み込み' }}
          />
        ) : reviews.content.length > 0 ? (
          <>
            <ul className="sfd-reviews">
              {reviews.content.map((review, i) => (
                <li key={`${reviews.number}-${i}`}>
                  <article className="sfd-review">
                    <p className="sfd-review-body">{review.body}</p>
                    <p className="sfd-review-by">
                      <span>{review.display_name || '匿名'}</span>
                      <time dateTime={review.received_on}>{localDate(review.received_on)}</time>
                    </p>
                  </article>
                </li>
              ))}
            </ul>
            {reviews.total_pages > 1 && <Pager basePath={BASE_PATH} result={reviews} />}
          </>
        ) : reviews.total_elements > 0 ? (
          // 掲出が減ってページ数が縮むと、開いていたページだけが空になる
          <Notice
            message="このページに口コミはありません"
            link={{ href: pageHref(BASE_PATH, 0), label: '最初のページへ' }}
          />
        ) : (
          <Notice message="掲載中の口コミはありません" />
        )}
      </div>
    </Frame>
  );
}
