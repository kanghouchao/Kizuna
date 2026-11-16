import Link from 'next/link';

interface NoticeProps {
  message: string;
  /** 補足の一行。 */
  detail?: string;
  link?: { href: string; label: string };
  /** 取得の失敗のように、読み上げで知らせるべきもの。 */
  alert?: boolean;
}

/** 一覧や表の代わりに置く一文と、あれば回復の導線。 */
export default function Notice({ message, detail, link, alert = false }: NoticeProps) {
  return (
    <div className="c-notice" role={alert ? 'alert' : undefined}>
      <p className="c-notice-text c-display">{message}</p>
      {detail && <p className="c-quiet">{detail}</p>}
      {link && (
        <Link href={link.href} className="c-textlink">
          {link.label}
        </Link>
      )}
    </div>
  );
}
