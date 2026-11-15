import { notFound } from 'next/navigation';
import { ARTICLES_PER_PAGE, REVIEWS_PER_PAGE } from '../lib/paging';
import type { StoreSitePageData, StoreSitePageName, StoreSiteRoute } from '../model/types';
import { storefrontService } from './storefront';

/**
 * 開くページが要るデータを取得する。サイト設定は全ページ共通なので殻が別に取る。
 * 存在しない・掲出外のキャストと記事は、ここで 404 にする。
 */
export async function loadPageData(
  route: StoreSiteRoute,
  storeId: string
): Promise<StoreSitePageData[StoreSitePageName]> {
  switch (route.page) {
    case 'page':
    case 'casts':
      return { casts: await storefrontService.fetchCasts(storeId) };
    case 'cast-detail': {
      const cast = await storefrontService.fetchCast(storeId, route.castId);
      return cast ? { cast } : notFound();
    }
    case 'schedule':
      return { shifts: await storefrontService.fetchShifts(storeId) };
    case 'reviews':
      return {
        reviews: await storefrontService.fetchReviews(storeId, route.pageIndex, REVIEWS_PER_PAGE),
        page: route.pageIndex,
      };
    case 'news':
      return {
        articles: await storefrontService.fetchArticles(
          storeId,
          route.pageIndex,
          ARTICLES_PER_PAGE
        ),
        page: route.pageIndex,
      };
    case 'news-detail': {
      const article = await storefrontService.fetchArticle(storeId, route.articleId);
      return article ? { article } : notFound();
    }
    default:
      return {};
  }
}
