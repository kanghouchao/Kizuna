import { cookies } from 'next/headers';
import { notFound, redirect } from 'next/navigation';

export default async function Home() {
  const cookieStore = await cookies();
  const role = cookieStore.get('x-mw-role')?.value;

  // ログイン情報と現在のミドルウェアのロジックに基づいてリダイレクト
  // サービス管理者（Central）の場合、ログイン状態に応じてリダイレクト
  if (role === 'central') {
    const token = cookieStore.get('token')?.value;
    redirect(token ? '/central/dashboard/' : '/login');
  }

  /**
   * テナントユーザーの場合、管理画面かフロントエンドページかを確認
   * ログイン情報がない場合はフロントエンドのホームページを表示
   * ログインしている場合は管理画面（ダッシュボード）へリダイレクト
   */
  if (role === 'tenant') {
    const token = cookieStore.get('token')?.value; // ログイン状態確認のためトークンを取得
    if (!token) {
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
    } else {
      redirect('/tenant/dashboard/'); // ログイン済みの場合は管理画面へ
    }
  }

  notFound();
}
