import type { ReactNode } from 'react';
import { connection } from 'next/server';
import { appDomain } from '@/shared/api/index.server';
import { AppDomainProvider } from '@/shared/lib';

/** 根 layout が包む。appDomain() は動的描画の中で読む必要があるため、先に connection() を待つ。 */
export async function AppDomainScope({ children }: { children: ReactNode }) {
  await connection();
  return <AppDomainProvider appDomain={appDomain()}>{children}</AppDomainProvider>;
}
