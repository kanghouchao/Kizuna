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
  textSlots: TemplateTextSlot[];
}

const TEMPLATE_METAS: Record<string, StoreTemplateMeta> = {
  default: {
    key: 'default',
    name: 'デフォルト',
    textSlots: [{ key: 'access_note', label: 'アクセス補足（店舗情報ページに表示）' }],
  },
  // modern / classic は #223 Phase 3 で追加する
};

/** 未知の key は default のメタにフォールバックする。 */
export function getTemplateMeta(templateKey: string): StoreTemplateMeta {
  return TEMPLATE_METAS[templateKey] ?? TEMPLATE_METAS['default'];
}
