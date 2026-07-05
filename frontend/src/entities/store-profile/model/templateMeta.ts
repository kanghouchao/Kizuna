/**
 * 店舗模版のフロント側メタデータ（issue #223）。
 * 模版=コードであり DB には template_key しか持たない。各模版が追加で使う
 * テキストスロット（custom_texts の key）はここで宣言し、設定画面は
 * 現在の模版のスロット定義に従って動的にフォームを描画する。
 * 模版を切り替えても他模版の key は custom_texts 内に保持される（削除しない）。
 */
export interface TemplateTextSlot {
  key: string;
  label: string;
}

export interface StoreTemplateMeta {
  key: string;
  name: string;
  /** カードに表示する風格説明。 */
  description: string;
  /** サムネイル画像の public パス。 */
  thumbnail: string;
  textSlots: TemplateTextSlot[];
}

const TEMPLATE_METAS: Record<string, StoreTemplateMeta> = {
  default: {
    key: 'default',
    name: 'デフォルト',
    description: '黒×金のラグジュアリー系。セリフ体で高級感を演出',
    thumbnail: '/templates/default.svg',
    textSlots: [{ key: 'access_note', label: 'アクセス補足（店舗情報ページに表示）' }],
  },
  modern: {
    key: 'modern',
    name: 'モダン',
    description: '藍黒×薔薇紅のビビッド系。サンセリフ体でモダンな印象',
    thumbnail: '/templates/modern.svg',
    textSlots: [{ key: 'access_note', label: 'アクセス補足（店舗情報ページに表示）' }],
  },
  classic: {
    key: 'classic',
    name: 'クラシック',
    description: '暖白×松石藍の明るく清潔な印象。セリフ体で上品に',
    thumbnail: '/templates/classic.svg',
    textSlots: [{ key: 'access_note', label: 'アクセス補足（店舗情報ページに表示）' }],
  },
};

/** 未知の key は default のメタにフォールバックする。 */
export function getTemplateMeta(templateKey: string): StoreTemplateMeta {
  return TEMPLATE_METAS[templateKey] ?? TEMPLATE_METAS['default'];
}

/** カード選択 UI 用に全模版メタを配列で返す。 */
export function getAllTemplateMetas(): StoreTemplateMeta[] {
  return Object.values(TEMPLATE_METAS);
}
