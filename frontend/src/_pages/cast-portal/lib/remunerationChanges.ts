import type { SelfRemunerationItem, SelfRemunerationSnapshot } from '@/entities/order';

/** 変更前後の差の 1 行。明細の差は item に明細名を持ち、label は短い項目名だけにする。 */
export type RemunerationChange = { item?: string } & (
  | { kind: 'changed'; label: string; before: number; after: number }
  | { kind: 'added' | 'removed'; label: string; amount: number }
  | { kind: 'text'; label: string; text: string }
);

/**
 * 明細の同一性の鍵（表示の React key と変更前後の突き合わせで共有する）。行 ID の無い明細は
 * 種別とサービスで見分け、同じ鍵が並ぶときは出現順の番号で区別する。
 */
export function itemKeys(items: readonly SelfRemunerationItem[]): string[] {
  const seen = new Map<string, number>();
  return items.map(item => {
    const base = item.line_id ?? `${item.kind}:${item.service_id ?? item.name}`;
    const nth = seen.get(base) ?? 0;
    seen.set(base, nth + 1);
    return `${base}#${nth}`;
  });
}

const money = (
  label: string,
  before: number,
  after: number,
  item?: string
): RemunerationChange[] =>
  before === after
    ? []
    : [{ kind: 'changed', label, before, after, ...(item === undefined ? {} : { item }) }];

/** 変更前後で値が変わった所だけを、利用者が読む順（状態 → 合計 → 明細）に並べる。 */
export function remunerationChanges(
  before: SelfRemunerationSnapshot,
  after: SelfRemunerationSnapshot
): RemunerationChange[] {
  const rows: RemunerationChange[] = [];
  if (before.completion_invalidated !== after.completion_invalidated)
    rows.push({
      kind: 'text',
      label: '状態',
      text: after.completion_invalidated ? '有効 → 無効化' : '無効化 → 有効',
    });
  rows.push(...money('約定報酬', before.agreed_remuneration, after.agreed_remuneration));
  rows.push(...money('発生済み報酬', before.accrued_remuneration, after.accrued_remuneration));
  const afterKeys = itemKeys(after.items);
  const afterItems = new Map(afterKeys.map((key, i) => [key, after.items[i]]));
  const beforeKeys = itemKeys(before.items);
  before.items.forEach((old, i) => {
    const next = afterItems.get(beforeKeys[i]);
    if (!next) {
      rows.push({ kind: 'removed', item: old.name, label: '削除', amount: old.remuneration });
      return;
    }
    rows.push(...money('お客様料金', old.price, next.price, next.name));
    rows.push(...money('固定報酬', old.remuneration, next.remuneration, next.name));
  });
  const kept = new Set(beforeKeys);
  after.items.forEach((item, i) => {
    if (!kept.has(afterKeys[i]))
      rows.push({ kind: 'added', item: item.name, label: '追加', amount: item.remuneration });
  });
  return rows;
}
