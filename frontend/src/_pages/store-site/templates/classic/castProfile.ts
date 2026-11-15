import type { Cast } from '../../model/types';

/** プロフィールの行。値の無い項目は行ごと出さない。custom_fields はサーバが公開分だけを表示順に返す。 */
export function castProfileRows(cast: Cast): { label: string; value: string }[] {
  const rows: { label: string; value: string }[] = [];
  if (cast.age) rows.push({ label: '年齢', value: `${cast.age}歳` });
  if (cast.height) rows.push({ label: '身長', value: `${cast.height}cm` });
  if (cast.bust) rows.push({ label: 'バスト', value: `${cast.bust}` });
  if (cast.waist) rows.push({ label: 'ウエスト', value: `${cast.waist}` });
  if (cast.hip) rows.push({ label: 'ヒップ', value: `${cast.hip}` });
  cast.custom_fields?.forEach(field =>
    rows.push({ label: field.label ?? '', value: field.value ?? '' })
  );
  return rows;
}
