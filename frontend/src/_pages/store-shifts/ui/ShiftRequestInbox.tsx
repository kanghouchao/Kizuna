'use client';

import { EyeOffIcon } from 'lucide-react';
import { useState } from 'react';
import { notify } from '@/shared/notify';
import { CastResponse } from '@/entities/cast';
import { shiftApi } from '@/entities/shift';
import { useResource } from '@/shared/lib';
import { Badge, Button, RegionError } from '@/shared/ui';

interface ShiftRequestInboxProps {
  casts: CastResponse[];
  /** 承認成功後に呼ばれる（シフトが新規作成・更新されるため、シフト一覧の再取得に使う）。 */
  onApproved: () => void;
}

/**
 * 出勤希望 inbox。受付済み(PENDING)のみを表示する — 処理済みの閲覧は cast 側履歴の責務。 新規希望（NEW）と確定シフトへの変更申請（CHANGE）を種別表示し、変更申請は現行→申請の日時を併記する。
 */
export function ShiftRequestInbox({ casts, onApproved }: ShiftRequestInboxProps) {
  const {
    data,
    isLoading: loading,
    failure,
    reload: load,
  } = useResource(() => shiftApi.listShiftRequests({ status: 'PENDING' }));
  const requests = data ?? [];
  const [processingId, setProcessingId] = useState<string | null>(null);

  // castId が未指定のとき、id を持たないキャストと undefined 同士で一致してしまうのを防ぐ
  const castName = (castId: string | undefined) =>
    (castId === undefined ? undefined : casts.find(c => c.id === castId)?.name) ?? castId;

  /** 承認する。published を渡すのは新規希望だけ — 変更申請の承認で指定すると後端が撥ねる。 */
  const approve = async (id: string, published?: boolean) => {
    setProcessingId(id);
    try {
      await shiftApi.approveShiftRequest(id, published);
      notify.success(
        published === false ? '出勤希望を非公開で承認しました' : '出勤希望を承認しました'
      );
      void load();
      onApproved();
    } catch {
      notify.error('承認に失敗しました');
    } finally {
      setProcessingId(null);
    }
  };

  const decline = async (id: string) => {
    setProcessingId(id);
    try {
      await shiftApi.declineShiftRequest(id);
      notify.success('出勤希望を却下しました');
      void load();
    } catch {
      notify.error('却下に失敗しました');
    } finally {
      setProcessingId(null);
    }
  };

  if (loading) {
    return <p className="text-sm text-muted-foreground">読み込み中...</p>;
  }
  // 読めなかった一覧を残すと「受付中の出勤希望はありません」に化け、未処理の希望を見落とす
  if (failure !== null) {
    return <RegionError message="出勤希望の取得に失敗しました" onRetry={() => void load()} />;
  }
  if (requests.length === 0) {
    return <p className="text-sm text-muted-foreground">受付中の出勤希望はありません</p>;
  }

  return (
    <ul className="space-y-3">
      {requests.map(request => {
        const isChange = request.type === 'CHANGE';
        // サーバ側で必ず拒否される申請には承認操作を出さない（謝絶・辞退のみ）。理由は種別で異なり、
        // 新規希望は目標営業日の終了だけ、変更申請はそれに対象シフトの削除・編集が重なる。
        const unapprovable = request.approvable === false;
        return (
          <li
            key={request.id}
            className="flex items-center justify-between rounded-[10px] border bg-card p-4 shadow-sm"
          >
            <div>
              <p className="flex items-center gap-2 text-sm font-medium text-foreground">
                {castName(request.cast_id)}
                <Badge
                  variant="outline"
                  className={
                    isChange
                      ? 'border-transparent bg-primary/10 text-primary-strong'
                      : 'border-transparent bg-muted text-foreground'
                  }
                >
                  {isChange ? '変更申請' : '新規希望'}
                </Badge>
              </p>
              {isChange && request.current_work_date && (
                <p className="text-xs text-muted-foreground">
                  現行: {request.current_work_date} {request.current_start_time?.slice(0, 5)}–
                  {request.current_end_time?.slice(0, 5)}
                </p>
              )}
              <p className="text-sm text-muted-foreground">
                {isChange ? '申請: ' : ''}
                {request.work_date} {request.start_time?.slice(0, 5)}–
                {request.end_time?.slice(0, 5)}
              </p>
              {request.note && <p className="mt-1 text-xs text-muted-foreground">{request.note}</p>}
              {unapprovable && (
                <p className="mt-1 text-xs text-destructive-strong">
                  {isChange
                    ? '対象の営業日が終了したか、対象のシフトが削除または変更されたため承認できません（謝絶のみ可能）'
                    : '対象の営業日が終了したため承認できません（辞退のみ可能）'}
                </p>
              )}
            </div>
            <div className="flex gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => decline(request.id ?? '')}
                disabled={processingId === request.id}
              >
                {isChange ? '謝絶' : '辞退'}
              </Button>
              {/* 出生の口が非公開を指定できる理由は lib/publication.ts。既存シフトの公開可否を
                  動かさない変更申請には出さない */}
              {!unapprovable && !isChange && (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => approve(request.id ?? '', false)}
                  disabled={processingId === request.id}
                >
                  <EyeOffIcon />
                  非公開で承認
                </Button>
              )}
              {!unapprovable && (
                <Button
                  type="button"
                  size="sm"
                  onClick={() => approve(request.id ?? '')}
                  disabled={processingId === request.id}
                >
                  {isChange ? '承認してシフト更新' : '承認'}
                </Button>
              )}
            </div>
          </li>
        );
      })}
    </ul>
  );
}
