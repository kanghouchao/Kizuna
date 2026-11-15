'use client';

import { Dialog } from '@base-ui/react/dialog';
import { XIcon } from 'lucide-react';
import Image from 'next/image';
import Link from 'next/link';
import { useState } from 'react';
import { STORE_SITE_NAV_PAGES } from '../../model/navigation';
import { classicRootClass } from './fonts';
import MemberReservationLink from './MemberReservationLink';

interface MobileNavProps {
  storeName: string;
  logoUrl?: string;
  current: string;
}

/**
 * 右端のナビを置けない画面の上部バーと、縦書きの引き出し。引き出しは開いている間だけ描くので、
 * 閉じている間は中の項目にフォーカスも読み上げも届かない。
 */
export default function MobileNav({ storeName, logoUrl, current }: MobileNavProps) {
  const [open, setOpen] = useState(false);
  const close = () => setOpen(false);

  return (
    <Dialog.Root open={open} onOpenChange={setOpen}>
      <header className="c-topbar">
        <Link href="/" className="c-topbar-mark c-display">
          {logoUrl ? (
            <span className="c-topbar-logo">
              <Image src={logoUrl} alt={storeName} fill sizes="120px" />
            </span>
          ) : (
            storeName
          )}
        </Link>
        <Dialog.Trigger className="c-menu-btn">
          <span className="c-menu-btn-bar" aria-hidden="true" />
          品書き
        </Dialog.Trigger>
      </header>

      <Dialog.Portal className={classicRootClass}>
        <Dialog.Backdrop className="c-drawer-scrim" />
        <Dialog.Popup className="c-drawer">
          <div className="c-drawer-head">
            <Dialog.Title className="c-drawer-title">品書き</Dialog.Title>
            <Dialog.Close className="c-drawer-close" aria-label="品書きを閉じる">
              <XIcon size={20} strokeWidth={1.25} aria-hidden="true" />
            </Dialog.Close>
          </div>

          <nav className="c-drawer-nav" aria-label="サイト内">
            <ul>
              {STORE_SITE_NAV_PAGES.map(({ href, label }) => (
                <li key={href}>
                  <Link
                    href={href}
                    aria-current={href === current ? 'page' : undefined}
                    onClick={close}
                  >
                    {label}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>

          <div className="c-drawer-foot">
            <div className="c-drawer-links">
              <MemberReservationLink className="c-textlink">会員予約</MemberReservationLink>
              <Link href="/auth/login" className="c-textlink" onClick={close}>
                ログイン
              </Link>
              <span className="c-age">18歳以上限定</span>
            </div>
            <Link href="/reservation" className="c-seal" onClick={close}>
              <span className="c-v">ご予約</span>
            </Link>
          </div>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
