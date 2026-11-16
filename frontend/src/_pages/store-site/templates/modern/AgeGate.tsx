'use client';

import type { ReactNode } from 'react';
import { useAgeCheck } from '../../model/useAgeCheck';

/**
 * 年齢確認の画面。同意するまでページの中身を inert にし、キーボードと支援技術から届かなくする。
 * 記録を読む前（loading）は中身を隠すだけで、問いは出さない。
 */
export default function AgeGate({
  storeName,
  children,
}: {
  storeName: string;
  children: ReactNode;
}) {
  const { phase, accept, decline, leave } = useAgeCheck();
  const verified = phase === 'verified';

  return (
    <>
      {!verified && (
        <div
          className="m-gate"
          {...(phase !== 'loading' && {
            role: 'dialog',
            'aria-modal': true,
            'aria-labelledby': 'm-gate-title',
          })}
        >
          {phase !== 'loading' && (
            <div className="grid min-h-full place-items-center px-5 py-10">
              <div className="m-pop relative w-full max-w-sm">
                <span
                  aria-hidden
                  className={`absolute inset-0 translate-x-3 translate-y-3 rotate-2 rounded-[var(--m-radius)] ${
                    phase === 'declined' ? 'm-bg-sky' : 'm-bg-yellow'
                  }`}
                />
                <div className="m-bg-card m-border relative rounded-[var(--m-radius)] px-6 py-9 text-center">
                  {phase === 'gate' ? (
                    <>
                      <p className="m-bg-pink m-border m-display mx-auto grid size-16 place-items-center rounded-full text-2xl">
                        18
                      </p>
                      <p className="m-display mt-5 text-2xl">{storeName}</p>
                      <p className="mt-5 text-sm font-bold">
                        このサイトは18歳以上の方のみ閲覧可能です
                      </p>
                      <h2 id="m-gate-title" className="m-display mt-4 text-xl">
                        あなたは18歳以上ですか？
                      </h2>
                      <div className="mt-6 grid grid-cols-2 gap-3">
                        <button
                          type="button"
                          onClick={accept}
                          className="m-bg-pink m-sticker m-lift rounded-full py-3 font-bold"
                        >
                          はい
                        </button>
                        <button
                          type="button"
                          onClick={decline}
                          className="m-bg-card m-sticker m-lift rounded-full py-3 font-bold"
                        >
                          いいえ
                        </button>
                      </div>
                      <p className="m-sub mt-6 text-xs">
                        日本の法律により、18歳未満の方の閲覧を禁止しています
                      </p>
                    </>
                  ) : (
                    <>
                      <h2 id="m-gate-title" className="m-display text-2xl">
                        アクセス制限
                      </h2>
                      <p className="mt-4 text-sm font-bold">18歳未満の方はご利用いただけません</p>
                      <button
                        type="button"
                        onClick={leave}
                        className="m-bg-card m-sticker m-lift mt-7 rounded-full px-6 py-3 text-sm font-bold"
                      >
                        ウィンドウを閉じる
                      </button>
                    </>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>
      )}
      <div inert={!verified}>{children}</div>
    </>
  );
}
