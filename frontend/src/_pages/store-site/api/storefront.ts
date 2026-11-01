import {
  Cast,
  PublicArticle,
  PublicArticleSummary,
  PublicReview,
  PublicShift,
  SiteConfig,
  StorefrontData,
} from '../model/types';
import type { Page } from '@/shared/api';
import { ServerApiError, serverClient } from '@/shared/api/index.server';

function storeHeaders(storeId: string): HeadersInit {
  return { 'X-Store-ID': storeId };
}

/** 公開店舗サイトのデータ取得。匿名の公開端点へ、呼び出し元が渡した店舗を X-Store-ID で名乗る。 */
export const storefrontService = {
  async getPageData(storeId: string): Promise<StorefrontData> {
    const [casts, siteConfig] = await Promise.all([
      this.fetchCasts(storeId),
      this.fetchSiteConfig(storeId),
    ]);

    return {
      casts,
      siteConfig,
    };
  },

  async fetchCasts(storeId: string): Promise<Cast[]> {
    try {
      return await serverClient.get<Cast[]>('/casts/public', {
        headers: storeHeaders(storeId),
        next: { revalidate: 60 },
      });
    } catch (error) {
      console.error('キャスト一覧の取得に失敗しました:', error);
      return [];
    }
  },

  /**
   * 公開キャスト一覧から 1 件を取得します（専用エンドポイントは持たない）。
   */
  async fetchCast(storeId: string, id: string): Promise<Cast | null> {
    const casts = await this.fetchCasts(storeId);
    return casts.find(cast => cast.id === id) ?? null;
  },

  /**
   * 本日の公開出勤シフト一覧を取得します（当日運用で変わるためキャッシュしない）
   */
  async fetchShifts(storeId: string): Promise<PublicShift[]> {
    try {
      return await serverClient.get<PublicShift[]>('/shifts/public', {
        headers: storeHeaders(storeId),
        cache: 'no-store',
      });
    } catch (error) {
      console.error('出勤情報の取得に失敗しました:', error);
      return [];
    }
  },

  /**
   * 掲出中の口コミを 1 ページ分取得します（公開期間が分単位で切り替わるためキャッシュしない）。
   * 失敗は null で返し、掲出 0 件と区別して画面に失敗を出させる。
   */
  async fetchReviews(
    storeId: string,
    page: number,
    size: number
  ): Promise<Page<PublicReview> | null> {
    try {
      return await serverClient.get<Page<PublicReview>>(
        `/reviews/public?page=${page}&size=${size}`,
        { headers: storeHeaders(storeId), cache: 'no-store' }
      );
    } catch (error) {
      console.error('口コミの取得に失敗しました:', error);
      return null;
    }
  },

  /**
   * 掲出中の記事を 1 ページ分取得します（公開期間が分単位で切り替わるためキャッシュしない）。
   * 失敗は null で返し、掲出 0 件と区別して画面に失敗を出させる。
   */
  async fetchArticles(
    storeId: string,
    page: number,
    size: number
  ): Promise<Page<PublicArticleSummary> | null> {
    try {
      return await serverClient.get<Page<PublicArticleSummary>>(
        `/articles/public?page=${page}&size=${size}`,
        { headers: storeHeaders(storeId), cache: 'no-store' }
      );
    } catch (error) {
      console.error('記事一覧の取得に失敗しました:', error);
      return null;
    }
  },

  /**
   * 掲出中の記事を 1 件取得します。404（存在しない・掲出外）だけを null にし、
   * それ以外の失敗は投げて「見つからない」と取り違えさせない。
   */
  async fetchArticle(storeId: string, id: string): Promise<PublicArticle | null> {
    try {
      return await serverClient.get<PublicArticle>(`/articles/public/${encodeURIComponent(id)}`, {
        headers: storeHeaders(storeId),
        cache: 'no-store',
      });
    } catch (error) {
      if (error instanceof ServerApiError && error.status === 404) return null;
      throw error;
    }
  },

  async fetchSiteConfig(storeId: string): Promise<SiteConfig> {
    try {
      return await serverClient.get<SiteConfig>('/config/public', {
        headers: storeHeaders(storeId),
        next: { revalidate: 60 },
      });
    } catch (error) {
      console.error('サイト設定の取得に失敗しました:', error);
      return {
        mv_type: 'image',
      };
    }
  },
};
