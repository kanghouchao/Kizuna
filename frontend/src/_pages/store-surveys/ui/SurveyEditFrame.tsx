import Link from 'next/link';
import { ReactNode } from 'react';
import { Button } from '@/shared/ui';

/** 定義の作成・改訂・差替の画面の外枠。見出し・戻り先・書き込みの案内・入力を縦に並べる。 */
export function SurveyEditFrame({
  title,
  back,
  feedback,
  children,
}: {
  title: string;
  back: { href: string; label: string };
  feedback?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <h1 className="text-2xl font-bold text-foreground">{title}</h1>
        <Button variant="outline" render={<Link href={back.href} />}>
          {back.label}
        </Button>
      </div>
      <p className="text-muted-foreground">
        保持期限は未設定です。不要な個人情報を入力しないでください。
      </p>
      {feedback}
      {children}
    </div>
  );
}

export const editCard = 'space-y-4 rounded-xl border bg-card p-6 text-card-foreground shadow-sm';
