import Image from 'next/image';
import Link from 'next/link';

interface HeaderProps {
  tenantName: string;
  logoUrl?: string;
}

export default function Header({ tenantName, logoUrl }: HeaderProps) {
  return (
    <header
      className="sticky top-0 z-50 border-b"
      style={{
        background: 'rgba(8, 8, 8, 0.97)',
        backdropFilter: 'blur(16px)',
        borderBottomColor: 'rgba(201, 168, 76, 0.12)',
      }}
    >
      <div className="max-w-7xl mx-auto px-6 lg:px-10">
        <div className="flex items-center justify-between h-15">
          {/* ロゴ */}
          <div className="flex items-center">
            {logoUrl ? (
              <Image
                src={logoUrl}
                alt={tenantName}
                width={140}
                height={36}
                className="h-8 w-auto"
              />
            ) : (
              <span
                className="text-lg tracking-[0.3em] text-[#C9A84C]"
                style={{ fontFamily: "'Noto Serif JP', 'Hiragino Mincho Pro', serif" }}
              >
                {tenantName}
              </span>
            )}
          </div>

          {/* ナビゲーション */}
          <nav className="hidden md:flex items-center gap-10">
            {[
              { href: '#cast', label: 'キャスト' },
              { href: '#campaign', label: 'キャンペーン' },
              { href: '#contact', label: 'お問い合わせ' },
            ].map(({ href, label }) => (
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

          {/* ログインボタン */}
          <Link
            href="/login"
            className="text-[9px] tracking-[0.3em] border border-[#C9A84C]/40 text-[#C9A84C] px-5 py-2 hover:bg-[#C9A84C] hover:text-[#080808] transition-all duration-300"
            style={{ fontFamily: "'Noto Serif JP', serif" }}
          >
            ログイン
          </Link>
        </div>
      </div>
    </header>
  );
}
