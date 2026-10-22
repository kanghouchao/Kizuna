'use client';

import { AdminShell } from '@/widgets/admin-shell';
import { Sidebar } from '@/widgets/sidebar';
import { Header } from '@/widgets/header';

/**
 * 従業員の管理画面（/admin と /store）を一つのシェルで包む layout。両者を同じ layout の子に置くので、
 * 跨ぐ遷移でシェルが再マウントされず、授権店舗一覧と本人情報を取り直さない。cast / member の portal は
 * この route group の外にあり、それぞれの layout が別のシェルを描く。
 */
export default function StaffLayout({ children }: { children: React.ReactNode }) {
  return (
    <AdminShell sidebar={<Sidebar />} header={<Header />}>
      {children}
    </AdminShell>
  );
}
