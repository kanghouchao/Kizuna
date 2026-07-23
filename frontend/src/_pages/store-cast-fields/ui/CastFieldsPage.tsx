'use client';

import { PencilSquareIcon, PlusIcon, TrashIcon } from '@heroicons/react/24/outline';
import { useState } from 'react';
import { CastFieldDefinitionResponse, castFieldDefinitionApi } from '@/entities/cast';
import { useManagedList } from '@/shared/lib';
import { toast } from 'react-hot-toast';
import { CastFieldCreateModal } from './CastFieldCreateModal';
import { CastFieldEditModal } from './CastFieldEditModal';

/** キャストのカスタムフィールド定義管理ページ。 */
export default function CastFieldsPage() {
  const {
    items: definitions,
    isLoading,
    refetch,
  } = useManagedList<CastFieldDefinitionResponse>(
    () => castFieldDefinitionApi.list(),
    'フィールド定義一覧の取得に失敗しました'
  );
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<CastFieldDefinitionResponse | null>(null);

  const handleDelete = async (id: string, label: string) => {
    if (!confirm(`「${label}」を削除しますか？`)) return;
    try {
      await castFieldDefinitionApi.delete(id);
      toast.success('フィールドを削除しました');
      void refetch();
    } catch {
      toast.error('フィールドの削除に失敗しました');
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">カスタムフィールド管理</h1>
          <p className="mt-1 text-sm text-gray-500">
            キャストの追加プロフィール項目を定義します。公開設定した項目は公開詳細ページに表示されます。
          </p>
        </div>
        <button
          onClick={() => setCreateOpen(true)}
          className="inline-flex items-center gap-1.5 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
        >
          <PlusIcon className="h-5 w-5" />
          フィールドを追加
        </button>
      </div>

      <div className="overflow-hidden rounded-[10px] border border-gray-200 bg-white shadow-sm">
        {isLoading ? (
          <div className="p-8 text-center text-gray-500">読み込み中...</div>
        ) : definitions.length === 0 ? (
          <div className="p-8 text-center text-gray-500">フィールドが登録されていません</div>
        ) : (
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  key
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  label
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  公開設定
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  表示順
                </th>
                <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                  アクション
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200 bg-white">
              {definitions.map(definition => (
                <tr
                  key={definition.id}
                  className="cursor-pointer hover:bg-gray-50"
                  onClick={() => setEditing(definition)}
                >
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                    {definition.key}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {definition.label}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span
                      className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${
                        definition.is_public
                          ? 'bg-green-100 text-green-800'
                          : 'bg-gray-100 text-gray-800'
                      }`}
                    >
                      {definition.is_public ? '公開' : '非公開'}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {definition.display_order}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium space-x-3">
                    <button
                      onClick={e => {
                        e.stopPropagation();
                        setEditing(definition);
                      }}
                      className="text-gray-400 hover:text-amber-600 focus:outline-none focus:ring-2 focus:ring-blue-500 rounded"
                    >
                      <PencilSquareIcon className="h-5 w-5" />
                    </button>
                    <button
                      onClick={e => {
                        e.stopPropagation();
                        void handleDelete(definition.id, definition.label);
                      }}
                      aria-label="削除"
                      className="text-gray-400 hover:text-red-600 focus:outline-none focus:ring-2 focus:ring-blue-500 rounded"
                    >
                      <TrashIcon className="h-5 w-5" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <CastFieldCreateModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={refetch}
      />
      <CastFieldEditModal
        open={editing !== null}
        definition={editing}
        onClose={() => setEditing(null)}
        onUpdated={refetch}
      />
    </div>
  );
}
