import type { ReactNode } from 'react';

type PageHeaderProps = {
  /** ページの主見出し。各ページに 1 つだけ置かれる h1 になる。 */
  title: string;
  /** 見出しの下に添える補足。持たないページがあるため任意。 */
  description?: string;
  /** 見出し右側の主アクション。複数渡された場合も同じ間隔で並ぶ。 */
  actions?: ReactNode;
};

/**
 * 一覧ページ外殻の見出しブロック。
 *
 * class 文字列をここに封じ込めることで、ページ側が外殻を再導出できなくなる
 * ——並行して改修される複数ページの余白と見出しサイズを揃える手段はこれのみ。
 */
export function PageHeader({ title, description, actions }: PageHeaderProps) {
  return (
    <div className="flex items-center justify-between">
      <div>
        <h1 className="text-2xl font-bold text-foreground">{title}</h1>
        {description && <p className="text-sm text-muted-foreground mt-1">{description}</p>}
      </div>
      {actions && <div className="flex items-center gap-3">{actions}</div>}
    </div>
  );
}
