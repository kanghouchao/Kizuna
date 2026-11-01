import type { Publication } from '@/shared/api';

export interface PublicationFormValues {
  published: boolean;
  publish_from: string;
  publish_until: string;
  display_order: number | null;
}

// 公開期間は店舗の現地日時（Asia/Tokyo）で入出力する。夏時間が無いので +09:00 固定で変換できる。
function storeInput(value: string | null | undefined) {
  if (!value) return '';
  return new Date(new Date(value).getTime() + 9 * 3600000).toISOString().slice(0, 16);
}

function fromStoreInput(value: string) {
  return value === '' ? null : new Date(`${value}:00+09:00`).toISOString();
}

function storeTime(value: string) {
  return new Date(value).toLocaleString('ja-JP', { timeZone: 'Asia/Tokyo' });
}

export const optionalStoreDate = (value: string) =>
  value === '' ||
  Number.isFinite(new Date(`${value}:00+09:00`).getTime()) ||
  '日時を正しく入力してください';

export function toPublicationFormValues(p: Publication | undefined): PublicationFormValues {
  return {
    published: p?.published ?? false,
    publish_from: storeInput(p?.publish_from),
    publish_until: storeInput(p?.publish_until),
    display_order: p?.display_order ?? null,
  };
}

export function fromPublicationFormValues(values: PublicationFormValues): Publication {
  return {
    published: values.published,
    publish_from: fromStoreInput(values.publish_from),
    publish_until: fromStoreInput(values.publish_until),
    display_order: values.display_order,
  };
}

export function publicationSummary(p: Publication) {
  const period =
    p.publish_from || p.publish_until
      ? `${p.publish_from ? storeTime(p.publish_from) : '開始指定なし'} 〜 ${p.publish_until ? storeTime(p.publish_until) : '終了指定なし'}`
      : '期間指定なし';
  const order = p.display_order == null ? '表示順指定なし' : `表示順 ${p.display_order}`;
  return `${p.published ? '公開' : '非公開'} / ${period} / ${order}`;
}
