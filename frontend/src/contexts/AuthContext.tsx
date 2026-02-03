'use client';

import React, { createContext, useContext } from 'react';
import { authApi as centralAuthApi } from '@/services/central/api';
import { authApi as tenantAuthApi } from '@/services/tenant/api';
import Cookies from 'js-cookie';
import { useRouter } from 'next/navigation';
import { isTenantDomain } from '@/lib/config';

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
  const getAuthApi = () => (isTenantDomain() ? tenantAuthApi : centralAuthApi);

  const logout = async () => {
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
