import { cookies } from 'next/headers';
import { notFound, redirect } from 'next/navigation';

export default async function Home() {
  const cookieStore = await cookies();
  const role = cookieStore.get('x-mw-role')?.value;

  // Central ドメインの場合、ログイン状態に応じてリダイレクト
  if (role === 'central') {
    const token = cookieStore.get('token')?.value;
    redirect(token ? '/central/dashboard/' : '/login');
  }

  // Tenant ドメインの場合、常にランディングページを表示
  // ログイン状態に関係なくアクセス可能（将来的に表示内容を変える可能性あり）
  if (role === 'tenant') {
    const templateKey = cookieStore.get('x-mw-tenant-template')?.value || 'default';
    try {
      const { default: TemplateComponent } = await import(
        `@/components/templates/tenant/${templateKey}/page`
      );
      return <TemplateComponent />;
    } catch (e) {
      console.error('Template not found:', e);
      notFound();
    }
  }

  notFound();
}
