'use client';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import {
  MePending,
  hasPermission,
  useCurrentStoreId,
  useMe,
  useStoreContext,
} from '@/entities/user';
import { storePath } from '@/shared/lib';
import { Button } from '@/shared/ui';
import { useReviewWrite } from '../lib/useReviewWrite';
import { ReviewEditor } from './ReviewEditor';
import { ReviewWriteFeedback } from './ReviewWriteFeedback';
import { DeniedPage } from '@/widgets/admin-shell';

export function ReviewCreatePage() {
  const { me } = useMe();
  const storeId = useStoreContext().currentStoreId;
  if (me === null) return <MePending />;
  if (
    me.user_type !== 'STAFF' ||
    !hasPermission(me, 'REVIEW_VIEW') ||
    !hasPermission(me, 'REVIEW_MANAGE')
  )
    return <DeniedPage />;
  return <Create key={storeId} canOrder={hasPermission(me, 'ORDER_MANAGE')} />;
}

function Create({ canOrder }: { canOrder: boolean }) {
  const storeId = useCurrentStoreId();
  const router = useRouter();
  const write = useReviewWrite(review =>
    router.replace(storePath(storeId, `/reviews/${review.id}`))
  );
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <h1 className="text-2xl font-bold text-foreground">口コミを受付</h1>
        <Button variant="outline" render={<Link href={storePath(storeId, '/reviews')} />}>
          一覧へ戻る
        </Button>
      </div>
      <ReviewWriteFeedback write={write} />
      {!write.denied && (
        <section className="space-y-4 rounded-xl border bg-card p-6 text-card-foreground shadow-sm">
          <ReviewEditor
            editor={{ action: 'CREATE' }}
            canOrder={canOrder}
            busy={write.blocked}
            onSubmit={write.submit}
          />
        </section>
      )}
    </div>
  );
}
