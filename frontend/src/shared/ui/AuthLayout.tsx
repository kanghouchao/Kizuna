'use client';

import Image from 'next/image';
import '@/styles/auth.css';

interface AuthLayoutProps {
  children: React.ReactNode;
  title: string;
  subtitle: string;
}

/**
 * 認証ページ共有レイアウト
 * 左: ブランドパネル（デスクトップのみ）、右: フォームエリア
 */
export default function AuthLayout({ children, title, subtitle }: AuthLayoutProps) {
  return (
    <div className="min-h-screen flex">
      {/* ブランドパネル（デスクトップ） */}
      <div className="hidden lg:flex lg:w-[44%] xl:w-[42%] auth-panel items-center justify-center p-12 relative">
        {/* 光彩エフェクト */}
        <div className="auth-glow auth-glow--1" />
        <div className="auth-glow auth-glow--2" />
        <div className="auth-glow auth-glow--3" />

        {/* 装飾リング */}
        <div className="auth-ring auth-ring--1" />
        <div className="auth-ring auth-ring--2" />

        {/* ウォーターマーク */}
        <span className="auth-watermark" aria-hidden="true">
          絆
        </span>

        {/* ブランドコンテンツ */}
        <div className="auth-brand text-center">
          <div>
            <Image
              src="/images/logos/192.svg"
              alt="KIZUNA"
              width={72}
              height={72}
              className="mx-auto opacity-90"
              priority
            />
          </div>

          <div className="auth-brand__divider my-8">
            <span className="text-violet-400/50 text-xs">&#9671;</span>
          </div>

          <h1 className="auth-brand__name text-white text-3xl xl:text-4xl">KIZUNA</h1>

          <p className="mt-4 text-violet-300/50 text-sm tracking-[0.2em]">繋がりを、ここから</p>

          <p className="mt-12 text-violet-400/30 text-xs tracking-wider leading-relaxed max-w-[240px] mx-auto">
            マルチ店舗型プラットフォーム
          </p>
        </div>
      </div>

      {/* フォームエリア */}
      <div className="flex-1 flex items-center justify-center auth-form-bg px-6 py-12 sm:px-12">
        <div className="w-full max-w-[420px]">
          {/* モバイル用ブランドバー */}
          <div className="lg:hidden auth-mobile-brand flex items-center justify-center mb-12">
            <Image
              src="/images/logos/32.svg"
              alt="KIZUNA"
              width={28}
              height={28}
              className="mr-3"
            />
            <span className="auth-brand__name text-[#1a1040] text-lg">KIZUNA</span>
          </div>

          {/* ページタイトル */}
          <div className="mb-10">
            <h2 className="text-[26px] font-semibold text-[#1a1040] tracking-tight leading-tight">
              {title}
            </h2>
            <p className="mt-2.5 text-sm text-[#9a958e] leading-relaxed">{subtitle}</p>
          </div>

          {/* フォームコンテンツ（スタガードアニメーション） */}
          <div className="auth-stagger">{children}</div>
        </div>
      </div>
    </div>
  );
}
