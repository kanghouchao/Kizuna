'use client';

import React, { createContext, useContext } from 'react';
import { authApi } from '../api/auth';
import { isAxiosError } from 'axios';
import { useRouter } from 'next/navigation';
import { broadcastLogout, clearSessionToken, getSessionUserId } from '@/shared/api';
import { clearSession, loginPath } from '@/shared/lib';
import { notify } from '@/shared/notify';

interface AuthContextType {
  /**
   * 理由を渡すと、ログイン画面が着地の理由を名乗る（白名単の理由コード）。
   * 既定の行き先は動かさない — 利用者が自分で押したログアウトが身に覚えのない説明の
   * 画面に着地しないよう、理由は呼び出し側が明示したときだけ載る。
   */
  logout: (reason?: string) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

interface AuthProviderProps {
  children: React.ReactNode;
}

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const router = useRouter();

  const logout = async (reason?: string) => {
    const userId = getSessionUserId();
    try {
      await authApi.logout();
    } catch (error) {
      console.error('Logout failed:', error);
      // 401 はセッションがすでに無いので畳んでよい。それ以外は失効したか分からず、リフレッシュ cookie が残ったまま
      // ログイン画面へ移ると自動着地で元のセッションへ戻る。知らせて、この画面でやり直させる。
      if (!isAxiosError(error) || error.response?.status !== 401) {
        notify.error('ログアウトできませんでした。もう一度お試しください');
        return;
      }
    }
    clearSessionToken();
    clearSession();
    if (userId !== undefined) broadcastLogout(userId);
    router.push(loginPath(reason));
  };

  const value: AuthContextType = {
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
