import Link from 'next/link';
import type { ReactNode } from 'react';
import { SITE_NAV, protoHref, type ProtoPageProps } from '../contract';
import { mincho } from './fonts';
import Header, { type MenuItem } from './Header';
import './styles.css';

const EN: Record<string, string> = {
  '/': 'Top',
  '/casts': 'Cast',
  '/schedule': 'Schedule',
  '/news': 'News',
  '/reviews': 'Reviews',
  '/menu': 'System',
  '/about': 'About',
  '/reservation': 'Reservation',
};

interface ShellProps {
  page: ProtoPageProps;
  children: ReactNode;
}

export default function Shell({ page, children }: ShellProps) {
  const { store, siteConfig, q } = page;
  const items: MenuItem[] = SITE_NAV.map(n => ({
    href: protoHref(n.href, q),
    label: n.label,
    en: EN[n.href] ?? '',
  }));
  const sns = siteConfig.sns_links ?? [];
  const partners = siteConfig.partner_links ?? [];
  const year = new Date().getFullYear();

  return (
    <div className={`p1356-default ${mincho.variable}`}>
      <Header
        storeName={store.name}
        logoUrl={siteConfig.logo_url}
        homeHref={protoHref('/', q)}
        items={items}
      />

      <main>{children}</main>

      <footer className="d-footer">
        <div className="d-footer-inner">
          <div className="d-footer-brand">
            <p className="d-footer-name">{store.name}</p>
            <p className="d-footer-age">18歳以上限定</p>
          </div>
          <nav className="d-footer-nav" aria-label="サイト内">
            {items.map(item => (
              <Link key={item.href} href={item.href}>
                {item.label}
              </Link>
            ))}
            <Link href="/auth/login">会員ログイン</Link>
          </nav>
          {(sns.length > 0 || partners.length > 0) && (
            <div className="d-footer-links">
              {sns.length > 0 && (
                <div>
                  <p className="d-label">Follow</p>
                  <ul>
                    {sns.map((s, i) => (
                      <li key={i}>
                        <a href={s.url} target="_blank" rel="noopener noreferrer">
                          {s.label || s.platform}
                        </a>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              {partners.length > 0 && (
                <div>
                  <p className="d-label">Partners</p>
                  <ul>
                    {partners.map((p, i) => (
                      <li key={i}>
                        <a href={p.url} target="_blank" rel="noopener noreferrer">
                          {p.name}
                        </a>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
        </div>
        <p className="d-footer-copy">
          © {year} {store.name}
        </p>
      </footer>

      <div className="d-dock">
        <Link href="/reservation" className="d-dock-cta">
          <span>ご予約</span>
          <span className="d-dock-en">Reservation</span>
        </Link>
      </div>
    </div>
  );
}

/** 写真の無いキャストの代わりに、名前の一文字目を明朝で据える。 */
export function NameTile({ name }: { name?: string }) {
  const initial = name ? Array.from(name)[0] : '・';
  return (
    <div className="d-nametile" aria-hidden="true">
      <span>{initial}</span>
    </div>
  );
}

export function castNo(i: number): string {
  return String(i + 1).padStart(2, '0');
}
