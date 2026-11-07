import type { ReactNode } from 'react';

interface EmptyStateProps {
  /** 何が無いか（例: 「まだ予約はありません」）。 */
  message: string;
  /** 次の操作。 */
  action?: ReactNode;
}

/** 空の一覧・区画。何が無いかと次の操作を出す。 */
export function EmptyState({ message, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed p-6 text-center">
      <p className="text-foreground">{message}</p>
      {action}
    </div>
  );
}
