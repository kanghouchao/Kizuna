import type { ReactNode } from 'react';
import { cn } from '@/shared/lib/utils';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from './table';

export interface ChangeRow {
  /** 同じ項目名が並ぶとき（同名の明細等）の識別。省略時は label。 */
  key?: string;
  label: string;
  /** 表示文字列で渡す。変わったかどうかはこの文字列の一致で判定する。 */
  before: string;
  after: string;
}

/**
 * 変更前後の対照表（DESIGN.md「キー値と変更前後」）。全項目を並べ、変わった行を bg-muted と太字で示す。
 * bg-muted の上に text-muted-foreground を重ねない規則のため、変わった行の変更前は foreground で描く。
 */
export function ChangeTable({ rows }: { rows: ChangeRow[] }) {
  return (
    <Table className="table-fixed tabular-nums">
      <TableHeader>
        <TableRow>
          <TableHead className="w-[28%]">項目</TableHead>
          <TableHead>変更前</TableHead>
          <TableHead>変更後</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map(row => {
          const changed = row.before !== row.after;
          const cell = 'align-top whitespace-pre-wrap wrap-anywhere';
          return (
            <TableRow
              key={row.key ?? row.label}
              data-changed={changed}
              className={cn(changed && 'bg-muted hover:bg-muted')}
            >
              <TableCell className={cn(cell, changed && 'font-semibold')}>{row.label}</TableCell>
              <TableCell className={cn(cell, !changed && 'text-muted-foreground')}>
                {row.before}
              </TableCell>
              <TableCell className={cn(cell, changed && 'font-semibold')}>{row.after}</TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}

/** 変更の一覧。各件は ChangeEntry で日時・操作者の見出しの下に ChangeTable を置く。 */
export function ChangeLog({ children }: { children: ReactNode }) {
  return <ol className="space-y-5">{children}</ol>;
}

export function ChangeEntry({
  title,
  meta,
  children,
}: {
  /** 件の見出し（h3）。ダイアログの題名が h2 のため。 */
  title: ReactNode;
  /** 見出しの下の補足（操作者・理由・版など）。 */
  meta?: ReactNode;
  children: ReactNode;
}) {
  return (
    <li className="space-y-2 border-t pt-5 first:border-t-0 first:pt-0">
      <h3 className="font-medium wrap-anywhere">{title}</h3>
      {meta && (
        <div className="space-y-0.5 text-sm text-muted-foreground wrap-anywhere">{meta}</div>
      )}
      {children}
    </li>
  );
}
