'use client';

import { useState } from 'react';
import {
  CastEnrollmentStatus,
  CastEnrollmentStatusResponse,
  CastResponse,
  castApi,
} from '@/entities/cast';
import { useCursorList } from '@/shared/lib';
import { Button, ConfirmDialog, RegionError } from '@/shared/ui';
import { notify } from '@/shared/notify';

const statusLabels: Record<CastEnrollmentStatus, string> = {
  ENROLLED: '在籍中',
  SUSPENDED: '在籍停止',
  WITHDRAWN: '退店',
};

export function CastEnrollmentPanel({
  cast,
  onChanged,
}: {
  cast: CastResponse;
  onChanged: (result: CastEnrollmentStatusResponse) => void;
}) {
  const [status, setStatus] = useState(cast.status);
  const [endedAt, setEndedAt] = useState(cast.ended_at);
  const [busy, setBusy] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const history = useCursorList(cursor => castApi.statusHistories(cast.id!, cursor));
  const snapshots = useCursorList(cursor => castApi.snapshots(cast.id!, cursor));

  async function change(action: 'suspend' | 'resume' | 'withdraw') {
    if (busy || !cast.id) return;
    setBusy(true);
    try {
      const result = await castApi[action](cast.id);
      setStatus(result.status);
      setEndedAt(result.ended_at);
      history.reload();
      onChanged(result);
      notify.success('在籍状態を更新しました');
    } catch {
      notify.error('在籍状態の更新に失敗しました');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-6">
      <section aria-label="在籍操作" className="space-y-3 rounded-xl border bg-card p-6">
        <h2 className="text-lg font-semibold">在籍</h2>
        <p>在籍状態: {status ? statusLabels[status] : '不明'}</p>
        {endedAt && <p>退店日時: {new Date(endedAt).toLocaleString('ja-JP')}</p>}
        <div className="flex gap-3">
          {status === 'ENROLLED' && (
            <Button
              type="button"
              variant="outline"
              disabled={busy}
              onClick={() => void change('suspend')}
            >
              停止する
            </Button>
          )}
          {status === 'SUSPENDED' && (
            <Button
              type="button"
              variant="outline"
              disabled={busy}
              onClick={() => void change('resume')}
            >
              再開する
            </Button>
          )}
          {status && status !== 'WITHDRAWN' && (
            <Button
              type="button"
              variant="destructive"
              disabled={busy}
              onClick={() => setConfirming(true)}
            >
              退店する
            </Button>
          )}
        </div>
      </section>
      <section aria-label="在籍履歴" className="space-y-3 rounded-xl border bg-card p-6">
        <details className="space-y-3">
          <summary className="cursor-pointer rounded-sm text-lg font-semibold focus-visible:outline-2 focus-visible:outline-ring">
            <h2 className="inline">在籍履歴</h2>
          </summary>
          {history.failed ? (
            <RegionError message="在籍履歴の取得に失敗しました" onRetry={history.reload} />
          ) : (
            <>
              {!history.isLoading && history.rows.length === 0 && (
                <p className="text-muted-foreground">在籍履歴はありません</p>
              )}
              <ul className="space-y-2">
                {history.rows.map(row => (
                  <li key={row.id} className="rounded-md border p-3">
                    <p>
                      {row.previous_status
                        ? `${statusLabels[row.previous_status]} → ${statusLabels[row.new_status]}`
                        : `入店（${statusLabels[row.new_status]}）`}
                    </p>
                    <p className="text-sm text-muted-foreground">
                      {new Date(row.recorded_at).toLocaleString('ja-JP')} · 実行者 ID:{' '}
                      {row.actor_id}
                    </p>
                  </li>
                ))}
              </ul>
              {history.isLoading && <p role="status">読み込み中...</p>}
              {history.hasMore && (
                <Button
                  type="button"
                  variant="outline"
                  disabled={history.isLoading}
                  onClick={history.loadMore}
                >
                  在籍履歴をさらに読み込む
                </Button>
              )}
            </>
          )}
        </details>
      </section>
      <section aria-label="内部情報の編集履歴" className="space-y-3 rounded-xl border bg-card p-6">
        <details className="space-y-3">
          <summary className="cursor-pointer rounded-sm text-lg font-semibold focus-visible:outline-2 focus-visible:outline-ring">
            <h2 className="inline">内部情報の編集履歴</h2>
          </summary>
          <p className="text-sm text-muted-foreground">各変更の直前の値を表示します。</p>
          {snapshots.failed ? (
            <RegionError message="内部情報履歴の取得に失敗しました" onRetry={snapshots.reload} />
          ) : (
            <>
              {!snapshots.isLoading && snapshots.rows.length === 0 && (
                <p className="text-muted-foreground">内部情報の編集履歴はありません</p>
              )}
              <ul className="space-y-2">
                {snapshots.rows.map(row => (
                  <li key={row.id} className="rounded-md border p-3">
                    <p className="text-sm text-muted-foreground">
                      {new Date(row.recorded_at).toLocaleString('ja-JP')} · 実行者 ID:{' '}
                      {row.actor_id}
                    </p>
                    {Object.keys(row.custom_fields).length === 0 ? (
                      <p>未入力</p>
                    ) : (
                      <dl>
                        {Object.entries(row.custom_fields).map(([key, value]) => (
                          <div key={key} className="grid grid-cols-2 gap-3">
                            <dt className="break-all">{key}</dt>
                            <dd className="whitespace-pre-wrap break-all">{value ?? '未入力'}</dd>
                          </div>
                        ))}
                      </dl>
                    )}
                  </li>
                ))}
              </ul>
              {snapshots.isLoading && <p role="status">読み込み中...</p>}
              {snapshots.hasMore && (
                <Button
                  type="button"
                  variant="outline"
                  disabled={snapshots.isLoading}
                  onClick={snapshots.loadMore}
                >
                  内部情報履歴をさらに読み込む
                </Button>
              )}
            </>
          )}
        </details>
      </section>
      <ConfirmDialog
        open={confirming}
        title="退店しますか？"
        description="退店は元に戻せません。再入店には新しい在籍と招待が必要です。"
        confirmLabel="退店する"
        onConfirm={() => void change('withdraw')}
        onClose={() => setConfirming(false)}
      />
    </div>
  );
}
