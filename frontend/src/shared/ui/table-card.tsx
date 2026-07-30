'use client';

import type { ComponentProps } from 'react';
import { cn } from '@/shared/lib/utils';
import { Card } from './card';

/**
 * テーブルを内包するカード。「カード内の内容はカード縁から 24px」という内壁の規約を
 * テーブルにも成立させる。テーブルは CardContent（px-6）に包めない — 包むと行の
 * border-b と hover 帯がカード縁の手前で切れるため、内壁は端セルの pl-6 / pr-6 で作る。
 *
 * 列間を primitive の 8px のまま据え置く理由、端 gutter とセル密度が非連動である理由、
 * および各規則の詳細度（密度は 0,1,1／端 gutter は 0,2,1 で、後者は primitive の
 * checkbox 用 pr-0 まで越える）は DESIGN.md の Spacing 節に一本化してある。
 * 値を動かす前にそちらを読むこと。
 *
 * Card 既定の py-6 / gap-6 は潰す。残すとテーブル最終行の下と、見出し帯とテーブルの
 * 間にそれぞれ 24px の空白が挟まり、カードとテーブルが地続きに見えなくなる。
 */
export function TableCard({ className, ...props }: ComponentProps<'div'>) {
  return (
    <Card
      className={cn(
        'py-0 gap-0 overflow-hidden',
        '[&_th]:h-11 [&_td]:py-3',
        '[&_tr>*:first-child]:pl-6 [&_tr>*:last-child]:pr-6',
        className
      )}
      {...props}
    />
  );
}
