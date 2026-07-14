'use client';

import { PlusIcon } from '@heroicons/react/24/outline';
import { useState } from 'react';
import {
  PlatformStaffResponse,
  PlatformStore,
  platformAuthApi,
  platformStaffApi,
} from '@/entities/user';
import {
  StaffCreateModal,
  StaffEditDrawer,
  staffRoleLabel,
  storeSetLabel,
} from '@/features/staff-management';
import { useManagedList } from '@/shared/lib';

/** スタッフ一覧ページ。一覧内モーダル=新規作成、ドロワー=編集（#325 D6）。 */
export default function StaffPage() {
  const {
    items: staff,
    isLoading,
    refetch,
  } = useManagedList<PlatformStaffResponse>(
    () => platformStaffApi.list(),
    'スタッフ一覧の取得に失敗しました'
  );
  const { items: stores } = useManagedList<PlatformStore>(
    () => platformAuthApi.stores(),
    '店舗一覧の取得に失敗しました'
  );
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<PlatformStaffResponse | null>(null);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">スタッフ管理</h1>
          <p className="mt-1 text-sm text-gray-500">ロール・担当店舗の付与と編集ができます。</p>
        </div>
        <button
          onClick={() => setCreateOpen(true)}
          className="inline-flex items-center gap-1.5 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
        >
          <PlusIcon className="h-5 w-5" />
          スタッフを追加
        </button>
      </div>

      <div className="overflow-hidden rounded-[10px] border border-gray-200 bg-white shadow-sm">
        {isLoading ? (
          <div className="p-8 text-center text-gray-500">読み込み中...</div>
        ) : staff.length === 0 ? (
          <div className="p-8 text-center text-gray-500">スタッフが登録されていません</div>
        ) : (
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  氏名
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  ロール
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  担当店舗
                </th>
                <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                  アクション
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200 bg-white">
              {staff.map(member => (
                <tr
                  key={member.id}
                  className="cursor-pointer hover:bg-gray-50"
                  onClick={() => setEditing(member)}
                >
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                    {member.display_name}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {staffRoleLabel(member.role)}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {storeSetLabel(member.store_scope_type, member.store_ids, stores)}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                    <button
                      onClick={e => {
                        e.stopPropagation();
                        setEditing(member);
                      }}
                      className="rounded text-blue-600 hover:text-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    >
                      編集
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <StaffCreateModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={refetch}
      />
      <StaffEditDrawer
        open={editing !== null}
        staff={editing}
        onClose={() => setEditing(null)}
        onUpdated={refetch}
      />
    </div>
  );
}
