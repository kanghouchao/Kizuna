import { Cast, SiteConfig, StorefrontData } from '@/types/storefront';
import { serverClient } from '@/lib/server-client';

/**
 * ストアフロント用データサービス
 * serverClient を使用してバックエンドAPIと通信します。
 */
export const storefrontService = {
  /**
   * テナントページの表示に必要な全データを取得します
   * テナントIDは serverClient 内部で Cookie から自動解決されます。
   */
  async getPageData(): Promise<StorefrontData> {
    // データの並列取得
    const [casts, siteConfig] = await Promise.all([this.fetchCasts(), this.fetchSiteConfig()]);

    return {
      casts,
      siteConfig,
    };
  },

  /**
   * キャスト一覧を取得します
   */
  async fetchCasts(): Promise<Cast[]> {
    try {
      // serverClient が URL解決、ヘッダー注入を自動で行います
      return await serverClient.get<Cast[]>('/tenant/casts/public', {
        next: { revalidate: 60 },
      });
    } catch (error) {
      console.error('キャスト一覧の取得に失敗しました:', error);
      return [];
    }
  },

  /**
   * サイト設定を取得します
   */
  async fetchSiteConfig(): Promise<SiteConfig> {
    try {
      return await serverClient.get<SiteConfig>('/tenant/config/public', {
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
