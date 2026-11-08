import type { SelfRemunerationItem, SelfRemunerationSnapshot } from '@/entities/order';
import { formatMinutes, formatRevision } from '@/shared/lib';

const BASES = {
  CURRENT_SETTING: '店舗の設定',
  ACCEPTED_TERMS: '受諾した条件',
  HISTORICAL_CORRECTION: '過去の版による訂正',
};

/** 明細の約定条件の根拠。受諾した条件は受諾した版を添える。 */
export function itemTerms(item: SelfRemunerationItem): string | undefined {
  if (!item.adoption_basis) return undefined;
  const version =
    item.adoption_basis === 'ACCEPTED_TERMS' && item.consent_version !== undefined
      ? `（${formatRevision(item.consent_version)}）`
      : '';
  return `${BASES[item.adoption_basis]}${version}`;
}

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

const text = <T>(
  label: string,
  before: T | undefined,
  after: T | undefined,
  item: string,
  format: (value: T) => string = String
): RemunerationChange[] => {
  if (before === after) return [];
  const show = (value: T | undefined) => (value === undefined ? 'なし' : format(value));
  return [{ kind: 'text', item, label, text: `${show(before)} → ${show(after)}` }];
};

const money = (
  label: string,
  before: number,
  after: number,
  item?: string
): RemunerationChange[] =>
  before === after
    ? []
    : [{ kind: 'changed', label, before, after, ...(item === undefined ? {} : { item }) }];

/** 増えた・消えた明細は、お客様料金と固定報酬を分けてそれぞれの額を出す。 */
const amounts = (kind: 'added' | 'removed', item: SelfRemunerationItem): RemunerationChange[] => [
  { kind, item: item.name, label: 'お客様料金', amount: item.price },
  { kind, item: item.name, label: '固定報酬', amount: item.remuneration },
];

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
      rows.push(...amounts('removed', old));
      return;
    }
    // 別の版への訂正は金額が同じでも名称・時間・条件が変わる。金額だけを比べると何が変わったか読めない
    rows.push(...text('名称', old.name, next.name, next.name));
    rows.push(
      ...text('時間', old.duration_minutes, next.duration_minutes, next.name, formatMinutes)
    );
    rows.push(...text('条件', itemTerms(old), itemTerms(next), next.name));
    rows.push(...text('版', old.revision_number, next.revision_number, next.name, formatRevision));
    rows.push(...money('お客様料金', old.price, next.price, next.name));
    rows.push(...money('固定報酬', old.remuneration, next.remuneration, next.name));
  });
  const kept = new Set(beforeKeys);
  after.items.forEach((item, i) => {
    if (!kept.has(afterKeys[i])) rows.push(...amounts('added', item));
  });
  return rows;
}
