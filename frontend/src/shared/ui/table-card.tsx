'use client';

import type { ComponentProps } from 'react';
import { cn } from '@/shared/lib/utils';
import { Card } from './card';

/**
 * テーブルを内包するカード。カード内壁 24px という規約をテーブルにも成立させる。
 *
 * テーブルは CardContent（px-6）に包めない。包むと行の border-b と hover 帯が
 * カード縁の 24px 手前で切れてしまい、データ表として読めなくなる。そのため内壁は
 * 端セル側の pl-6 / pr-6 で作る。この 24px は「カードの内壁」という定数であり、
 * セルの密度とは独立に据え置く。
 *
 * 列間（セル左右）は primitive の 8px のまま触らない。列数だけ効いてくる値なので、
 * ここを広げると列の多い一覧がより早く横スクロールに落ちる（8 列のキャスト一覧で
 * 12px にすると固有幅が 88px 増え、1024px 幅の窓で溢れる）。縦の 12px は行数にしか
 * 効かず幅を増やさないので、行の詰まりはこちらで解く。
 *
 * Card 既定の py-6 / gap-6 は潰す。残すとテーブル最終行の下と、見出し帯とテーブルの
 * 間にそれぞれ 24px の空白が挟まり、カードとテーブルが地続きに見えなくなる。
 *
 * セルへの指定は子孫セレクタ（詳細度 0,1,1）なので、呼び出し側が TableCell に
 * 直接書いた padding より強い。個別に変えたいセルが出たら、ここを直すこと。
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
