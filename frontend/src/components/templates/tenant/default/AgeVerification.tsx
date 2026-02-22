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
    return <div className="fixed inset-0 bg-[#080808] z-[9999]" />;
  }

  return (
    <div
      className="fixed inset-0 z-[9999] flex items-center justify-center p-6"
      style={{
        background: 'radial-gradient(ellipse at 50% 35%, #130d08 0%, #080808 65%)',
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
      <div className="absolute top-8 left-8 w-14 h-14 border-t border-l border-[#C9A84C]/25" />
      <div className="absolute top-8 right-8 w-14 h-14 border-t border-r border-[#C9A84C]/25" />
      <div className="absolute bottom-8 left-8 w-14 h-14 border-b border-l border-[#C9A84C]/25" />
      <div className="absolute bottom-8 right-8 w-14 h-14 border-b border-r border-[#C9A84C]/25" />

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
              className="text-[#C9A84C]/45 text-[9px] tracking-[0.6em] uppercase mb-5"
              style={{ letterSpacing: '0.5em' }}
            >
              Premium Entertainment
            </p>

            {/* 店舗名 */}
            <h1
              className="text-[28px] text-[#F8F4F0] font-light tracking-[0.2em] mb-7"
              style={{ fontFamily: "'Noto Serif JP', 'Hiragino Mincho Pro', 'Yu Mincho', serif" }}
            >
              {storeName}
            </h1>

            {/* ゴールド区切り線 */}
            <div className="flex items-center justify-center gap-3 mb-8">
              <div className="h-px w-16 bg-gradient-to-r from-transparent to-[#C9A84C]/45" />
              <span className="text-[#C9A84C]/55 text-[11px]">◆</span>
              <div className="h-px w-16 bg-gradient-to-l from-transparent to-[#C9A84C]/45" />
            </div>

            {/* 年齢確認ボックス */}
            <div className="border border-[#C9A84C]/12 bg-[#F8F4F0]/[0.02] px-6 py-5 mb-7">
              <p
                className="text-[#F8F4F0]/85 text-sm font-light mb-1.5 leading-relaxed"
                style={{ fontFamily: "'Noto Serif JP', serif" }}
              >
                このサイトは18歳以上の方のみ閲覧可能です
              </p>
              <p className="text-[#A89880]/55 text-[10px] tracking-wider">
                This website is restricted to individuals aged 18 and over
              </p>
            </div>

            <p
              className="text-[#F8F4F0]/65 text-sm font-light mb-1"
              style={{ fontFamily: "'Noto Serif JP', serif" }}
            >
              あなたは18歳以上ですか？
            </p>
            <p className="text-[#A89880]/45 text-[10px] tracking-wider mb-8">
              Are you 18 years of age or older?
            </p>

            {/* ボタン */}
            <div className="grid grid-cols-2 gap-3">
              <button
                onClick={handleAccept}
                className="py-3.5 border border-[#C9A84C]/70 text-[#C9A84C] text-[10px] tracking-[0.35em] uppercase transition-all duration-300 hover:bg-[#C9A84C] hover:text-[#080808] hover:border-[#C9A84C] font-medium"
              >
                はい &nbsp; YES
              </button>
              <button
                onClick={handleDecline}
                className="py-3.5 border border-[#2A2A2A] text-[#484848] text-[10px] tracking-[0.35em] uppercase transition-all duration-300 hover:border-[#484848] hover:text-[#A89880] font-medium"
              >
                いいえ &nbsp; NO
              </button>
            </div>

            {/* 法的注記 */}
            <p className="text-[#252525] text-[9px] mt-8 leading-relaxed tracking-wider">
              日本の法律により、18歳未満の方の閲覧を禁止しています
            </p>
          </>
        ) : (
          /* アクセス拒否状態 */
          <>
            <div className="flex items-center justify-center gap-3 mb-8">
              <div className="h-px w-16 bg-gradient-to-r from-transparent to-[#8B1A2E]/45" />
              <span className="text-[#8B1A2E]/55 text-[11px]">◆</span>
              <div className="h-px w-16 bg-gradient-to-l from-transparent to-[#8B1A2E]/45" />
            </div>
            <p
              className="text-[#F8F4F0] text-lg font-light tracking-[0.25em] mb-2"
              style={{ fontFamily: "'Noto Serif JP', serif" }}
            >
              アクセス制限
            </p>
            <p className="text-[#A89880]/60 text-[9px] tracking-[0.5em] uppercase mb-8">
              Access Restricted
            </p>
            <p
              className="text-[#F8F4F0]/55 text-sm font-light mb-2 leading-relaxed"
              style={{ fontFamily: "'Noto Serif JP', serif" }}
            >
              18歳未満の方はご利用いただけません
            </p>
            <p className="text-[#A89880]/45 text-[10px] mb-10 leading-relaxed">
              You must be 18 years or older to access this site
            </p>
            <button
              onClick={() => window.close()}
              className="py-2.5 px-8 border border-[#252525] text-[#484848] text-[9px] tracking-[0.3em] uppercase transition-all duration-300 hover:border-[#484848] hover:text-[#A89880]"
            >
              ウィンドウを閉じる
            </button>
          </>
        )}
      </div>
    </div>
  );
}
