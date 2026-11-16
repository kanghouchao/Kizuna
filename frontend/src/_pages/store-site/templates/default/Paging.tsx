import Link from 'next/link';
import type { ReactNode } from 'react';
import type { Page } from '@/shared/api';
import { pageHref, pagerRange } from '../../lib/paging';

/** 一覧の代わりに出す一文と、あれば立て直すための導線。 */
export function Notice({
  message,
  note,
  link,
  alert = false,
}: {
  message: string;
  note?: string;
  link?: { href: string; label: string };
  alert?: boolean;
}) {
  return (
    <div className="sfd-notice" role={alert ? 'alert' : undefined}>
      <p className="sfd-notice-text">{message}</p>
      {note && <p className="sfd-notice-note">{note}</p>}
      {link && (
        <Link href={link.href} className="sfd-textlink">
          {link.label}
        </Link>
      )}
    </div>
  );
}

/** 端に達した 前へ / 次へ はリンクにせず、進めない状態として残す。 */
function Step({ href, children }: { href: string | null; children: ReactNode }) {
  return href ? (
    <Link href={href} className="sfd-pager-step">
      {children}
    </Link>
  ) : (
    <span className="sfd-pager-step" aria-disabled="true">
      {children}
    </span>
  );
}

/** 一覧のページ送り。件数と表示中の範囲、最大 5 件の番号、前へ / 次へ。 */
export function Pager({ basePath, result }: { basePath: string; result: Page<unknown> }) {
  const { page, from, to, pages, hasPrev, hasNext } = pagerRange(result);

  return (
    <div className="sfd-pager">
      <p className="sfd-pager-range">{`${result.total_elements} 件中 ${from}–${to} を表示`}</p>
      <nav aria-label="ページネーション" className="sfd-pager-nav">
        <Step href={hasPrev ? pageHref(basePath, page - 1) : null}>前へ</Step>
        <ol className="sfd-pager-pages">
          {pages.map(index => (
            <li key={index}>
              <Link
                href={pageHref(basePath, index)}
                aria-current={index === page ? 'page' : undefined}
                className="sfd-pager-no"
              >
                {index + 1}
              </Link>
            </li>
          ))}
        </ol>
        <Step href={hasNext ? pageHref(basePath, page + 1) : null}>次へ</Step>
      </nav>
    </div>
  );
}
