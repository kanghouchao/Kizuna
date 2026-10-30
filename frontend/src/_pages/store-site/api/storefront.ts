import { Cast, PublicShift, SiteConfig, StorefrontData } from '../model/types';
import { serverClient } from '@/shared/api/index.server';

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
