import { ShiftResponse } from '@/entities/shift';

/**
 * 公式サイトへ出さない確定シフトか。タイムライン・カレンダー・公開パネルが同じ判定を映すため、
 * 述語はここ一箇所に持つ。
 *
 * <p>公開可否は確定シフトの上の軸であり、仮シフト（TENTATIVE）はフラグ値に関わらず店外へ出ない
 * ので非公開の数には入れない（ADR 0015）。published は省略され得るため、明示的な false だけを
 * 非公開と読む — 未知を非公開に倒すと、値の載らない応答が全件「隠れている」に化ける。
 */
export function isUnpublished(shift: ShiftResponse): boolean {
  return shift.status === 'CONFIRMED' && shift.published === false;
}
