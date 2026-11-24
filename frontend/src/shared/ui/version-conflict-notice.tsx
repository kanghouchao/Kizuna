'use client';

import { useEffect, useRef } from 'react';
import { TriangleAlertIcon } from 'lucide-react';
import { cn } from '@/shared/lib/utils';
import { Button } from './button';

export const LOAD_LATEST_LABEL = '入力を破棄して最新の内容を読み込む';

interface VersionConflictNoticeProps {
  /** 書き込み中は読み込みを押せない。 */
  busy: boolean;
  /** 押すと最新の内容を読み込む。入力はここで初めて置き換わる。 */
  onLoad: () => void;
  /** 同じ競合で案内が複数出る画面は false。後から描かれた案内が、押した場所から視点を奪う。 */
  reveal?: boolean;
  className?: string;
}

/**
 * 版の競合で押せなくなった操作のそばに置く案内。通知は消えるので、止めている理由と読み込みの口を
 * 画面に残す。押した場所は長いフォームやダイアログの末尾にあるので、出たときに一度だけ見える位置へ
 * 送る（描き直しのたびに送ると、入力中の利用者を引き戻す）。
 */
export function VersionConflictNotice({
  busy,
  onLoad,
  reveal = true,
  className,
}: VersionConflictNoticeProps) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (reveal) ref.current?.scrollIntoView({ block: 'nearest' });
    // 出たときの一度だけ送る
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  return (
    <div
      ref={ref}
      role="alert"
      className={cn(
        'flex flex-wrap items-center gap-3 rounded-lg bg-warning/10 p-4 text-warning-strong',
        className
      )}
    >
      <TriangleAlertIcon aria-hidden className="size-5 shrink-0" />
      <p className="min-w-0 flex-1 basis-64 text-sm">
        他の操作で更新されたため、操作を止めています。最新の内容を読み込むと、入力中の内容は置き換わります。
      </p>
      <Button type="button" disabled={busy} onClick={onLoad}>
        {LOAD_LATEST_LABEL}
      </Button>
    </div>
  );
}
