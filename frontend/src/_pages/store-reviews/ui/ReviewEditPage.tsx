'use client';
import Link from 'next/link';
import { useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import {
  MePending,
  hasPermission,
  useCurrentStoreId,
  useMe,
  useStoreContext,
} from '@/entities/user';
import { reviewApi } from '@/entities/content/review';
import { storePath, useResource } from '@/shared/lib';
import { Button, RegionError } from '@/shared/ui';
import { useReviewWrite } from '../lib/useReviewWrite';
import { ReviewMissing } from './ReviewDetail';
import { ReviewEditor } from './ReviewEditor';
import { ReviewWriteFeedback } from './ReviewWriteFeedback';
import { accessError } from './reviewErrors';

export function ReviewEditPage() {
  const id = useParams().id as string;
  const { me } = useMe();
  const storeId = useStoreContext().currentStoreId;
  if (me === null) return <MePending />;
  if (
    me.user_type !== 'STAFF' ||
    !hasPermission(me, 'REVIEW_VIEW') ||
    !hasPermission(me, 'REVIEW_MANAGE')
  )
    return <div role="alert">口コミの訂正権限がありません</div>;
  return <Edit key={`${storeId}:${id}`} id={id} canOrder={hasPermission(me, 'ORDER_MANAGE')} />;
}

/** 訂正再受付は旧記録を取り下げて新しい口コミを作るので、成功後は訂正先の詳細へ移る。 */
function Edit({ id, canOrder }: { id: string; canOrder: boolean }) {
  const storeId = useCurrentStoreId();
  const router = useRouter();
  const [access, setAccess] = useState<string | null>(null);
  const detail = useResource(() => {
    setAccess(null);
    return reviewApi.get(id).catch(error => {
      setAccess(accessError(error)?.replace('操作', '閲覧') ?? null);
      throw error;
    });
  }, [id]);
  const write = useReviewWrite(review =>
    router.replace(storePath(storeId, `/reviews/${review.id}`))
  );
  const detailHref = storePath(storeId, `/reviews/${id}`);
  const row = detail.data;
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <h1 className="text-2xl font-bold text-foreground">訂正再受付</h1>
        <Button variant="outline" render={<Link href={detailHref} />}>
          詳細へ戻る
        </Button>
      </div>
      <ReviewWriteFeedback write={write} onReload={() => void detail.reload()} />
      {write.missing || detail.failure === 'notFound' ? (
        <ReviewMissing />
      ) : detail.failure !== null ? (
        <RegionError
          message={access ?? '口コミを取得できませんでした'}
          onRetry={() => void detail.reload()}
        />
      ) : detail.isLoading || !row ? (
        <p>読み込み中...</p>
      ) : row.superseded_by_id ? (
        <RegionError
          message="この口コミは訂正済みです"
          fallback={{ href: detailHref, label: '詳細へ戻る' }}
        />
      ) : (
        !write.denied &&
        !write.conflict && (
          <section className="space-y-4 rounded-xl border bg-card p-6 text-card-foreground shadow-sm">
            <ReviewEditor
              key={row.version}
              editor={{ action: 'CORRECT', row }}
              canOrder={canOrder}
              busy={write.blocked}
              onSubmit={write.submit}
            />
          </section>
        )
      )}
    </div>
  );
}
