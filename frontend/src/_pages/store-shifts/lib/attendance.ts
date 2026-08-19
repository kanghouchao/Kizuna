/**
 * 当日実績（実際に出勤した事実）の語義。実績はシフトと別の集約で、記録・訂正・取消のライフサイクルを
 * 自分で持つ（ADR 0014）。画面が同じ判定を何度も導かないよう、述語はここ一箇所に置く。
 */
import { AttendanceResponse, ShiftResponse } from '@/entities/shift';

/**
 * シフト id → そのシフトの未取消の実績。読み口に取消済みは現れないので、この写像に居ること自体が
 * 「未取消の実績が付いている」— シフトの勤務日・キャスト変更が塞がる条件そのものである。
 */
export function attendanceByShift(
  attendances: AttendanceResponse[]
): Map<string, AttendanceResponse> {
  const byShift = new Map<string, AttendanceResponse>();
  for (const a of attendances) {
    if (a.shift_id !== undefined) byShift.set(a.shift_id, a);
  }
  return byShift;
}

/**
 * その営業日に未取消の実績を持つキャストの集合。実績は（キャスト・営業日）に 1 行しか立たないため、
 * ここに居るキャストへの二本目の記録は必ず後端で撥ねられる（ADR 0014 の帰結）。記録の口を出すかどうかは
 * この集合が決める。
 */
export function castsWithAttendance(attendances: AttendanceResponse[]): Set<string> {
  const casts = new Set<string>();
  for (const a of attendances) {
    if (a.cast_id !== undefined) casts.add(a.cast_id);
  }
  return casts;
}

/** 予定なしの飛び込み出勤か。 */
export function isWalkIn(attendance: AttendanceResponse): boolean {
  return attendance.shift_id === undefined;
}

/** 'yyyy-MM-ddTHH:mm:ss' を datetime-local 入力が読める 'yyyy-MM-ddTHH:mm' へ切り詰める。 */
export function toDateTimeInput(value: string | undefined): string {
  return (value ?? '').slice(0, 16);
}

/** Date を datetime-local 入力の 'yyyy-MM-ddTHH:mm'（ローカルタイム基準）へ整形する。 */
export function nowDateTimeInput(now: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}`;
}

/** 実績 1 件の時間帯の表示。未終了は終了側を空けたまま示す — 記入漏れと閉店前を同じ姿にしない。 */
export function attendanceSpanLabel(attendance: AttendanceResponse): string {
  const at = (value: string | undefined) => (value ?? '').slice(11, 16);
  return `${at(attendance.actual_start_at)}–${at(attendance.actual_end_at)}`;
}
