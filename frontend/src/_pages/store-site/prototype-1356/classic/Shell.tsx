import Image from 'next/image';
import Link from 'next/link';
import type { ReactNode } from 'react';
import { protoHref, SITE_NAV, type ProtoPageProps } from '../contract';
import { classicBody, classicDisplay } from './fonts';
import MobileBar from './MobileBar';
import './styles.css';

interface ShellProps {
  page: ProtoPageProps;
  current: string;
  children: ReactNode;
}

export default function Shell({ page, current, children }: ShellProps) {
  const { store, siteConfig, q } = page;
  const sns = siteConfig.sns_links ?? [];
  const partners = siteConfig.partner_links ?? [];

  return (
    <div className={`p1356-classic ${classicDisplay.variable} ${classicBody.variable}`}>
      {/* 右端の縦レール。読み順に合わせ、項目は右の列から左の列へ流す */}
      <aside className="c-rail" aria-label="サイト内">
        <Link href={protoHref('/', q)} aria-label={store.name}>
          {siteConfig.logo_url ? (
            <span className="c-rail-logo">
              <Image
                src={siteConfig.logo_url}
                alt={store.name}
                fill
                sizes="76px"
                style={{ objectFit: 'contain' }}
              />
            </span>
          ) : (
            <span className="c-rail-mark c-v c-display">{store.name}</span>
          )}
        </Link>
        <span className="c-rail-tick" aria-hidden="true" />
        <nav>
          <ul className="c-rail-nav" style={{ margin: 0, padding: 0, listStyle: 'none' }}>
            {SITE_NAV.map((item, i) => (
              <li key={item.href} style={{ gridColumn: i < 4 ? 2 : 1, gridRow: (i % 4) + 1 }}>
                <Link
                  href={protoHref(item.href, q)}
                  className="c-rail-link c-v"
                  aria-current={item.href === current ? 'page' : undefined}
                >
                  {item.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
        <span className="c-rail-tick" aria-hidden="true" />
        <Link href={protoHref('/reservation', q)} className="c-seal">
          <span className="c-v">ご予約</span>
        </Link>
        <Link href={protoHref('/auth/login', q)} className="c-rail-login c-v">
          ログイン
        </Link>
      </aside>

      <div className="c-shell">
        <MobileBar storeName={store.name} logoUrl={siteConfig.logo_url} q={q} current={current} />

        <main className="c-main">{children}</main>

        <footer className="c-foot">
          <div className="c-foot-in">
            <div className="c-foot-cols">
              <section>
                <h2>御案内</h2>
                <ul>
                  {SITE_NAV.map(item => (
                    <li key={item.href}>
                      <Link href={protoHref(item.href, q)}>{item.label}</Link>
                    </li>
                  ))}
                  <li>
                    <Link href={protoHref('/auth/login', q)}>ログイン</Link>
                  </li>
                </ul>
              </section>
              {sns.length > 0 && (
                <section>
                  <h2>公式SNS</h2>
                  <ul>
                    {sns.map((s, i) => (
                      <li key={`${s.url}-${i}`}>
                        <a href={s.url} target="_blank" rel="noopener noreferrer">
                          {s.label || s.platform}
                        </a>
                      </li>
                    ))}
                  </ul>
                </section>
              )}
              {partners.length > 0 && (
                <section>
                  <h2>提携先</h2>
                  <ul>
                    {partners.map((p, i) => (
                      <li key={`${p.url}-${i}`}>
                        <a href={p.url} target="_blank" rel="noopener noreferrer">
                          {p.name}
                        </a>
                      </li>
                    ))}
                  </ul>
                </section>
              )}
            </div>
            <div className="c-foot-sign">
              <p className="c-foot-name c-v c-display">{store.name}</p>
            </div>
            <div className="c-foot-copy">
              <span className="c-age">18歳以上限定</span>
              <span>
                © {new Date().getFullYear()} {store.name}
              </span>
            </div>
          </div>
        </footer>
      </div>
    </div>
  );
}
