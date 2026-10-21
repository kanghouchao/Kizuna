'use client';

import { AdminShell } from '@/widgets/admin-shell';
import { Sidebar } from '@/widgets/sidebar';
import { Header } from '@/widgets/header';

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <AdminShell sidebar={<Sidebar />} header={<Header />}>
      {children}
    </AdminShell>
  );
}
