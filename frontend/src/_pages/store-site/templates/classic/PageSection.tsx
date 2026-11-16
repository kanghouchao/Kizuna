import Link from 'next/link';
import type { ReactNode } from 'react';

interface PageSectionProps {
  /** ページの見出し。縦書きで右に立てるので、長さの決まった語だけを渡す。 */
  title: string;
  /** 見出しの隣に添える短い一行（人数など）。 */
  note?: ReactNode;
  children: ReactNode;
}

/** 下層ページの頭: 現在地の道筋と、縦書きの見出しを右に立てた本文の段。 */
export default function PageSection({ title, note, children }: PageSectionProps) {
  return (
    <>
      <nav className="c-crumb" aria-label="現在地">
        <Link href="/">TOP</Link>
        <span aria-hidden="true">／</span>
        <span aria-current="page">{title}</span>
      </nav>

      <section className="c-sec c-pagehead">
        <div className="c-sec-title c-v">
          <h1 className="c-display">{title}</h1>
          {note && <p>{note}</p>}
        </div>
        <div className="c-sec-body">{children}</div>
      </section>
    </>
  );
}
