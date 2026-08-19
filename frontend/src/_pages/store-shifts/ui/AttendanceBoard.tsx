'use client';

import { PlusIcon } from 'lucide-react';
import { useMemo } from 'react';
import { CastResponse } from '@/entities/cast';
import { AbsenceResponse, AttendanceResponse, ShiftResponse } from '@/entities/shift';
import { Badge, Button, RegionError } from '@/shared/ui';
import { hhmm, shiftSpan } from '../lib/datetime';
import { castName } from '../lib/labels';
import { attendanceByShift, attendanceSpanLabel, castsWithAttendance } from '../lib/attendance';
import { AttendanceFormTarget } from './AttendanceFormModal';
import { ShiftDayNav } from './ShiftDayNav';

interface AttendanceBoardProps {
  /** 表示営業日 'yyyy-MM-dd'。 */
  date: string;
  /** その営業日のシフト。 */
  shifts: ShiftResponse[];
  attendances: AttendanceResponse[];
  absences: AbsenceResponse[];
  casts: CastResponse[];
  loading: boolean;
  /** シフトか実績が読めていない。どちらが欠けても「未記録」を名乗れない。 */
  failed: boolean;
  onRetry: () => void;
  /** 欠勤だけが読めていない。行は出せるが「欠勤ではない」とは言えない。 */
  absencesFailed: boolean;
  onRetryAbsences: () => void;
  onChangeDate: (date: string) => void;
  onOpenForm: (target: AttendanceFormTarget) => void;
  onCancel: (attendance: AttendanceResponse) => void;
}

/** シフト 1 本と、それに付いた実績の有無。 */
interface ShiftRow {
  shift: ShiftResponse;
  attendance: AttendanceResponse | undefined;
  /** 導出された欠勤か。 */
  absent: boolean;
  /** 同じ営業日の別の枠で既に記録済みか。実績は（キャスト・営業日）に 1 行しか立たない。 */
  recordedElsewhere: boolean;
}

/** 記録済みの実績を示す行の右側。シフト紐づきの行と飛び込みの行が同じ姿を持つ。 */
function RecordedAttendance({
  attendance,
  label,
  onCorrect,
  onCancel,
}: {
  attendance: AttendanceResponse;
  /** 読み上げの宛先になるキャスト名。 */
  label: string;
  onCorrect: () => void;
  onCancel: () => void;
}) {
  return (
    <>
      <span className="text-sm text-foreground">{attendanceSpanLabel(attendance)}</span>
      {attendance.waiting_place !== undefined && (
        <span className="text-xs text-muted-foreground">待機 {attendance.waiting_place}</span>
      )}
      <Button
        type="button"
        variant="outline"
        size="sm"
        onClick={onCorrect}
        aria-label={`${label}の実績を訂正`}
      >
        訂正
      </Button>
      <Button
        type="button"
        variant="ghost"
        size="sm"
        className="text-destructive-strong"
        onClick={onCancel}
        aria-label={`${label}の実績を取消`}
      >
        取消
      </Button>
    </>
  );
}

/**
 * 当日実績の記録・訂正・取消の面。予定（シフト）と実績を 1 行に並べ、欠勤（導出）もここで名乗る。
 * 実績は（キャスト・営業日）に 1 行しか立たないので、同じキャストの二本目の枠には記録の口を
 * 出さない — 出しても後端の一意性で必ず撥ねられる（ADR 0014）。
 */
