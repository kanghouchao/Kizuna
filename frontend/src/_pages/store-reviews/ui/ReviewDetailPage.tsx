'use client';
import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { MePending, hasPermission, useMe, useStoreContext } from '@/entities/user';
import { Review } from '@/entities/content/review';
import { storePath } from '@/shared/lib';
import { Button } from '@/shared/ui';
import { useReviewWrite } from '../lib/useReviewWrite';
import { DetailEditor, ReviewDetail, ReviewMissing } from './ReviewDetail';
import { ReviewEditor, editorTitle } from './ReviewEditor';
import { ReviewWriteFeedback } from './ReviewWriteFeedback';

export function ReviewDetailPage() {
  const id = useParams().id as string;
  const { me } = useMe();
  const storeId = useStoreContext().currentStoreId;
  if (me === null) return <MePending />;
  if (me.user_type !== 'STAFF' || !hasPermission(me, 'REVIEW_VIEW'))
    return <div role="alert">口コミの閲覧権限がありません</div>;
  return (
    <Detail
      key={`${storeId}:${id}`}
      id={id}
      canModerate={hasPermission(me, 'REVIEW_MODERATE')}
      canManage={hasPermission(me, 'REVIEW_MANAGE')}
      canOrder={hasPermission(me, 'ORDER_MANAGE')}
    />
  );
}

/** 審査・公開許可・公開設定・取り下げは、この画面の節で入力して ConfirmDialog で確定する。 */
function Detail({
  id,
  canModerate,
  canManage,
  canOrder,
}: {
  id: string;
  canModerate: boolean;
  canManage: boolean;
  canOrder: boolean;
}) {
  const router = useRouter();
  const [editor, setEditor] = useState<DetailEditor | null>(null);
  const [latest, setLatest] = useState<Review | undefined>();
  const [revision, setRevision] = useState(0);
  const operationHeading = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    if (editor) operationHeading.current?.focus();
  }, [editor]);
  const write = useReviewWrite(review => {
    setEditor(null);
    // 同じ要求の再確認は別の口コミ（受付・訂正先）の結果を返しうる
    if (review.id === id) setLatest(review);
    else router.push(storePath(`/reviews/${review.id}`));
  });
  const settled = !write.denied && !write.conflict;
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-foreground">口コミの内容・履歴</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            受付本文と判断、公開許可の記録を確認します。
          </p>
        </div>
        <Button variant="outline" render={<Link href={storePath('/reviews')} />}>
          一覧へ戻る
        </Button>
      </div>
      <ReviewWriteFeedback
        write={write}
        onReload={() => {
          setEditor(null);
          setLatest(undefined);
          setRevision(value => value + 1);
        }}
      />
      {write.missing ? (
        <ReviewMissing />
      ) : (
        <>
          {editor && settled && (
            <section
              aria-labelledby="review-operation"
              className="space-y-4 rounded-xl border bg-card p-6 text-card-foreground shadow-sm"
            >
              <h2
                id="review-operation"
                tabIndex={-1}
                ref={operationHeading}
                className="text-lg font-semibold outline-none"
              >
                {editorTitle(editor)}
              </h2>
              <ReviewEditor
                key={`${editor.action}-${editor.row.version}`}
                editor={editor}
                canOrder={canOrder}
                busy={write.blocked}
                onSubmit={write.submit}
                onCancel={() => setEditor(null)}
              />
            </section>
          )}
          <ReviewDetail
            key={revision}
            id={id}
            initial={latest}
            canModerate={canModerate && settled}
            canManage={canManage && settled}
            busy={write.blocked}
            onEdit={setEditor}
          />
        </>
      )}
    </div>
  );
}
