'use client';

import { createContext, useContext } from 'react';
import type { Dispatch, SetStateAction } from 'react';
import { useResource } from '@/shared/lib';
import { platformAuthApi } from '../api/platform';
import { PlatformMeResponse } from './types';

/**
 * 自分のプロフィール（/platform/me）の共有 seam。両コンソール layout に1つだけ搭載し、
 * Header とアカウント設定ページが同じ取得結果を消費する（消費者ごとの重複取得を無くす）。
 * 更新の応答は setMe で差し替える — 差し替えは搭載中の全消費者へ届くため、プロフィール
 * 更新後のヘッダー表示が再取得なしで追随する。
 */
interface MeContextValue {
  /** null = 未取得（読み込み中・失敗）。 */
  me: PlatformMeResponse | null;
  isLoading: boolean;
  failure: 'notFound' | 'error' | null;
  /** 取り直す。失敗の再試行もこれ。 */
  reload: () => Promise<void>;
  /**
   * 更新の応答をそのまま反映する差し替え口。取り直しに倒すと、直したばかりの内容が
   * 読み込み表示で一瞬消える。
   */
  setMe: Dispatch<SetStateAction<PlatformMeResponse | null>>;
}

const MeContext = createContext<MeContextValue | undefined>(undefined);

export function useMe(): MeContextValue {
  const context = useContext(MeContext);
  if (context === undefined) {
    throw new Error('useMe must be used within a MeProvider');
  }
  return context;
}

export function MeProvider({ children }: { children: React.ReactNode }) {
  const { data, setData, isLoading, failure, reload } = useResource(() => platformAuthApi.me());

  return (
    <MeContext.Provider value={{ me: data, isLoading, failure, reload, setMe: setData }}>
      {children}
    </MeContext.Provider>
  );
}
