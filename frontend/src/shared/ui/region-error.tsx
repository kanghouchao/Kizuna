'use client';

import Link from 'next/link';
import { cn } from '@/shared/lib/utils';
import { Button } from './button';

type RegionErrorProps = {
  /** 読めなかったものを 1 行で述べる文言。 */
  message: string;
  /** 外側の配置。場面ごとの差はここだけに出る。 */
  className?: string;
} & (
  | {
      /** 押すと領域を読み直す。渡さなければ再試行ボタンは出ない。 */
      onRetry: () => void;
      fallback?: never;
    }
  | {
      onRetry?: never;
      /** 再試行しても成功しない 404 の代替導線。 */
      fallback: { href: string; label: string };
    }
);

/**
 * 取得に失敗した領域が、自分の失敗を自分で名乗る姿。一覧・選択肢・詳細頁・附属区画の
 * すべてがこの 1 行 ＋ ボタン 1 つに収斂する（DESIGN.md「領域内エラー態」）。
 *
 * 逃げ道はちょうど 1 つ。再試行と代替導線を独立した任意 props にすると「どちらも無い」
 * ＝出口の無いエラーと「どちらもある」＝二重の導線が書けてしまうため、型で畳んである。
 *
 * 容器の role="alert" は装飾ではない。失敗は領域がマウントされた後に起き、かつこの姿は
 * toast を排しているため、これが無いと行が消えたことも再試行が現れたことも読み上げ利用者に
 * 届かない。暗黙の aria-atomic で容器が丸ごと読まれるので、ボタンのラベルも文言と共に届く。
 * 焦点は移さない — 対象にはフォーム内の選択肢も含まれ、打鍵中の欄から焦点を奪う方が害が大きい。
 */
export function RegionError(props: RegionErrorProps) {
  const { message, className } = props;

  return (
    <div role="alert" className={cn('flex items-center gap-3', className)}>
      <p className="text-sm text-destructive-strong">{message}</p>
      {props.onRetry ? (
        // Button は素の <button> を描くため既定は submit。フォーム内の選択肢が失敗した場面で
        // 送信してしまわないよう type を明示する。
        <Button type="button" variant="outline" size="sm" onClick={props.onRetry}>
          再試行
        </Button>
      ) : (
        <Button render={<Link href={props.fallback.href} />} variant="outline" size="sm">
          {props.fallback.label}
        </Button>
      )}
    </div>
  );
}
