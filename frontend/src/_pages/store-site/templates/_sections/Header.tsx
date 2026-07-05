'use client';

import Image from 'next/image';
import Link from 'next/link';
import { useState } from 'react';

interface HeaderProps {
  tenantName: string;
  logoUrl?: string;
}

const navLinks = [
  { href: '/', label: 'TOP' },
  { href: '/casts', label: 'キャスト' },
  { href: '/schedule', label: '出勤表' },
  { href: '/menu', label: '料金' },
  { href: '/about', label: '店舗情報' },
];

export default function Header({ tenantName, logoUrl }: HeaderProps) {
  const [menuOpen, setMenuOpen] = useState(false);

  const closeMenu = () => setMenuOpen(false);

  return (
    <>
      <header
        className="sticky top-0 z-50 border-b"
        style={{
          background: 'color-mix(in srgb, var(--storefront-bg) 97%, transparent)',
          backdropFilter: 'blur(16px)',
          borderBottomColor: 'color-mix(in srgb, var(--storefront-accent) 12%, transparent)',
        }}
      >
        <div className="max-w-7xl mx-auto px-5 lg:px-10">
          <div className="flex items-center justify-between h-14 md:h-15">
            {/* ロゴ */}
            <Link href="/" onClick={closeMenu} className="flex items-center shrink-0">
              {logoUrl ? (
                <Image
                  src={logoUrl}
                  alt={tenantName}
                  width={120}
                  height={32}
                  className="h-7 md:h-8 w-auto"
                />
              ) : (
                <span
                  className="text-base md:text-lg tracking-[0.25em] md:tracking-[0.3em] text-[var(--storefront-accent)]"
                  style={{ fontFamily: 'var(--storefront-font-display)' }}
                >
                  {tenantName}
                </span>
              )}
            </Link>

            {/* デスクトップナビ */}
            <nav className="hidden md:flex items-center gap-10">
              {navLinks.map(({ href, label }) => (
                <Link
                  key={href}
                  href={href}
                  className="text-[10px] tracking-[0.25em] text-[color-mix(in_srgb,var(--storefront-fg)_40%,transparent)] hover:text-[var(--storefront-accent)] transition-colors duration-300"
                  style={{ fontFamily: 'var(--storefront-font-display)' }}
                >
                  {label}
                </Link>
              ))}
            </nav>

            {/* 右側エリア */}
            <div className="flex items-center gap-3 md:gap-4">
              {/* ログインボタン（sm以上で表示） */}
              <Link
                href="/login"
                className="hidden sm:block text-[9px] tracking-[0.3em] border border-[color-mix(in_srgb,var(--storefront-accent)_40%,transparent)] text-[var(--storefront-accent)] px-4 py-1.5 md:px-5 md:py-2 hover:bg-[var(--storefront-accent)] hover:text-[var(--storefront-bg)] transition-all duration-300"
                style={{ fontFamily: 'var(--storefront-font-display)' }}
              >
                ログイン
              </Link>

              {/* ハンバーガーボタン（md未満で表示） */}
              <button
                onClick={() => setMenuOpen(prev => !prev)}
                className="md:hidden flex flex-col justify-center items-center w-8 h-8 gap-[5px] shrink-0"
                aria-label={menuOpen ? 'メニューを閉じる' : 'メニューを開く'}
                aria-expanded={menuOpen}
              >
                <span
                  className="block w-[18px] h-px bg-[var(--storefront-accent)]"
                  style={{
                    transition: 'transform 0.25s ease, opacity 0.25s ease',
                    transform: menuOpen ? 'translateY(6px) rotate(45deg)' : 'none',
                  }}
                />
                <span
                  className="block w-[18px] h-px bg-[var(--storefront-accent)]"
                  style={{
                    transition: 'opacity 0.25s ease',
                    opacity: menuOpen ? 0 : 1,
                  }}
                />
                <span
                  className="block w-[18px] h-px bg-[var(--storefront-accent)]"
                  style={{
                    transition: 'transform 0.25s ease',
                    transform: menuOpen ? 'translateY(-6px) rotate(-45deg)' : 'none',
                  }}
                />
              </button>
            </div>
          </div>
        </div>
      </header>

      {/* モバイルメニューオーバーレイ */}
      <div
        className="fixed inset-0 z-40 md:hidden flex flex-col pointer-events-none"
        style={{
          top: '56px',
          background: 'color-mix(in srgb, var(--storefront-bg) 98%, transparent)',
          opacity: menuOpen ? 1 : 0,
          transition: 'opacity 0.3s ease',
          pointerEvents: menuOpen ? 'auto' : 'none',
        }}
      >
        {/* ナビリンク */}
        <nav className="flex flex-col items-center justify-center flex-1 gap-10">
          {navLinks.map(({ href, label }, i) => (
            <Link
              key={href}
              href={href}
              onClick={closeMenu}
              className="text-[color-mix(in_srgb,var(--storefront-fg)_60%,transparent)] text-base tracking-[0.35em] hover:text-[var(--storefront-accent)] transition-colors duration-200"
              style={{
                fontFamily: 'var(--storefront-font-display)',
                transitionDelay: menuOpen ? `${i * 60}ms` : '0ms',
                transform: menuOpen ? 'translateY(0)' : 'translateY(8px)',
                opacity: menuOpen ? 1 : 0,
                transition: 'color 0.2s ease, transform 0.35s ease, opacity 0.35s ease',
              }}
            >
              {label}
            </Link>
          ))}

          {/* 区切り */}
          <div className="flex items-center gap-3 my-2">
            <div className="h-px w-10 bg-linear-to-r from-transparent to-[color-mix(in_srgb,var(--storefront-accent)_25%,transparent)]" />
            <span className="text-[color-mix(in_srgb,var(--storefront-accent)_30%,transparent)] text-[10px]">
              ◆
            </span>
            <div className="h-px w-10 bg-linear-to-l from-transparent to-[color-mix(in_srgb,var(--storefront-accent)_25%,transparent)]" />
          </div>

          {/* ログインボタン（xs） */}
          <Link
            href="/login"
            onClick={closeMenu}
            className="text-[10px] tracking-[0.4em] border border-[color-mix(in_srgb,var(--storefront-accent)_40%,transparent)] text-[var(--storefront-accent)] px-8 py-3 hover:bg-[var(--storefront-accent)] hover:text-[var(--storefront-bg)] transition-all duration-300"
            style={{ fontFamily: 'var(--storefront-font-display)' }}
          >
            ログイン
          </Link>
        </nav>

        {/* 下部コピーライト */}
        <p
          className="text-center text-[var(--storefront-subtle)] text-[9px] tracking-wider pb-8"
          style={{ fontFamily: 'var(--storefront-font-display)' }}
        >
          18歳以上限定 / For Adults Only
        </p>
      </div>
    </>
  );
}
