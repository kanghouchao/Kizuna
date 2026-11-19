import { TriangleAlertIcon } from 'lucide-react';
import { Button } from '@/shared/ui';

export const STALE_MESSAGE =
  'この受注は他の操作で更新されたため、このままでは操作を続けられません。入力中の内容は画面に残しています';
export const LOAD_LATEST_LABEL = '入力を破棄して最新の内容を読み込む';
export const LATEST_LOADED_MESSAGE = '最新の内容を読み込みました。入力は置き換わっています';
export const INPUT_KEPT_MESSAGE =
  '最新の状態を取得しました。入力は残っています。内容を確認してやり直してください';

/** 押せなくなった操作のそばに置く。通知は消えるので、止めている理由と読み込みの口をここに残す。 */
export function OrderStaleNotice({ busy, onLoad }: { busy: boolean; onLoad: () => void }) {
  return (
    <div
      role="alert"
      className="flex flex-wrap items-center gap-3 rounded-lg bg-warning/10 p-4 text-warning-strong"
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
