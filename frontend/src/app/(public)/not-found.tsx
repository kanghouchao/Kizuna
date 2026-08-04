'use client';

import Link from 'next/link';
import { Button } from '@/shared/ui';

/**
 * 未一致 URL の受け皿。管理コンソール側の typo もここに落ちるため、店舗サイトでも認証画面でもなく
 * admin のトークン語彙で書く。(public) の根 layout はテーマ配線を持たない（テーマは (admin) 側だけの
 * 関心事）ので、ここでのトークンは常に light 値に解決される。dark 表示が無いのは構造上の帰結。
 */
export default function NotFound() {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-background px-6 text-center">
      <div className="max-w-md">
        <h1 className="text-2xl font-bold text-foreground">ページが見つかりませんでした</h1>
        <p className="mt-4 text-muted-foreground">
          アクセスしたリンクが無効になっているか、移動された可能性があります。ブラウザのアドレスが正しいかをご確認のうえ、以下のリンクから操作を続けてください。
        </p>
        <div className="mt-8 flex flex-col space-y-3">
          <Button asChild className="w-full">
            <Link href="/">ホームへ戻る</Link>
          </Button>
          <Button asChild variant="outline" className="w-full">
            <Link href="/platform/login">ログイン画面を開く</Link>
          </Button>
        </div>
        <p className="mt-6 text-sm text-muted-foreground">
          お困りの場合はサポート担当までお問い合わせください。
        </p>
      </div>
    </div>
  );
}
