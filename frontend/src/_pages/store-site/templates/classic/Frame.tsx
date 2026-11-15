import Image from 'next/image';
import Link from 'next/link';
import type { ReactNode } from 'react';
import type { PublicStore } from '@/shared/api/index.server';
import { STORE_SITE_NAV_PAGES } from '../../model/navigation';
import type { SiteConfig } from '../../model/types';
import AgeGate from './AgeGate';
import { classicRootClass } from './fonts';
import MemberReservationLink from './MemberReservationLink';
import MobileNav from './MobileNav';
import './classic.css';

interface FrameProps {
  store: PublicStore;
  siteConfig: SiteConfig;
  /** ナビゲーションで現在地として示すページの href */
  current: string;
  children: ReactNode;
}

/** classic の枠組み: 年齢確認、右端の縦書きのナビ（狭い・低い画面では上部バーと引き出し）、フッター。 */
export default function Frame({ store, siteConfig, current, children }: FrameProps) {
  const sns = siteConfig.sns_links ?? [];
  const partners = siteConfig.partner_links ?? [];

  return (
    <div className={`${classicRootClass} c-page`}>
      <AgeGate storeName={store.name} />

      <header className="c-rail">
        <Link href="/" className="c-rail-home">
          {siteConfig.logo_url ? (
            <span className="c-rail-logo">
              <Image src={siteConfig.logo_url} alt={store.name} fill sizes="76px" />
            </span>
          ) : (
            <span className="c-rail-mark c-v c-name c-display">{store.name}</span>
          )}
        </Link>
        <nav aria-label="サイト内">
          <ul className="c-rail-nav">
            {STORE_SITE_NAV_PAGES.map(({ href, label }) => (
              <li key={href}>
                <Link
                  href={href}
                  className="c-rail-link c-v"
                  aria-current={href === current ? 'page' : undefined}
                >
                  {label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
        <Link href="/reservation" className="c-seal">
          <span className="c-v">ご予約</span>
        </Link>
        <div className="c-rail-sub">
          <Link href="/auth/login" className="c-rail-link c-v">
            ログイン
          </Link>
          <MemberReservationLink className="c-rail-link c-v">会員予約</MemberReservationLink>
        </div>
      </header>

      <div className="c-shell">
        <MobileNav storeName={store.name} logoUrl={siteConfig.logo_url} current={current} />

        <main className="c-main">{children}</main>

        <footer className="c-foot">
          <div className="c-foot-in">
            <div className="c-foot-cols">
              <nav aria-label="ご案内">
                <h2>ご案内</h2>
                <ul>
                  {STORE_SITE_NAV_PAGES.map(({ href, label }) => (
                    <li key={href}>
                      <Link href={href}>{label}</Link>
                    </li>
                  ))}
                  <li>
                    <Link href="/auth/login">ログイン</Link>
                  </li>
                </ul>
              </nav>
              {sns.length > 0 && (
                <section>
                  <h2>公式SNS</h2>
                  <ul>
                    {sns.map((item, index) => (
                      <li key={index}>
                        <a href={item.url} target="_blank" rel="noopener noreferrer">
                          {item.label || item.platform}
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
                    {partners.map((partner, index) => (
                      <li key={index}>
                        <a href={partner.url} target="_blank" rel="noopener noreferrer">
                          {partner.name}
                        </a>
                      </li>
                    ))}
                  </ul>
                </section>
              )}
            </div>
            <p className="c-foot-name c-v c-name c-display">{store.name}</p>
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
