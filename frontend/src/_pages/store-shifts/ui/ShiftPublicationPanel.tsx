'use client';

import { EyeIcon, EyeOffIcon } from 'lucide-react';
import { CastResponse } from '@/entities/cast';
import { ShiftResponse } from '@/entities/shift';
import { Button, Switch } from '@/shared/ui';
import { shiftLabel } from '../lib/labels';
import { isUnpublished } from '../lib/publication';

interface ShiftPublicationPanelProps {
  shifts: ShiftResponse[];
  casts: CastResponse[];
  publishing: boolean;
  /** 公開可否の変更。一括もここへ複数件で入る（一括 API は無い — ADR 0015）。 */
  onChangePublication: (targets: ShiftResponse[], published: boolean) => void;
}

/**
 * 公式サイトの公開状態を俯瞰・一括操作するパネル。タイムラインのシフトバーにある目玉と
 * 同じ行単位フラグへの二つ目の入口で、表示は常に同じ状態を映す。
 */
export function ShiftPublicationPanel({
  shifts,
  casts,
  publishing,
  onChangePublication,
}: ShiftPublicationPanelProps) {
  const label = (s: ShiftResponse) => shiftLabel(s, casts);

  const confirmed = shifts.filter(s => s.status === 'CONFIRMED');
  const tentative = shifts.filter(s => s.status !== 'CONFIRMED');
  const hidden = confirmed.filter(isUnpublished);
  const shown = confirmed.filter(s => !isUnpublished(s));

  // 既に望む状態の行は送らない。画面が古ければその行は取りこぼすが、古い画面はどちらにせよ
  // 嘘をついており、直す道は取り直しであって無用な逐行呼びではない。
  const bulk = (published: boolean) => onChangePublication(published ? hidden : shown, published);

  return (
    <div className="rounded-lg border bg-card shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-6 py-4">
        <div>
          <h2 className="text-lg font-semibold text-foreground">公式サイトの公開状態</h2>
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
          <li key={s.id} className="flex items-center justify-between gap-3 px-6 py-3">
            <span className="min-w-0 truncate text-sm text-foreground">{label(s)}</span>
            <span className="flex shrink-0 items-center gap-2">
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
          </li>
        ))}
        {/* 仮シフトは公開の操作面を持たない（TENTATIVE はフラグ値に関わらず店外へ出ない） */}
        {tentative.map(s => (
          <li key={s.id} className="flex items-center justify-between gap-3 px-6 py-3">
            <span className="min-w-0 truncate text-sm text-muted-foreground">{label(s)}</span>
            <span className="shrink-0 text-xs text-muted-foreground">確定すると公開できます</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
