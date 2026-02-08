'use client';

import Link from 'next/link';
import Image from 'next/image';
import {
  PlusIcon,
  MagnifyingGlassIcon,
  PencilSquareIcon,
  TrashIcon,
} from '@heroicons/react/24/outline';
import { useEffect, useState } from 'react';
import { girlApi } from '@/services/tenant/api';
import { GirlResponse } from '@/types/api';
import { toast } from 'react-hot-toast';

/** キャスト一覧ページ */
export default function GirlListPage() {
  const [girls, setGirls] = useState<GirlResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [search, setSearch] = useState('');

  useEffect(() => {
    fetchGirls();
  }, []);

  /** キャスト一覧を取得する */
  const fetchGirls = async (searchQuery?: string) => {
    try {
      setIsLoading(true);
      const response = await girlApi.list({
        size: 100,
        sort: 'displayOrder,asc',
        search: searchQuery || undefined,
      });
      setGirls(response.content);
    } catch {
      toast.error('キャスト一覧の取得に失敗しました');
    } finally {
      setIsLoading(false);
    }
  };

  /** 検索を実行する */
  const handleSearch = () => {
    fetchGirls(search);
  };

  /** キャストを削除する */
  const handleDelete = async (id: string, name: string) => {
    if (!confirm(`「${name}」を削除しますか？`)) return;
    try {
      await girlApi.delete(id);
      toast.success('キャストを削除しました');
      fetchGirls(search);
    } catch {
      toast.error('キャストの削除に失敗しました');
    }
  };

  /** ステータスの表示ラベルと色を返す */
  const statusLabel = (status: string) => {
    switch (status) {
      case 'ACTIVE':
        return { text: '有効', color: 'bg-green-100 text-green-800' };
      case 'INACTIVE':
        return { text: '無効', color: 'bg-gray-100 text-gray-800' };
      default:
        return { text: status, color: 'bg-gray-100 text-gray-800' };
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">キャスト管理</h1>
          <p className="text-sm text-gray-500 mt-1">キャスト情報の登録・編集ができます。</p>
        </div>
        <Link
          href="/tenant/girls/create"
          className="inline-flex items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700"
        >
          <PlusIcon className="-ml-1 mr-2 h-5 w-5" />
          新規キャスト登録
        </Link>
      </div>

      {/* 検索バー */}
      <div className="bg-white p-4 rounded-lg shadow-sm border border-gray-200 flex items-center space-x-4">
        <div className="flex-1 relative">
          <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
            <MagnifyingGlassIcon className="h-5 w-5 text-gray-400" />
          </div>
          <input
            type="text"
            value={search}
            onChange={e => setSearch(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleSearch()}
            className="block w-full pl-10 pr-3 py-2 border border-gray-300 rounded-md leading-5 bg-white placeholder-gray-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
            placeholder="名前で検索..."
          />
        </div>
        <button
          onClick={handleSearch}
          className="inline-flex items-center px-4 py-2 border border-gray-300 shadow-sm text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50"
        >
          検索
        </button>
      </div>

      {/* テーブル */}
      <div className="bg-white shadow-sm border border-gray-200 rounded-lg overflow-hidden">
        {isLoading ? (
          <div className="p-8 text-center text-gray-500">読み込み中...</div>
        ) : girls.length === 0 ? (
          <div className="p-8 text-center text-gray-500">キャストが登録されていません</div>
        ) : (
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  写真
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  名前
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  年齢
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  スリーサイズ
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  表示順
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  ステータス
                </th>
                <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                  アクション
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {girls.map(girl => {
                const status = statusLabel(girl.status);
                return (
                  <tr key={girl.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="h-12 w-10 rounded overflow-hidden bg-gray-100 relative">
                        {girl.photo_url ? (
                          <Image
                            src={`/api${girl.photo_url}`}
                            alt={girl.name}
                            fill
                            className="object-cover"
                            sizes="40px"
                          />
                        ) : (
                          <div className="flex items-center justify-center h-full text-gray-300 text-xs">
                            No
                          </div>
                        )}
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="text-sm font-medium text-gray-900">{girl.name}</div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                      {girl.age ? `${girl.age}歳` : '-'}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                      {girl.bust && girl.waist && girl.hip
                        ? `B${girl.bust} W${girl.waist} H${girl.hip}`
                        : '-'}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                      {girl.display_order ?? 0}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span
                        className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${status.color}`}
                      >
                        {status.text}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium space-x-3">
                      <Link
                        href={`/tenant/girls/${girl.id}/edit`}
                        className="text-gray-400 hover:text-amber-600 inline-block"
                      >
                        <PencilSquareIcon className="h-5 w-5" />
                      </Link>
                      <button
                        onClick={() => handleDelete(girl.id, girl.name)}
                        className="text-gray-400 hover:text-red-600"
                      >
                        <TrashIcon className="h-5 w-5" />
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
