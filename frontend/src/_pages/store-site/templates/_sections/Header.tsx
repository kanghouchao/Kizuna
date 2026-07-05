'use client';

import Image from 'next/image';
import Link from 'next/link';
import { useState } from 'react';

interface HeaderProps {
  tenantName: string;
  logoUrl?: string;
}

const navLinks = [
  { href: '#cast', label: 'キャスト' },
  { href: '#campaign', label: 'キャンペーン' },
  { href: '#contact', label: 'お問い合わせ' },
];

export default function Header({ tenantName, logoUrl }: HeaderProps) {
  const [menuOpen, setMenuOpen] = useState(false);

  const closeMenu = () => setMenuOpen(false);

  return (
    <>
      <header
        className="sticky top-0 z-50 border-b"
        style={{
          background: 'rgba(8, 8, 8, 0.97)',
          backdropFilter: 'blur(16px)',
          borderBottomColor: 'rgba(201, 168, 76, 0.12)',
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
                  className="text-base md:text-lg tracking-[0.25em] md:tracking-[0.3em] text-[#C9A84C]"
                  style={{ fontFamily: "'Noto Serif JP', 'Hiragino Mincho Pro', serif" }}
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
                  className="text-[10px] tracking-[0.25em] text-[#F8F4F0]/40 hover:text-[#C9A84C] transition-colors duration-300"
                  style={{ fontFamily: "'Noto Serif JP', serif" }}
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
                className="hidden sm:block text-[9px] tracking-[0.3em] border border-[#C9A84C]/40 text-[#C9A84C] px-4 py-1.5 md:px-5 md:py-2 hover:bg-[#C9A84C] hover:text-[#080808] transition-all duration-300"
                style={{ fontFamily: "'Noto Serif JP', serif" }}
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
                  className="block w-[18px] h-px bg-[#C9A84C]"
                  style={{
                    transition: 'transform 0.25s ease, opacity 0.25s ease',
                    transform: menuOpen ? 'translateY(6px) rotate(45deg)' : 'none',
                  }}
                />
                <span
                  className="block w-[18px] h-px bg-[#C9A84C]"
                  style={{
                    transition: 'opacity 0.25s ease',
                    opacity: menuOpen ? 0 : 1,
                  }}
                />
                <span
                  className="block w-[18px] h-px bg-[#C9A84C]"
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
          background: 'rgba(8, 8, 8, 0.98)',
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
              className="text-[#F8F4F0]/60 text-base tracking-[0.35em] hover:text-[#C9A84C] transition-colors duration-200"
              style={{
                fontFamily: "'Noto Serif JP', serif",
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
            <div className="h-px w-10 bg-linear-to-r from-transparent to-[#C9A84C]/25" />
            <span className="text-[#C9A84C]/30 text-[10px]">◆</span>
            <div className="h-px w-10 bg-linear-to-l from-transparent to-[#C9A84C]/25" />
          </div>

          {/* ログインボタン（xs） */}
          <Link
            href="/login"
            onClick={closeMenu}
            className="text-[10px] tracking-[0.4em] border border-[#C9A84C]/40 text-[#C9A84C] px-8 py-3 hover:bg-[#C9A84C] hover:text-[#080808] transition-all duration-300"
            style={{ fontFamily: "'Noto Serif JP', serif" }}
          >
            ログイン
          </Link>
        </nav>

        {/* 下部コピーライト */}
        <p
          className="text-center text-[#252525] text-[9px] tracking-wider pb-8"
          style={{ fontFamily: "'Noto Serif JP', serif" }}
        >
          18歳以上限定 / For Adults Only
        </p>
      </div>
    </>
  );
}
