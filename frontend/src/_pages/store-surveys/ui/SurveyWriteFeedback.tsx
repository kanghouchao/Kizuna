import { ReactNode } from 'react';
import { Button } from '@/shared/ui';
import type { SurveyWrite } from '../lib/useSurveyWrite';

/** 結果不明の再確認と、409・404 の後に入力を保ったまま最新状態を読み直す入口。 */
export function SurveyWriteFeedback({
  write,
  busy,
  onRefresh,
  refreshError,
  children,
}: {
  write: SurveyWrite;
  busy: boolean;
  onRefresh: () => void;
  refreshError?: string | null;
  children?: ReactNode;
}) {
  return (
    <>
      {refreshError && <p role="alert">{refreshError}</p>}
      {write.uncertain && (
        <div role="alert" className="rounded-lg border p-4 space-y-3">
          <p>結果が確認できません。同じ要求で結果を確認するまで、新しい操作はできません。</p>
          <Button variant="outline" disabled={busy} onClick={() => void write.replay()}>
            同じ要求で結果を確認
          </Button>
        </div>
      )}
      {(write.conflict || write.missing) && (
        <div role="alert" className="rounded-lg border p-4 space-y-3">
          <p>
            {write.missing
              ? '対象が見つかりません。入力は保持しています。'
              : '状態が更新されています。入力を保持したまま最新状態を確認してください。'}
          </p>
          <Button variant="outline" disabled={busy} onClick={onRefresh}>
            最新状態を確認
          </Button>
        </div>
      )}
      {children}
    </>
  );
}
