'use client';

import React, { createContext, useContext } from 'react';
import { platformAuthApi } from '../api/platform';
import Cookies from 'js-cookie';
import { useRouter } from 'next/navigation';
import { clearPlatformSession, loginPath } from '@/shared/lib';

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
    try {
      await platformAuthApi.logout();
    } catch (error) {
      console.error('Logout failed:', error);
    } finally {
      Cookies.remove('token');
      clearPlatformSession();
      router.push(loginPath(reason));
    }
  };

  const value: AuthContextType = {
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
