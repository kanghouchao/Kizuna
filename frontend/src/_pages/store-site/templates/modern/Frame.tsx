import { ArrowUpRightIcon } from 'lucide-react';
import Link from 'next/link';
import type { ReactNode } from 'react';
import type { PublicStore } from '@/shared/api/index.server';
import { STORE_SITE_NAV_PAGES } from '../../model/navigation';
import type { SiteConfig } from '../../model/types';
import AgeGate from './AgeGate';
import { toneAt } from './castParts';
import { roundedDisplay } from './fonts';
import { TabBar, TopNav } from './Navigation';
import './modern.css';

function Footer({ store, siteConfig }: { store: PublicStore; siteConfig: SiteConfig }) {
  const sns = siteConfig.sns_links ?? [];
  const partners = siteConfig.partner_links ?? [];

  return (
    <footer className="mx-auto mt-16 max-w-7xl px-4 lg:px-6">
      <div className="m-bg-ink rounded-[var(--m-radius)] px-6 py-10 lg:px-12">
        <div className="flex flex-col gap-8 lg:flex-row lg:justify-between">
          <div className="min-w-0">
            <p className="m-display text-3xl">{store.name}</p>
            <p className="m-bg-yellow mt-3 inline-block rounded-full px-3 py-1 text-xs font-bold">
              18歳以上限定
            </p>
            {sns.length > 0 && (
              <ul className="mt-5 flex flex-wrap gap-2" aria-label="SNS">
                {sns.map((s, i) => (
                  <li key={`${s.url}-${i}`}>
                    <a
                      href={s.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className={`${toneAt(i)} inline-block rounded-full px-4 py-1.5 text-sm font-bold`}
                    >
                      {s.label || s.platform}
                    </a>
                  </li>
                ))}
              </ul>
            )}
          </div>
          <nav aria-label="フッターメニュー">
            <ul className="grid grid-cols-2 gap-x-8 gap-y-2 text-sm sm:grid-cols-4 lg:grid-cols-2">
              {STORE_SITE_NAV_PAGES.map(({ href, label }) => (
                <li key={href}>
                  <Link href={href} className="hover:text-[var(--m-yellow)]">
                    {label}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
        </div>
        {partners.length > 0 && (
          <div className="mt-8 border-t border-white/25 pt-6">
            <p className="text-xs font-bold tracking-widest">PARTNER</p>
            <ul className="mt-3 flex flex-wrap gap-2">
              {partners.map((p, i) => (
                <li key={`${p.url}-${i}`}>
                  <a
                    href={p.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 rounded-full border border-white/60 px-3 py-1 text-sm hover:bg-white/10"
                  >
                    {p.name}
                    <ArrowUpRightIcon className="size-3.5" aria-hidden />
                  </a>
                </li>
              ))}
            </ul>
          </div>
        )}
        <p className="mt-8 text-xs">© {store.name}</p>
      </div>
    </footer>
  );
}

/** modern の枠組み。年齢確認・上部のナビ・フッター・下部のタブバーで各ページの中身を囲む。 */
export default function Frame({
  store,
  siteConfig,
  children,
}: {
  store: PublicStore;
  siteConfig: SiteConfig;
  children: ReactNode;
}) {
  return (
    <div className={`sf-modern ${roundedDisplay.variable}`}>
      <AgeGate storeName={store.name}>
        <div className="m-page">
          <TopNav storeName={store.name} logoUrl={siteConfig.logo_url} />
          <main className="mx-auto max-w-7xl px-4 lg:px-6 lg:pt-8">{children}</main>
          <Footer store={store} siteConfig={siteConfig} />
        </div>
        <TabBar />
      </AgeGate>
    </div>
  );
}
