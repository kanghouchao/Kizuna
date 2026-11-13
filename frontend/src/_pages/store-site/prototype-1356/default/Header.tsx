'use client';

import Image from 'next/image';
import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';

export interface MenuItem {
  href: string;
  label: string;
  en: string;
}

interface HeaderProps {
  storeName: string;
  logoUrl?: string;
  homeHref: string;
  items: MenuItem[];
}

export default function Header({ storeName, logoUrl, homeHref, items }: HeaderProps) {
  const [open, setOpen] = useState(false);
  const closeRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    const root = document.documentElement;
    const prev = root.style.overflow;
    root.style.overflow = 'hidden';
    closeRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => {
      root.style.overflow = prev;
      window.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const close = () => setOpen(false);
  const logo = (
    <Link href={homeHref} onClick={close} className="d-bar-logo" aria-label={storeName}>
      {logoUrl ? (
        <span className="d-bar-logo-img">
          <Image src={logoUrl} alt={storeName} fill sizes="160px" />
        </span>
      ) : (
        <span className="d-bar-logo-text">{storeName}</span>
      )}
    </Link>
  );

  return (
    <>
      <header className="d-bar">
        <div className="d-bar-side">
          <Link href="/reservation" className="d-bar-reserve">
            ご予約
          </Link>
        </div>
        {logo}
        <div className="d-bar-side d-bar-side-end">
          <button
            type="button"
            className="d-bar-menu"
            aria-expanded={open}
            aria-controls="p1356d-menu"
            onClick={() => setOpen(true)}
          >
            <span className="d-bar-menu-lines" aria-hidden="true" />
            MENU
          </button>
        </div>
      </header>

      <div
        id="p1356d-menu"
        className={`d-menu ${open ? 'is-open' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-label="メニュー"
        aria-hidden={!open}
        inert={!open}
      >
        <div className="d-menu-top">
          <div className="d-bar-side" />
          <span className="d-menu-name">{storeName}</span>
          <div className="d-bar-side d-bar-side-end">
            <button ref={closeRef} type="button" className="d-bar-menu" onClick={close}>
              CLOSE
            </button>
          </div>
        </div>
        <div className="d-menu-body">
          <ol className="d-menu-list">
            {items.map((item, i) => (
              <li key={item.href}>
                <Link href={item.href} onClick={close} className="d-menu-link">
                  <span className="d-menu-no">{String(i + 1).padStart(2, '0')}</span>
                  <span className="d-menu-ja">{item.label}</span>
                  <span className="d-menu-en">{item.en}</span>
                </Link>
              </li>
            ))}
          </ol>
          <aside className="d-menu-aside">
            <Link href="/reservation" onClick={close} className="d-btn">
              ご予約へ進む
            </Link>
            <Link href="/auth/login" onClick={close} className="d-menu-login">
              会員ログイン
            </Link>
            <p className="d-menu-age">18歳以上限定 ／ For Adults Only</p>
          </aside>
        </div>
      </div>
    </>
  );
}
