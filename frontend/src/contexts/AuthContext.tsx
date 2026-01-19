'use client';

import React, { createContext, useContext, useEffect } from 'react';
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

  useEffect(() => {
    const checkAuth = async () => {
      if (typeof window === 'undefined') {
        return;
      }

      const token = Cookies.get('token');
      const publicRoutes = ['/login', '/register', '/tenant'];
      const currentPath = window.location.pathname;

      if (!token && !publicRoutes.includes(currentPath)) {
        router.push('/login');
      }
    };
    checkAuth();
  }, [router]);

  const logout = async () => {
    try {
      await getAuthApi().logout();
      router.push('/login');
    } finally {
      Cookies.remove('token');
    }
  };

  const value: AuthContextType = {
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
