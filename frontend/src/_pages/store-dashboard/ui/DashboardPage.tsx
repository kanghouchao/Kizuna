'use client';

import { Card, CardDescription, CardHeader, CardTitle } from '@/shared/ui';

export default function StoreDashboard() {
  return (
    <div className="min-h-screen bg-background flex flex-col items-center justify-center">
      <div className="max-w-2xl w-full px-6 py-10 rounded-lg shadow-lg bg-card">
        <h1 className="text-3xl font-bold text-foreground mb-4 text-center">店舗ダッシュボード</h1>
        <p className="text-lg text-foreground text-center mb-6">ようこそ、someone さん！</p>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <Card>
            <CardHeader>
              <CardTitle role="heading" aria-level={2} className="text-xl">
                コンテンツ管理
              </CardTitle>
              <CardDescription>記事やページの作成・編集・公開ができます。</CardDescription>
            </CardHeader>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle role="heading" aria-level={2} className="text-xl">
                ユーザー管理
              </CardTitle>
              <CardDescription>店舗内のユーザーの追加・権限設定ができます。</CardDescription>
            </CardHeader>
          </Card>
        </div>
        <div className="mt-8 text-center text-muted-foreground text-xs">Powered by Kizuna</div>
      </div>
    </div>
  );
}
