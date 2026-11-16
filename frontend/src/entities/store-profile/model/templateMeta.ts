/**
 * 店舗テンプレートのフロント側の宣言。テンプレートはコードであり、DB は template_key だけを持つ。
 * 宣言は選択カードに出す情報だけを持ち、店舗が設定する項目はテンプレートに依らない。
 */
export interface StoreTemplateMeta {
  key: string;
  name: string;
  /** カードに表示する風格説明。 */
  description: string;
  /** サムネイル画像の public パス。各テンプレートのトップを同じ内容で写したもの。 */
  thumbnail: string;
}

const TEMPLATE_METAS: StoreTemplateMeta[] = [
  {
    key: 'default',
    name: 'デフォルト',
    description:
      '高級感のある静かな佇まい。画面いっぱいの Banner に店名を重ね、キャストを縦長の写真で見せる',
    thumbnail: '/templates/default.png',
  },
  {
    key: 'modern',
    name: 'モダン',
    description: '明るくポップな印象。太い丸ゴシックと角丸のカードで、キャストを密に並べる',
    thumbnail: '/templates/modern.png',
  },
  {
    key: 'classic',
    name: 'クラシック',
    description: '和風の落ち着いた佇まい。縦書きの店名と罫線で、冊子のように読ませる',
    thumbnail: '/templates/classic.png',
  },
];

/** カード選択 UI 用に全テンプレートの宣言を配列で返す。 */
export function getAllTemplateMetas(): StoreTemplateMeta[] {
  return TEMPLATE_METAS;
}
