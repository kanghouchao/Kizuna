import Link from 'next/link';
import type { Page } from '@/shared/api';
import { pageHref, pagerRange } from '../../lib/paging';

interface PagerProps {
  basePath: string;
  result: Page<unknown>;
}

/** 件数と表示範囲、番号の窓、前へ・次へ。端に達した前へ・次へはリンクにせず、押せない語として残す。 */
export default function Pager({ basePath, result }: PagerProps) {
  const { page, from, to, pages, hasPrev, hasNext } = pagerRange(result);

  const step = (label: string, index: number, enabled: boolean) =>
    enabled ? (
      <Link href={pageHref(basePath, index)} className="c-pager-step">
        {label}
      </Link>
    ) : (
      <span className="c-pager-step" aria-disabled="true">
        {label}
      </span>
    );

  return (
    <div className="c-pager">
      <p className="c-quiet">{`${result.total_elements} 件中 ${from}-${to} を表示`}</p>
      <nav aria-label="ページネーション" className="c-pager-nav">
        {step('前へ', page - 1, hasPrev)}
        <ol>
          {pages.map(index => (
            <li key={index}>
              <Link
                href={pageHref(basePath, index)}
                className="c-pager-no"
                aria-current={index === page ? 'page' : undefined}
              >
                {index + 1}
              </Link>
            </li>
          ))}
        </ol>
        {step('次へ', page + 1, hasNext)}
      </nav>
    </div>
  );
}
