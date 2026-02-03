interface HeaderProps {
  tenantName: string;
  logoUrl?: string;
}

export default function Header({ tenantName, logoUrl }: HeaderProps) {
  return (
    <header className="bg-white shadow-sm sticky top-0 z-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between items-center h-16">
          {/* Logo */}
          <div className="flex items-center">
            {logoUrl ? (
              <img src={logoUrl} alt={tenantName} className="h-10 w-auto" />
            ) : (
              <span className="text-2xl font-bold text-indigo-600">{tenantName}</span>
            )}
          </div>

          {/* Navigation */}
          <nav className="hidden md:flex space-x-8">
            <a href="#cast" className="text-gray-700 hover:text-indigo-600 transition-colors">
              キャスト
            </a>
            <a href="#about" className="text-gray-700 hover:text-indigo-600 transition-colors">
              店舗情報
            </a>
            <a href="#contact" className="text-gray-700 hover:text-indigo-600 transition-colors">
              お問い合わせ
            </a>
          </nav>

          {/* Login Button */}
          <div className="flex items-center">
            <a
              href="/login"
              className="bg-indigo-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-indigo-700 transition-colors"
            >
              ログイン
            </a>
          </div>
        </div>
      </div>
    </header>
  );
}
