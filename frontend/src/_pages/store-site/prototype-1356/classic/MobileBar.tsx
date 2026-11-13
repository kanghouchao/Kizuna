'use client';

import { XIcon } from 'lucide-react';
import Image from 'next/image';
import Link from 'next/link';
import { useEffect, useState } from 'react';
import { protoHref, SITE_NAV } from '../contract';

interface MobileBarProps {
  storeName: string;
  logoUrl?: string;
  q: string;
  current: string;
}

/** 狭い画面の上帯と「品書き」のドロワー。ドロワーの項目は縦書きで右から並ぶ */
export default function MobileBar({ storeName, logoUrl, q, current }: MobileBarProps) {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open]);

  const close = () => setOpen(false);

  return (
    <>
      <header className="c-topbar">
        <Link href={protoHref('/', q)} className="c-topbar-mark c-display" onClick={close}>
          {logoUrl ? (
            <span className="c-topbar-logo">
              <Image
                src={logoUrl}
                alt={storeName}
                fill
                sizes="120px"
                style={{ objectFit: 'contain', objectPosition: 'left center' }}
              />
            </span>
          ) : (
            storeName
          )}
        </Link>
        <button
          type="button"
          className="c-menu-btn"
          aria-expanded={open}
          aria-controls="c-classic-drawer"
          onClick={() => setOpen(true)}
        >
          <span className="c-menu-btn-bar" aria-hidden="true" />
          品書き
        </button>
      </header>

      <div id="c-classic-drawer" className="c-drawer" data-open={open ? 'true' : 'false'}>
        <button
          type="button"
          className="c-drawer-scrim"
          aria-label="品書きを閉じる"
          tabIndex={open ? 0 : -1}
          onClick={close}
        />
        <div
          className="c-drawer-panel"
          role="dialog"
          aria-modal="true"
          aria-label="品書き"
          aria-hidden={!open}
        >
          <div className="c-drawer-head">
            <span>品書き</span>
            <button
              type="button"
              className="c-drawer-close"
              aria-label="閉じる"
              tabIndex={open ? 0 : -1}
              onClick={close}
            >
              <XIcon size={20} strokeWidth={1.25} />
            </button>
          </div>

          <nav className="c-drawer-nav" aria-label="サイト内">
            <ul>
              {SITE_NAV.map(item => (
                <li key={item.href}>
                  <Link
                    href={protoHref(item.href, q)}
                    aria-current={item.href === current ? 'page' : undefined}
                    tabIndex={open ? 0 : -1}
                    onClick={close}
                  >
                    {item.label}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>

          <div className="c-drawer-foot">
            <Link href={protoHref('/auth/login', q)} tabIndex={open ? 0 : -1} onClick={close}>
              ログイン
            </Link>
            <span className="c-age">18歳以上限定</span>
            <Link
              href={protoHref('/reservation', q)}
              className="c-seal"
              tabIndex={open ? 0 : -1}
              onClick={close}
            >
              <span className="c-v">ご予約</span>
            </Link>
          </div>
        </div>
      </div>
    </>
  );
}
