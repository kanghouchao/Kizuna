'use client';

import { createContext, useContext, type ReactNode } from 'react';

const AppDomainContext = createContext<string | null>(null);

/** アプリドメインをクライアントへ渡す。値は根 layout が AppDomainScope で供給する。 */
export function AppDomainProvider({
  appDomain,
  children,
}: {
  appDomain: string;
  children: ReactNode;
}) {
  return <AppDomainContext.Provider value={appDomain}>{children}</AppDomainContext.Provider>;
}

export function useAppDomain(): string {
  const appDomain = useContext(AppDomainContext);
  if (appDomain === null) {
    throw new Error('useAppDomain は AppDomainProvider の内側で使う');
  }
  return appDomain;
}
