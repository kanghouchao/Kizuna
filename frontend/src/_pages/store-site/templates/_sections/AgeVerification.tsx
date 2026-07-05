'use client';

import { useState, useEffect } from 'react';

const STORAGE_KEY = 'kizuna_age_verified';

interface AgeGateProps {
  storeName: string;
}

export default function AgeGate({ storeName }: AgeGateProps) {
  const [phase, setPhase] = useState<'loading' | 'gate' | 'declined' | 'done'>('loading');
  const [mounted, setMounted] = useState(false);
  const [exiting, setExiting] = useState(false);

  useEffect(() => {
    const verified = localStorage.getItem(STORAGE_KEY) === 'true';
    setPhase(verified ? 'done' : 'gate');
    // フェードイン用のマウントフラグ
    const t = setTimeout(() => setMounted(true), 50);
    return () => clearTimeout(t);
  }, []);

  const handleAccept = () => {
    setExiting(true);
    localStorage.setItem(STORAGE_KEY, 'true');
    setTimeout(() => setPhase('done'), 700);
  };

  const handleDecline = () => {
    setPhase('declined');
  };

  // 検証済みの場合は何も表示しない
  if (phase === 'done') return null;

  // ローディング中は黒い画面を表示
  if (phase === 'loading') {
    return <div className="fixed inset-0 bg-[var(--storefront-bg)] z-[9999]" />;
  }

  return (
    <div
      className="fixed inset-0 z-[9999] flex items-center justify-center p-6"
      style={{
        background:
          'radial-gradient(ellipse at 50% 35%, var(--storefront-bg-glow) 0%, var(--storefront-bg) 65%)',
        opacity: exiting ? 0 : mounted ? 1 : 0,
        transition: 'opacity 0.7s ease',
      }}
    >
      {/* グレインテクスチャ */}
      <div
        className="absolute inset-0 pointer-events-none opacity-20"
        style={{
          backgroundImage: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='300' height='300'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='300' height='300' filter='url(%23n)' opacity='1'/%3E%3C/svg%3E")`,
        }}
      />

      {/* コーナーオーナメント */}
      <div className="absolute top-8 left-8 w-14 h-14 border-t border-l border-[color-mix(in_srgb,var(--storefront-accent)_25%,transparent)]" />
      <div className="absolute top-8 right-8 w-14 h-14 border-t border-r border-[color-mix(in_srgb,var(--storefront-accent)_25%,transparent)]" />
      <div className="absolute bottom-8 left-8 w-14 h-14 border-b border-l border-[color-mix(in_srgb,var(--storefront-accent)_25%,transparent)]" />
      <div className="absolute bottom-8 right-8 w-14 h-14 border-b border-r border-[color-mix(in_srgb,var(--storefront-accent)_25%,transparent)]" />

      {/* メインコンテンツ */}
      <div
        className="w-full max-w-[360px] text-center"
        style={{
          transform: mounted && !exiting ? 'translateY(0)' : 'translateY(16px)',
          transition: 'transform 0.7s ease',
        }}
      >
        {phase === 'gate' ? (
          <>
            {/* 英語サブタイトル */}
            <p
              className="text-[color-mix(in_srgb,var(--storefront-accent)_45%,transparent)] text-[9px] tracking-[0.6em] uppercase mb-5"
              style={{ letterSpacing: '0.5em' }}
            >
              Premium Entertainment
            </p>

            {/* 店舗名 */}
            <h1
              className="text-[28px] text-[var(--storefront-fg)] font-light tracking-[0.2em] mb-7"
              style={{ fontFamily: 'var(--storefront-font-display)' }}
            >
              {storeName}
            </h1>

            {/* ゴールド区切り線 */}
            <div className="flex items-center justify-center gap-3 mb-8">
              <div className="h-px w-16 bg-linear-to-r from-transparent to-[color-mix(in_srgb,var(--storefront-accent)_45%,transparent)]" />
              <span className="text-[color-mix(in_srgb,var(--storefront-accent)_55%,transparent)] text-[11px]">
                ◆
              </span>
              <div className="h-px w-16 bg-linear-to-l from-transparent to-[color-mix(in_srgb,var(--storefront-accent)_45%,transparent)]" />
            </div>

            {/* 年齢確認ボックス */}
            <div className="border border-[color-mix(in_srgb,var(--storefront-accent)_12%,transparent)] bg-[color-mix(in_srgb,var(--storefront-fg)_2%,transparent)] px-6 py-5 mb-7">
              <p
                className="text-[color-mix(in_srgb,var(--storefront-fg)_85%,transparent)] text-sm font-light mb-1.5 leading-relaxed"
                style={{ fontFamily: 'var(--storefront-font-display)' }}
              >
                このサイトは18歳以上の方のみ閲覧可能です
              </p>
              <p className="text-[color-mix(in_srgb,var(--storefront-muted)_55%,transparent)] text-[10px] tracking-wider">
                This website is restricted to individuals aged 18 and over
              </p>
            </div>

            <p
              className="text-[color-mix(in_srgb,var(--storefront-fg)_65%,transparent)] text-sm font-light mb-1"
              style={{ fontFamily: 'var(--storefront-font-display)' }}
            >
              あなたは18歳以上ですか？
            </p>
            <p className="text-[color-mix(in_srgb,var(--storefront-muted)_45%,transparent)] text-[10px] tracking-wider mb-8">
              Are you 18 years of age or older?
            </p>

            {/* ボタン */}
            <div className="grid grid-cols-2 gap-3">
              <button
                onClick={handleAccept}
                className="py-3.5 border border-[color-mix(in_srgb,var(--storefront-accent)_70%,transparent)] text-[var(--storefront-accent)] text-[10px] tracking-[0.35em] uppercase transition-all duration-300 hover:bg-[var(--storefront-accent)] hover:text-[var(--storefront-bg)] hover:border-[var(--storefront-accent)] font-medium"
              >
                はい &nbsp; YES
              </button>
              <button
                onClick={handleDecline}
                className="py-3.5 border border-[var(--storefront-line)] text-[var(--storefront-neutral)] text-[10px] tracking-[0.35em] uppercase transition-all duration-300 hover:border-[var(--storefront-neutral)] hover:text-[var(--storefront-muted)] font-medium"
              >
                いいえ &nbsp; NO
              </button>
            </div>

            {/* 法的注記 */}
            <p className="text-[var(--storefront-subtle)] text-[9px] mt-8 leading-relaxed tracking-wider">
              日本の法律により、18歳未満の方の閲覧を禁止しています
            </p>
          </>
        ) : (
          /* アクセス拒否状態 */
          <>
            <div className="flex items-center justify-center gap-3 mb-8">
              <div className="h-px w-16 bg-linear-to-r from-transparent to-[color-mix(in_srgb,var(--storefront-danger)_45%,transparent)]" />
              <span className="text-[color-mix(in_srgb,var(--storefront-danger)_55%,transparent)] text-[11px]">
                ◆
              </span>
              <div className="h-px w-16 bg-linear-to-l from-transparent to-[color-mix(in_srgb,var(--storefront-danger)_45%,transparent)]" />
            </div>
            <p
              className="text-[var(--storefront-fg)] text-lg font-light tracking-[0.25em] mb-2"
              style={{ fontFamily: 'var(--storefront-font-display)' }}
            >
              アクセス制限
            </p>
            <p className="text-[color-mix(in_srgb,var(--storefront-muted)_60%,transparent)] text-[9px] tracking-[0.5em] uppercase mb-8">
              Access Restricted
            </p>
            <p
              className="text-[color-mix(in_srgb,var(--storefront-fg)_55%,transparent)] text-sm font-light mb-2 leading-relaxed"
              style={{ fontFamily: 'var(--storefront-font-display)' }}
            >
              18歳未満の方はご利用いただけません
            </p>
            <p className="text-[color-mix(in_srgb,var(--storefront-muted)_45%,transparent)] text-[10px] mb-10 leading-relaxed">
              You must be 18 years or older to access this site
            </p>
            <button
              onClick={() => window.close()}
              className="py-2.5 px-8 border border-[var(--storefront-subtle)] text-[var(--storefront-neutral)] text-[9px] tracking-[0.3em] uppercase transition-all duration-300 hover:border-[var(--storefront-neutral)] hover:text-[var(--storefront-muted)]"
            >
              ウィンドウを閉じる
            </button>
          </>
        )}
      </div>
    </div>
  );
}
