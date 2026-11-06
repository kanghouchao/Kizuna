import { Button } from '@/shared/ui';
import type { useReviewWrite } from '../lib/useReviewWrite';

export function ReviewWriteFeedback({
  write,
  onReload,
}: {
  write: ReturnType<typeof useReviewWrite>;
  /** 409 の後に口コミを読み直す。口コミを表示しない画面（一覧・受付）では案内を消すだけにする。 */
  onReload?: () => void;
}) {
  return (
    <>
      {write.uncertain && (
        <div role="alert" className="rounded-lg border p-4 space-y-3">
          <p>結果が確認できません。同じ要求で結果を確認するまで、新しい操作はできません。</p>
          <Button variant="outline" disabled={write.busy} onClick={() => void write.replay()}>
            同じ要求で結果を確認
          </Button>
        </div>
      )}
      {write.denied && (
        <div role="alert">{write.denied}。権限とログイン状態を確認してください。</div>
      )}
      {write.conflict && (
        <div role="alert" className="rounded-lg border p-4 space-y-3">
          <p>口コミが更新されています。最新状態を確認してから、改めて操作を選んでください。</p>
          <Button
            variant="outline"
            onClick={() => {
              write.clearConflict();
              onReload?.();
            }}
          >
            最新状態を確認して戻る
          </Button>
        </div>
      )}
    </>
  );
}
