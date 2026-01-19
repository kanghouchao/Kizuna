'use client';

export default function TenantDashboard() {
  return (
    <div className="min-h-screen bg-white flex flex-col items-center justify-center">
      <div className="max-w-2xl w-full px-6 py-10 rounded-lg shadow-lg bg-gray-50">
        <h1 className="text-3xl font-bold text-indigo-700 mb-4 text-center">
          テナントダッシュボード
        </h1>
        <p className="text-lg text-gray-700 text-center mb-6">ようこそ、someone さん！</p>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div className="bg-white rounded-lg shadow p-6">
            <h2 className="text-xl font-semibold text-indigo-600 mb-2">コンテンツ管理</h2>
            <p className="text-gray-600 text-sm">記事やページの作成・編集・公開ができます。</p>
          </div>
          <div className="bg-white rounded-lg shadow p-6">
            <h2 className="text-xl font-semibold text-indigo-600 mb-2">ユーザー管理</h2>
            <p className="text-gray-600 text-sm">
              テナント内のユーザーの追加・権限設定ができます。
            </p>
          </div>
        </div>
        <div className="mt-8 text-center text-gray-400 text-xs">Powered by oli-CMS</div>
      </div>
    </div>
  );
}
