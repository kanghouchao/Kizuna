import { useEffect, useRef } from 'react';
import { Button } from '@/shared/ui';

export const LOAD_LATEST_LABEL = '入力を破棄して最新の内容を読み込む';
export const LATEST_LOADED_MESSAGE = '最新の内容を読み込みました。入力は置き換わっています';
export const INPUT_KEPT_MESSAGE =
  '最新の状態を取得しました。入力は残っています。内容を確認してやり直してください';

/**
 * 画面の受注が古くなったことを名乗る帯。入力は操作者が読み込みを選ぶまで画面に残す
 * （自動で置き換えると、案内を読む前に入力が消える）。
 */
export function OrderStaleNotice({ busy, onLoad }: { busy: boolean; onLoad: () => void }) {
  const notice = useRef<HTMLDivElement>(null);
  // 送信の口は長いフォームの末尾にある。押せなくなった理由を、出た時に一度だけ見える位置へ運ぶ
  // 新しいブラウザは scrollIntoView が Promise を返す。効果の戻り値にしない
  useEffect(() => {
    notice.current?.scrollIntoView({ block: 'center' });
  }, []);
  return (
    <div ref={notice} role="alert" className="space-y-2">
      <p className="text-destructive-strong">
        この受注は他の操作で更新されたため、このままでは操作を続けられません。入力中の内容は画面に残しています。最新の内容を読み込むと、入力中の内容は置き換わります。
      </p>
      <Button type="button" variant="outline" disabled={busy} onClick={onLoad}>
        {LOAD_LATEST_LABEL}
      </Button>
    </div>
  );
}
