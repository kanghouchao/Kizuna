'use client';

import { useState, useEffect } from 'react';
import { toast } from 'react-hot-toast';
import { systemConfigService } from '@/services/central/config';
import { SystemConfigResponse, SystemConfigUpdateRequest } from '@/types/api';

type ConfigGroup = {
  [category: string]: SystemConfigResponse[];
};

// axios エラーからサーバーのバリデーションメッセージを取り出す
function errorMessage(error: unknown): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const data = (error as { response?: { data?: { error?: string } } }).response?.data;
    if (data?.error) return data.error;
  }
  return '設定の更新に失敗しました';
}

export default function SystemSettingsPage() {
  const [configs, setConfigs] = useState<SystemConfigResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [editValue, setEditValue] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    fetchConfigs();
  }, []);

  const fetchConfigs = async () => {
    try {
      const data = await systemConfigService.getAllConfigs();
      setConfigs(data);
    } catch (error) {
      console.error('設定の取得に失敗しました', error);
      toast.error('設定の取得に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  const saveConfig = async (configKey: string, configValue: string) => {
    const request: SystemConfigUpdateRequest = {
      config_key: configKey,
      config_value: configValue,
    };
    await systemConfigService.updateConfig(request);
    setConfigs(prev =>
      prev.map(c => (c.config_key === configKey ? { ...c, config_value: configValue } : c))
    );
    toast.success('設定を更新しました');
  };

  const handleToggle = async (config: SystemConfigResponse) => {
    setSaving(true);
    try {
      await saveConfig(config.config_key, config.config_value === 'true' ? 'false' : 'true');
    } catch (error) {
      console.error('設定の更新に失敗しました', error);
      toast.error(errorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  const handleEdit = (config: SystemConfigResponse) => {
    setEditingKey(config.config_key);
    // 秘匿設定は現在値を取得できないため空欄から入力する
    setEditValue(config.secret ? '' : config.config_value || '');
  };

  const handleCancel = () => {
    setEditingKey(null);
    setEditValue('');
  };

  const handleSave = async () => {
    if (!editingKey) return;
    setSaving(true);
    try {
      await saveConfig(editingKey, editValue);
      setEditingKey(null);
    } catch (error) {
      console.error('設定の更新に失敗しました', error);
      toast.error(errorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  // カテゴリごとにグループ化
  const groupedConfigs: ConfigGroup = configs.reduce((acc, config) => {
    const category = config.category || 'その他';
    if (!acc[category]) {
      acc[category] = [];
    }
    acc[category].push(config);
    return acc;
  }, {} as ConfigGroup);

  if (loading) {
    return <div className="p-8 text-center text-gray-500">読み込み中...</div>;
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">システム設定</h1>
        <p className="mt-2 text-sm text-gray-600">
          プラットフォーム全体の共通設定を管理します。変更は即座に反映される場合があります。
        </p>
      </div>

      {Object.keys(groupedConfigs).length === 0 ? (
        <div className="bg-white p-8 rounded-lg shadow text-center text-gray-500">
          設定項目がありません。
        </div>
      ) : (
        Object.entries(groupedConfigs).map(([category, items]) => (
          <div
            key={category}
            className="bg-white shadow rounded-lg overflow-hidden border border-gray-200"
          >
            <div className="px-6 py-4 border-b border-gray-200 bg-gray-50 flex items-center justify-between">
              <h3 className="text-lg font-medium text-gray-900">{category}</h3>
              <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                {items.length} 項目
              </span>
            </div>
            <ul className="divide-y divide-gray-200">
              {items.map(config => (
                <li key={config.config_key} className="p-6 hover:bg-gray-50 transition-colors">
                  <div className="flex flex-col md:flex-row md:items-start md:justify-between gap-4">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-gray-900">
                          {config.config_key}
                        </span>
                        {config.description && (
                          <span className="text-xs text-gray-500 ml-2">{config.description}</span>
                        )}
                      </div>

                      {config.value_type === 'BOOLEAN' ? (
                        <div className="mt-2">
                          <button
                            type="button"
                            role="switch"
                            aria-checked={config.config_value === 'true'}
                            aria-label={config.config_key}
                            onClick={() => handleToggle(config)}
                            disabled={saving}
                            className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
                              config.config_value === 'true' ? 'bg-indigo-600' : 'bg-gray-300'
                            }`}
                          >
                            <span
                              className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                                config.config_value === 'true' ? 'translate-x-6' : 'translate-x-1'
                              }`}
                            />
                          </button>
                        </div>
                      ) : editingKey === config.config_key ? (
                        <div className="mt-3">
                          {config.secret ? (
                            <input
                              type="password"
                              value={editValue}
                              onChange={e => setEditValue(e.target.value)}
                              placeholder="新しい値を入力"
                              className="block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border"
                            />
                          ) : config.value_type === 'NUMBER' ? (
                            <input
                              type="number"
                              value={editValue}
                              onChange={e => setEditValue(e.target.value)}
                              className="block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border"
                            />
                          ) : (
                            <textarea
                              value={editValue}
                              onChange={e => setEditValue(e.target.value)}
                              className="block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border"
                              rows={3}
                            />
                          )}
                          <div className="mt-2 flex justify-end gap-2">
                            <button
                              onClick={handleCancel}
                              className="px-3 py-1.5 border border-gray-300 rounded-md text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
                              disabled={saving}
                            >
                              キャンセル
                            </button>
                            <button
                              onClick={handleSave}
                              className="px-3 py-1.5 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50"
                              disabled={saving}
                            >
                              {saving ? '保存中...' : '保存'}
                            </button>
                          </div>
                        </div>
                      ) : (
                        <div
                          className="mt-2 text-sm text-gray-700 break-all font-mono bg-gray-50 p-2 rounded cursor-pointer hover:bg-gray-100 border border-transparent hover:border-gray-300"
                          onClick={() => handleEdit(config)}
                          title="クリックして編集"
                        >
                          {config.secret ? (
                            <span className="text-gray-400 italic">(秘匿設定)</span>
                          ) : (
                            config.config_value || (
                              <span className="text-gray-400 italic">(未設定)</span>
                            )
                          )}
                        </div>
                      )}
                    </div>

                    {config.value_type !== 'BOOLEAN' && editingKey !== config.config_key && (
                      <div className="shrink-0">
                        <button
                          onClick={() => handleEdit(config)}
                          className="text-indigo-600 hover:text-indigo-900 text-sm font-medium"
                        >
                          編集
                        </button>
                      </div>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          </div>
        ))
      )}
    </div>
  );
}
