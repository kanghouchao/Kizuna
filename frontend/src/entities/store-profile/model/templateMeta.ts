/**
 * 店舗テンプレートのフロント側の宣言。テンプレートはコードであり、DB は template_key だけを持つ。
 * 宣言は選択カードに出す情報だけを持ち、店舗が設定する項目はテンプレートに依らない。
 */
export interface StoreTemplateMeta {
  key: string;
  name: string;
  /** カードに表示する風格説明。 */
  description: string;
  /** サムネイル画像の public パス。 */
  thumbnail: string;
}

const TEMPLATE_METAS: StoreTemplateMeta[] = [
  {
    key: 'default',
    name: 'デフォルト',
    description: '黒×金のラグジュアリー系。セリフ体で高級感を演出',
    thumbnail: '/templates/default.svg',
  },
  {
    key: 'modern',
    name: 'モダン',
    description: '藍黒×薔薇紅のビビッド系。サンセリフ体でモダンな印象',
    thumbnail: '/templates/modern.svg',
  },
  {
    key: 'classic',
    name: 'クラシック',
    description: '暖白×松石藍の明るく清潔な印象。セリフ体で上品に',
    thumbnail: '/templates/classic.svg',
  },
];

/** カード選択 UI 用に全テンプレートの宣言を配列で返す。 */
export function getAllTemplateMetas(): StoreTemplateMeta[] {
  return TEMPLATE_METAS;
}
