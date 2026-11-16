import Link from 'next/link';
import type { ReactNode } from 'react';
import type { PublicStore } from '@/shared/api/index.server';
import { STORE_SITE_NAV_PAGES } from '../../model/navigation';
import type { SiteConfig } from '../../model/types';
import AgeGate from './AgeGate';
import { mincho } from './fonts';
import Header from './Header';
import './styles.css';

interface FrameProps {
  store: PublicStore;
  siteConfig: SiteConfig;
  children: ReactNode;
}

/** default テンプレートの枠組み。年齢確認・上部バー・フッター・スマートフォンの予約の帯を持つ。 */
export default function Frame({ store, siteConfig, children }: FrameProps) {
  const sns = siteConfig.sns_links ?? [];
  const partners = siteConfig.partner_links ?? [];

  return (
    <div className={`sfd ${mincho.variable}`}>
      <AgeGate storeName={store.name} />
      <Header storeName={store.name} logoUrl={siteConfig.logo_url} />

      <main>{children}</main>

      <footer className="sfd-footer">
        <div className="sfd-footer-inner">
          <div>
            <p className="sfd-footer-name">{store.name}</p>
            <p className="sfd-footer-age">18歳以上限定</p>
          </div>
          <nav className="sfd-footer-nav" aria-label="フッター">
            {STORE_SITE_NAV_PAGES.map(({ href, label }) => (
              <Link key={href} href={href}>
                {label}
              </Link>
            ))}
            <Link href="/auth/login">会員ログイン</Link>
          </nav>
          {(sns.length > 0 || partners.length > 0) && (
            <div className="sfd-footer-links">
              {sns.length > 0 && (
                <div>
                  <p className="sfd-label">Follow</p>
                  <ul>
                    {sns.map((item, i) => (
                      <li key={i}>
                        <a href={item.url} target="_blank" rel="noopener noreferrer">
                          {item.label || item.platform}
                        </a>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              {partners.length > 0 && (
                <div>
                  <p className="sfd-label">Partners</p>
                  <ul>
                    {partners.map((item, i) => (
                      <li key={i}>
                        <a href={item.url} target="_blank" rel="noopener noreferrer">
                          {item.name}
                        </a>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
        </div>
        <p className="sfd-footer-copy">
          © {new Date().getFullYear()} {store.name}
        </p>
      </footer>

      <div className="sfd-dock">
        <Link href="/reservation" className="sfd-dock-cta">
          <span>ご予約</span>
          <span className="sfd-dock-en" aria-hidden="true">
            Reservation
          </span>
        </Link>
      </div>
    </div>
  );
}