export function AttendanceBoard({
  date,
  shifts,
  attendances,
  absences,
  casts,
  loading,
  failed,
  onRetry,
  absencesFailed,
  onRetryAbsences,
  onChangeDate,
  onOpenForm,
  onCancel,
}: AttendanceBoardProps) {
  const { shiftRows, walkIns, recordedCount, unrecordedCount } = useMemo(() => {
    const byShift = attendanceByShift(attendances);
    const attended = castsWithAttendance(attendances);
    const absentCasts = new Set(absences.map(a => a.cast_id));

    const rows: ShiftRow[] = shifts
      .map(shift => {
        const attendance = shift.id === undefined ? undefined : byShift.get(shift.id);
        const castId = shift.cast_id;
        return {
          shift,
          attendance,
          absent: castId !== undefined && absentCasts.has(castId),
          recordedElsewhere:
            attendance === undefined && castId !== undefined && attended.has(castId),
        };
      })
      .sort((a, b) => {
        const byStart =
          shiftSpan(a.shift.start_time, a.shift.end_time).start -
          shiftSpan(b.shift.start_time, b.shift.end_time).start;
        return byStart !== 0
          ? byStart
          : castName(casts, a.shift.cast_id).localeCompare(castName(casts, b.shift.cast_id));
      });

    // 予定なしの飛び込みは並べる予定を持たないので、実開始の順に後ろへ置く
    const standalone = attendances
      .filter(a => a.shift_id === undefined)
      .sort((a, b) => (a.actual_start_at ?? '').localeCompare(b.actual_start_at ?? ''));

    // 未記録は枠ではなくキャストで数える。実績が営業日に 1 行である以上、
    // 同じキャストの二本目の枠は独立した「未記録」ではない
    const confirmedCasts = new Set(
      shifts
        .filter(s => s.status === 'CONFIRMED' && s.cast_id !== undefined)
        .map(s => s.cast_id as string)
    );
    const unrecorded = [...confirmedCasts].filter(castId => !attended.has(castId)).length;

    return {
      shiftRows: rows,
      walkIns: standalone,
      recordedCount: attendances.length,
      unrecordedCount: unrecorded,
    };
  }, [shifts, attendances, absences, casts]);

  const hasRows = shiftRows.length > 0 || walkIns.length > 0;

  return (
    <div className="rounded-lg border bg-card shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-6 py-4">
        <div>
          <h2 className="text-lg font-semibold text-foreground">{date} の実績</h2>
          <p className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
            <span className="text-success-strong">実績 {recordedCount}件</span>
            {/* 欠勤は未記録の部分集合（確定シフトあり・実績なしのうち門を過ぎた分）。並べて置くと
                三つの排他な桶に読めて、同じ人が二度数えられているように見える */}
            <span className="text-muted-foreground">
              未記録 {unrecordedCount}件
              {absences.length > 0 && (
                <span className="text-destructive-strong">（うち欠勤 {absences.length}件）</span>
              )}
            </span>
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <ShiftDayNav date={date} onChangeDate={onChangeDate} />
          <Button type="button" onClick={() => onOpenForm({ attendance: null, shift: null })}>
            <PlusIcon />
            飛び込みを記録
          </Button>
        </div>
      </div>

      {/* 欠勤は導出で、営業日の終了と各キャストの最終予定終了の経過を待つ。0 件を「誰も休んで
          いない」と読ませないため、件数の隣ではなく常設の注記として置く */}
      <p className="border-b px-6 py-2 text-xs text-muted-foreground">
        欠勤は営業日が終わり、そのキャストの最後の予定終了を過ぎてから導出されます。
      </p>
      {absencesFailed && (
        <div className="border-b px-6 py-2">
          <RegionError message="欠勤の取得に失敗しました" onRetry={onRetryAbsences} />
        </div>
      )}

      {failed ? (
        <RegionError
          message="シフトと実績の取得に失敗しました"
          onRetry={onRetry}
          className="justify-center p-8"
        />
      ) : loading ? (
        <div className="p-8 text-center text-muted-foreground">読み込み中...</div>
      ) : !hasRows ? (
        <div className="p-12 text-center">
          <p className="text-muted-foreground">この日のシフトも実績もまだありません。</p>
        </div>
      ) : (
        <ul className="divide-y">
          {shiftRows.map(({ shift, attendance, absent, recordedElsewhere }) => {
            const confirmed = shift.status === 'CONFIRMED';
            const label = castName(casts, shift.cast_id);
            return (
              <li
                key={shift.id}
                className="flex flex-wrap items-center justify-between gap-3 px-6 py-3"
              >
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-foreground">{label}</p>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    予定 {hhmm(shift.start_time)}–{hhmm(shift.end_time)}
                    {!confirmed && '（未確定）'}
                  </p>
                </div>
                <div className="flex shrink-0 flex-wrap items-center gap-3">
                  {attendance ? (
                    <RecordedAttendance
                      attendance={attendance}
                      label={label}
                      onCorrect={() => onOpenForm({ attendance, shift })}
                      onCancel={() => onCancel(attendance)}
                    />
                  ) : !confirmed ? (
                    // 実績を指せるのは確定シフトだけ（ADR 0014）。記録の口を出しても後端が撥ねる。
                    // 欠勤より先に見るのは、欠勤が（キャスト・営業日）の事実であって枠の事実では
                    // ないためで、確定していない枠に付けると「この枠を飛ばした」と読める
                    <span className="text-xs text-muted-foreground">
                      確定すると実績を記録できます
                    </span>
                  ) : absent ? (
                    <Badge
                      variant="outline"
                      className="border-transparent bg-destructive/10 text-destructive-strong"
                    >
                      欠勤
                    </Badge>
                  ) : recordedElsewhere ? (
                    <span className="text-xs text-muted-foreground">
                      同じ営業日の実績は 1 件にまとまります
                    </span>
                  ) : (
                    <>
                      <span className="text-xs text-muted-foreground">未記録</span>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => onOpenForm({ attendance: null, shift })}
                        aria-label={`${label}の実績を記録する`}
                      >
                        記録する
                      </Button>
                    </>
                  )}
                </div>
              </li>
            );
          })}
          {walkIns.map(attendance => {
            const label = castName(casts, attendance.cast_id);
            return (
              <li
                key={attendance.id}
                className="flex flex-wrap items-center justify-between gap-3 px-6 py-3"
              >
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-foreground">{label}</p>
                  {/* 予定の行と同じ位置なので、予定が無いこともその場所で言う */}
                  <p className="mt-0.5 text-xs text-muted-foreground">飛び込み（予定なし）</p>
                </div>
                <div className="flex shrink-0 flex-wrap items-center gap-3">
                  <RecordedAttendance
                    attendance={attendance}
                    label={label}
                    onCorrect={() => onOpenForm({ attendance, shift: null })}
                    onCancel={() => onCancel(attendance)}
                  />
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
