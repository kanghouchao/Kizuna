import { CastResponse } from '@/entities/cast';
import { ShiftResponse } from '@/entities/shift';
import { hhmm } from './datetime';

/** 名簿から表示名を引く。id 未指定を「id を持たないキャスト」と取り違えないよう先に弾く。 */
export function castName(casts: CastResponse[], id: string | undefined): string {
  return (id === undefined ? undefined : casts.find(c => c.id === id)?.name) ?? '不明';
}

/**
 * シフト 1 件の呼び名（キャスト名 + 時間帯）。タイムラインの目玉とパネルの Switch は同じ行への
 * 二つの入口なので、読み上げられる名前も一つの組み立てから出す。
 */
export function shiftLabel(shift: ShiftResponse, casts: CastResponse[]): string {
  return `${castName(casts, shift.cast_id)} ${hhmm(shift.start_time)}–${hhmm(shift.end_time)}`;
}
