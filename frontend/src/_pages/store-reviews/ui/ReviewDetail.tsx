import { useRef, useState } from 'react';
import { Review, reviewApi } from '@/entities/review';
import { useResource } from '@/shared/lib';
import { Badge, Button, RegionError } from '@/shared/ui';
import { statuses, permissions, viaLabels, basisLabels, time } from './labels';
import { ReviewHistory } from './ReviewHistory';
import { accessError } from './reviewErrors';
import { Editor } from './ReviewEditor';
export function ReviewDetail({
  id,
  initial,
  canModerate,
  canManage,
  busy,
  onEdit,
  onMissing,
}: {
  id: string;
  initial?: Review;
  canModerate: boolean;
  canManage: boolean;
  busy: boolean;
  onEdit: (editor: Editor) => void;
  onMissing: () => void;
}) {
  const [access, setAccess] = useState<string | null>(null);
  const consumed = useRef<Review | undefined>(undefined);
  const detail = useResource(() => {
    if (initial && consumed.current !== initial) {
      consumed.current = initial;
      return Promise.resolve(initial);
    }
    setAccess(null);
    return reviewApi.get(id).catch(error => {
      setAccess(accessError(error)?.replace('操作', '閲覧') ?? null);
      throw error;
    });
  }, [id, initial]);
  if (detail.isLoading) return <p>読み込み中...</p>;
  if (detail.failure === 'notFound')
    return (
      <div role="alert">
        <p>口コミが見つかりません</p>
        <Button variant="outline" onClick={onMissing}>
          一覧へ戻る
        </Button>
      </div>
    );
  if (detail.failure !== null)
    return (
      <RegionError
        message={access ?? '口コミを取得できませんでした'}
        onRetry={() => void detail.reload()}
      />
    );
  if (!detail.data) return <p>口コミを読み込み中...</p>;
  const row = detail.data;
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap gap-2">
        <Badge variant="outline">スタッフによる記録</Badge>
        <Badge variant="outline">{statuses[row.status]}</Badge>
        <Badge variant="outline">{permissions[row.permission_status]}</Badge>
      </div>
      <p className="text-warning-strong">公開連携は未設定</p>
      <p className="text-sm text-muted-foreground">
        内部承認と公開許可を別々に管理します。
        {row.publication_eligible
          ? '公開候補の条件を満たしています。'
          : '公開候補の条件を満たしていません。'}
      </p>
      <dl className="grid grid-cols-[auto_1fr] gap-3 text-sm">
        <dt>表示名</dt>
        <dd className="wrap-anywhere">{row.display_name ?? '匿名'}</dd>
        <dt>本文</dt>
        <dd className="whitespace-pre-wrap wrap-anywhere">{row.body}</dd>
        <dt>受付</dt>
        <dd>
          {time(row.received_at)} / {viaLabels[row.received_via]}
        </dd>
        <dt>記録者</dt>
        <dd>{row.recorded_by.display_name}</dd>
        {row.origin_order_id && (
          <>
            <dt>関連受注</dt>
            <dd>#{row.origin_order_id}（受付時に確認）</dd>
          </>
        )}
        {row.supersedes_id && (
          <>
            <dt>訂正元</dt>
            <dd>#{row.supersedes_id}</dd>
          </>
        )}
        {row.superseded_by_id && (
          <>
            <dt>訂正先</dt>
            <dd>#{row.superseded_by_id}</dd>
          </>
        )}
      </dl>
      {row.permission && (
        <section className="space-y-3 rounded-lg border p-4">
          <h2 className="text-lg font-semibold">公開許可の記録</h2>
          <p>自店舗ウェブサイト / {basisLabels[row.permission.basis_type]}</p>
          <p>
            {time(row.permission.granted_at)} / 記録者 {row.permission.recorded_by.display_name}
          </p>
          <p className="whitespace-pre-wrap wrap-anywhere">{row.permission.evidence_note}</p>
          {row.permission.revocation && (
            <>
              <h3 className="font-semibold">撤回の記録</h3>
              <p>{time(row.permission.revocation.withdrawal_received_at)}</p>
              <p className="whitespace-pre-wrap wrap-anywhere">
                {row.permission.revocation.reason}
              </p>
            </>
          )}
        </section>
      )}
      {canModerate && row.status === 'PENDING' && (
        <div className="flex flex-wrap gap-3">
          <Button disabled={busy} onClick={() => onEdit({ action: 'APPROVE', row })}>
            内部承認
          </Button>
          <Button
            variant="outline"
            disabled={busy}
            onClick={() => onEdit({ action: 'REJECT', row })}
          >
            却下
          </Button>
        </div>
      )}
      {canManage && (
        <div className="flex flex-wrap gap-3">
          {row.permission_status === 'NOT_GRANTED' &&
            (row.status === 'PENDING' || row.status === 'APPROVED') && (
              <Button disabled={busy} onClick={() => onEdit({ action: 'GRANT', row })}>
                公開許可を記録
              </Button>
            )}
          {row.permission_status === 'GRANTED' && (
            <Button
              variant="outline"
              disabled={busy}
              onClick={() => onEdit({ action: 'REVOKE', row })}
            >
              公開許可を撤回
            </Button>
          )}
          {!row.superseded_by_id && (
            <Button
              variant="outline"
              disabled={busy}
              onClick={() => onEdit({ action: 'CORRECT', row })}
            >
              訂正再受付
            </Button>
          )}
          {row.status !== 'WITHDRAWN' && (
            <Button
              variant="outline"
              disabled={busy}
              onClick={() => onEdit({ action: 'WITHDRAW', row })}
            >
              口コミを取り下げ
            </Button>
          )}
        </div>
      )}
      <Button variant="outline" disabled={busy} onClick={() => void detail.reload()}>
        最新状態を確認
      </Button>
      <ReviewHistory key={row.version} id={id} />
    </div>
  );
}
