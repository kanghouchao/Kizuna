'use client';

import Image from 'next/image';
import Link from 'next/link';
import { useRef, useState } from 'react';
import { STORE_SITE_NAV_PAGES } from '../../model/navigation';
import { useMemberReservationUrl } from '../../model/useMemberReservationUrl';

const EN_LABELS: Record<string, string> = {
  '/': 'Home',
  '/casts': 'Cast',
  '/schedule': 'Schedule',
  '/news': 'News',
  '/reviews': 'Reviews',
  '/menu': 'System',
  '/about': 'About',
  '/reservation': 'Reservation',
};

const MENU_ID = 'sfd-menu';

interface HeaderProps {
  storeName: string;
  logoUrl?: string;
}

/**
 * 上部バーと全画面メニュー。メニューは modal の dialog で、閉じている間は中の項目へ到達できず、
 * 開いている間は Tab が中に留まり Esc で閉じる。フォーカスの出し入れはブラウザに任せる。
 */
export default function Header({ storeName, logoUrl }: HeaderProps) {
  const menuRef = useRef<HTMLDialogElement>(null);
  const [open, setOpen] = useState(false);
  const memberReservationUrl = useMemberReservationUrl();

  const openMenu = () => {
    menuRef.current?.showModal();
    setOpen(true);
  };
  const closeMenu = () => menuRef.current?.close();

  return (
    <>
      <header className="sfd-bar">
        <div className="sfd-bar-side">
          <Link href="/reservation" className="sfd-bar-reserve">
            ご予約
          </Link>
        </div>
        <Link href="/" className="sfd-bar-logo">
          {logoUrl ? (
            <span className="sfd-bar-logo-img">
              <Image src={logoUrl} alt={storeName} fill loading="eager" sizes="140px" />
            </span>
          ) : (
            <span className="sfd-bar-logo-text">{storeName}</span>
          )}
        </Link>
        <div className="sfd-bar-side sfd-bar-side-end">
          <button
            type="button"
            className="sfd-bar-menu"
            aria-haspopup="dialog"
            aria-expanded={open}
            aria-controls={MENU_ID}
            onClick={openMenu}
          >
            <span className="sfd-bar-menu-lines" aria-hidden="true" />
            MENU
          </button>
        </div>
      </header>

      <dialog
        ref={menuRef}
        id={MENU_ID}
        className="sfd-menu"
        aria-label="メニュー"
        onClose={() => setOpen(false)}
      >
        <div className="sfd-menu-top">
          <div className="sfd-bar-side" />
          <span className="sfd-menu-name">{storeName}</span>
          <div className="sfd-bar-side sfd-bar-side-end">
            <button type="button" className="sfd-bar-menu" onClick={closeMenu}>
              CLOSE
            </button>
          </div>
        </div>
        <div className="sfd-menu-body">
          <nav aria-label="サイト内">
            <ol className="sfd-menu-list">
              {STORE_SITE_NAV_PAGES.map(({ href, label }, i) => (
                <li key={href}>
                  <Link href={href} onClick={closeMenu} className="sfd-menu-link">
                    <span className="sfd-menu-no" aria-hidden="true">
                      {String(i + 1).padStart(2, '0')}
                    </span>
                    <span className="sfd-menu-ja">{label}</span>
                    <span className="sfd-menu-en" aria-hidden="true">
                      {EN_LABELS[href]}
                    </span>
                  </Link>
                </li>
              ))}
            </ol>
          </nav>
          <div className="sfd-menu-aside">
            <Link href="/reservation" onClick={closeMenu} className="sfd-btn">
              ご予約へ進む
            </Link>
            {memberReservationUrl && (
              <a href={memberReservationUrl} className="sfd-menu-sub">
                会員の方は WEB予約
              </a>
            )}
            <Link href="/auth/login" onClick={closeMenu} className="sfd-menu-sub">
              会員ログイン
            </Link>
            <p className="sfd-menu-age">18歳以上限定 ／ For Adults Only</p>
          </div>
        </div>
      </dialog>
    </>
  );
}
