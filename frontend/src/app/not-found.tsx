'use client';

import Link from 'next/link';

export default function NotFound() {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-linear-to-br from-blue-50 to-indigo-100 px-6 text-center">
      <div className="max-w-md">
        <h1 className="text-3xl font-bold text-indigo-700">ページが見つかりませんでした</h1>
        <p className="mt-4 text-gray-600">
          アクセスしたリンクが無効になっているか、移動された可能性があります。ブラウザのアドレスが正しいかをご確認のうえ、以下のリンクから操作を続けてください。
        </p>
        <div className="mt-8 flex flex-col space-y-3">
          <Link
            href="/"
            className="w-full rounded-md bg-indigo-600 px-4 py-2 text-white hover:bg-indigo-700"
          >
            ホームへ戻る
          </Link>
          <Link
            href="/login"
            className="w-full rounded-md border border-indigo-200 px-4 py-2 text-indigo-700 hover:bg-white"
          >
            ログイン画面を開く
          </Link>
        </div>
        <p className="mt-6 text-sm text-gray-500">
          お困りの場合はサポート担当までお問い合わせください。
        </p>
      </div>
    </div>
  );
}
