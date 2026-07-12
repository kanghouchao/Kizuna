'use client';

import React, { createContext, useContext } from 'react';
import { centralAuthApi } from '../api/central';
import { storeAuthApi } from '../api/store';
import { platformAuthApi } from '../api/platform';
import Cookies from 'js-cookie';
import { useRouter } from 'next/navigation';
import { clearPlatformSession, isPlatformSession, isTenantDomain } from '@/shared/lib';

interface AuthContextType {
  logout: () => void;
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
  const getAuthApi = () => (isTenantDomain() ? storeAuthApi : centralAuthApi);

  const logout = async () => {
    if (isPlatformSession()) {
      try {
        await platformAuthApi.logout();
      } catch (error) {
        console.error('Logout failed:', error);
      } finally {
        Cookies.remove('token');
        clearPlatformSession();
        router.push('/platform/login');
      }
      return;
    }

    try {
      await getAuthApi().logout();
    } catch (error) {
      console.error('Logout failed:', error);
    } finally {
      Cookies.remove('token');
      router.push('/login');
    }
  };

  const value: AuthContextType = {
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
