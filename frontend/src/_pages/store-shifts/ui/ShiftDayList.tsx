'use client';

import { EyeIcon, EyeOffIcon } from 'lucide-react';
import { CastResponse } from '@/entities/cast';
import { ShiftResponse } from '@/entities/shift';
import { Button, Switch } from '@/shared/ui';
import { shiftLabel } from '../lib/labels';
import { isUnpublished } from '../lib/publication';

interface ShiftDayListProps {
  shifts: ShiftResponse[];
  casts: CastResponse[];
  publishing: boolean;
  /**
   * 未取消の当日実績が付いているシフトの id。読み口に取消済みは現れないので、取消済みの実績
   * しか無い行はこの集合に居らず削除の口が戻る — 画面からは見分けられないためで、そこは
   * 後端が RESTRICT で拒む（ADR 0014）。
   */
  attendedShiftIds: ReadonlySet<string>;
  /** 実績をまだ読めていない。未知は「実績なし」ではないので、その間は削除の口を伏せる。 */
  attendanceLoading: boolean;
  /** 公開可否の変更。一括もここへ複数件で入る（一括 API は無い — ADR 0015）。 */
  onChangePublication: (targets: ShiftResponse[], published: boolean) => void;
  /** 削除の要求。確認は呼び出し側が受ける。 */
  onDelete: (shift: ShiftResponse) => void;
}

/**
 * その日のシフトを 1 行ずつ並べ、行単位の操作（公開可否の切替・削除）を受ける一覧。公開の切替は
 * タイムラインのシフトバーにある目玉と同じ行単位フラグへの二つ目の入口で、表示は常に同じ状態を映す。
 */
export function ShiftDayList({
  shifts,
  casts,
  publishing,
  attendedShiftIds,
  attendanceLoading,
  onChangePublication,
  onDelete,
}: ShiftDayListProps) {
  const label = (s: ShiftResponse) => shiftLabel(s, casts);
  const attended = (s: ShiftResponse) => s.id !== undefined && attendedShiftIds.has(s.id);

  const confirmed = shifts.filter(s => s.status === 'CONFIRMED');
  const tentative = shifts.filter(s => s.status !== 'CONFIRMED');
  const hidden = confirmed.filter(isUnpublished);
  const shown = confirmed.filter(s => !isUnpublished(s));
  // 実績の有無は確定・仮を問わない。中性化の手順（未確定へ戻す）が産む姿がまさに
  // 「仮シフト + 未取消の実績」なので、確定の側にだけ門を置くと手順どおり操作した行に口が戻る
  const hasUndeletableShift = shifts.some(attended);

  // 既に望む状態の行は送らない。画面が古ければその行は取りこぼすが、古い画面はどちらにせよ
  // 嘘をついており、直す道は取り直しであって無用な逐行呼びではない。
  const bulk = (published: boolean) => onChangePublication(published ? hidden : shown, published);

  /**
   * 行の削除口。実績に塞がれた行は理由をその場で名乗る（代わりの手順は一覧の末尾に置く）。
   * 読めていない間は口も理由も出さない — 未知の行に「実績がある」と言わせないため。
   */
  const deleteAction = (s: ShiftResponse) => {
    if (attendanceLoading) return null;
    return attended(s) ? (
      <span className="text-xs text-muted-foreground">実績があるため削除できません</span>
    ) : (
      <Button
        type="button"
        variant="ghost"
        size="sm"
        className="text-destructive-strong"
        onClick={() => onDelete(s)}
        aria-label={`${label(s)} を削除`}
      >
        削除
      </Button>
    );
  };

  return (
    <div className="rounded-lg border bg-card shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-6 py-4">
        <div>
          <h2 className="text-lg font-semibold text-foreground">この日のシフト</h2>
          <p className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
            <span className="text-success-strong">公開 {shown.length}件</span>
            <span className="text-muted-foreground">非公開 {hidden.length}件</span>
            {tentative.length > 0 && (
              <span className="text-warning-strong">未確定 {tentative.length}件</span>
            )}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={publishing || hidden.length === 0}
            onClick={() => bulk(true)}
          >
            <EyeIcon />
            全て公開
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={publishing || shown.length === 0}
            onClick={() => bulk(false)}
          >
            <EyeOffIcon />
            全て非公開
          </Button>
        </div>
      </div>

      <ul className="divide-y">
        {confirmed.map(s => (
          <li key={s.id} className="flex flex-wrap items-center justify-between gap-3 px-6 py-3">
            <span className="min-w-0 flex-1 truncate text-sm text-foreground">{label(s)}</span>
            <span className="flex shrink-0 items-center gap-3">
              <span className="flex items-center gap-2">
                <span className="text-xs text-muted-foreground">
                  {isUnpublished(s) ? '非公開' : '公開'}
                </span>
                <Switch
                  checked={!isUnpublished(s)}
                  disabled={publishing}
                  aria-label={`${label(s)} を公開する`}
                  onCheckedChange={next => onChangePublication([s], next)}
                />
              </span>
              {deleteAction(s)}
            </span>
          </li>
        ))}
        {/* 仮シフトは公開の操作面を持たない（TENTATIVE はフラグ値に関わらず店外へ出ない）。
            削除は確定と同じく受ける — ここに口が無いと未確定のシフトを消す手段が画面から消える */}
        {tentative.map(s => (
          <li key={s.id} className="flex flex-wrap items-center justify-between gap-3 px-6 py-3">
            <span className="min-w-0 flex-1 truncate text-sm text-muted-foreground">
              {label(s)}
            </span>
            <span className="flex shrink-0 items-center gap-3">
              <span className="text-xs text-muted-foreground">確定すると公開できます</span>
              {deleteAction(s)}
            </span>
          </li>
        ))}
      </ul>

      {hasUndeletableShift && (
        <p className="border-t px-6 py-2 text-xs text-muted-foreground">
          当日実績が付いたシフトは、実績を取り消しても削除できません。ステータスを未確定に戻して無効にします。
        </p>
      )}
    </div>
  );
}
