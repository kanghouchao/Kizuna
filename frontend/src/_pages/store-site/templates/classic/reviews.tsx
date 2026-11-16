import { pageHref } from '../../lib/paging';
import type { StoreSitePageProps } from '../../model/types';
import { localDate } from './format';
import Frame from './Frame';
import Notice from './Notice';
import PageSection from './PageSection';
import Pager from './Pager';

const BASE_PATH = '/reviews';

/** 口コミページ（classic テンプレート）。本文は文字のまま、受け付けたときの改行を保って並べる。 */
export default function ClassicReviewsPage({
  store,
  siteConfig,
  reviews,
  page,
}: StoreSitePageProps<'reviews'>) {
  return (
    <Frame store={store} siteConfig={siteConfig} current={BASE_PATH}>
      <PageSection title="口コミ">
        {reviews === null ? (
          <Notice
            alert
            message="口コミを読み込めませんでした"
            link={{ href: pageHref(BASE_PATH, page), label: '再読み込み' }}
          />
        ) : reviews.content.length > 0 ? (
          <>
            <ul className="c-voices">
              {reviews.content.map((review, index) => (
                <li key={`${reviews.number}-${index}`}>
                  <article className="c-voice">
                    <p className="c-voice-body">{review.body}</p>
                    <p className="c-voice-by">
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
      </PageSection>
    </Frame>
  );
}
