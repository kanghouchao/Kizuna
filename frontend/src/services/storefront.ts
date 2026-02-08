import { Cast, SiteConfig, StorefrontData } from '@/types/storefront';

/**
 * ストアフロント用データサービス
 * バックエンドAPIとの通信詳細をカプセル化します。
 */
export const storefrontService = {
  /**
   * テナントページの表示に必要な全データを取得します
   * @param tenantId テナントID
   */
  async getPageData(tenantId: string): Promise<StorefrontData> {
    // データの並列取得
    const [casts, siteConfig] = await Promise.all([
      this.fetchCasts(tenantId),
      this.fetchSiteConfig(tenantId),
    ]);

    return {
      casts,
      siteConfig,
    };
  },

  /**
   * キャスト一覧を取得します
   */
  async fetchCasts(tenantId: string): Promise<Cast[]> {
    try {
      const backendUrl =
        process.env.TENANT_VALIDATION_API_URL?.replace('/central/tenant', '') ||
        'http://backend:8080';

      const response = await fetch(`${backendUrl}/tenant/casts/public`, {
        headers: {
          'X-Role': 'tenant',
          'X-Tenant-ID': tenantId,
        },
        next: { revalidate: 60 },
      });

      if (!response.ok) return [];
      return await response.json();
    } catch (error) {
      console.error('キャスト一覧の取得に失敗しました:', error);
      return [];
    }
  },

  /**
   * サイト設定を取得します (現在はモック、将来的にAPIへ置き換え)
   */
  async fetchSiteConfig(tenantId: string): Promise<SiteConfig> {
    // TODO: 実際のAPIから設定を取得するように実装する
    return {
      logo_url: undefined,
      banner_url: undefined,
      description: undefined,
      mv_url: undefined,
      mv_type: 'image',
      sns_links: undefined,
      partner_links: undefined,
    };
  },
};
