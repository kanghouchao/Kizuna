'use client';

import { useState, useEffect } from 'react';
import { toast } from 'react-hot-toast';
import {
  SystemConfigResponse,
  SystemConfigUpdateRequest,
  systemConfigService,
} from '@/entities/system-config';
import { getApiErrorMessage } from '@/shared/lib';
import { Badge, Button, Card, Input, Switch, Textarea } from '@/shared/ui';

type ConfigGroup = {
  [category: string]: SystemConfigResponse[];
};

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
      toast.error(getApiErrorMessage(error, '設定の更新に失敗しました'));
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
      toast.error(getApiErrorMessage(error, '設定の更新に失敗しました'));
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
    return <div className="p-8 text-center text-muted-foreground">読み込み中...</div>;
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-foreground">システム設定</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          プラットフォーム全体の共通設定を管理します。変更は即座に反映される場合があります。
        </p>
      </div>

      {Object.keys(groupedConfigs).length === 0 ? (
        <Card className="p-8 text-center text-muted-foreground">設定項目がありません。</Card>
      ) : (
        Object.entries(groupedConfigs).map(([category, items]) => (
          <Card key={category} className="gap-0 overflow-hidden py-0">
            <div className="px-6 py-4 border-b flex items-center justify-between">
              <h3 className="text-lg font-medium text-foreground">{category}</h3>
              <Badge
                variant="outline"
                className="border-transparent bg-primary/10 text-primary-strong"
              >
                {items.length} 項目
              </Badge>
            </div>
            <ul className="divide-y">
              {items.map(config => (
                <li key={config.config_key} className="p-6">
                  <div className="flex flex-col md:flex-row md:items-start md:justify-between gap-4">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-foreground">
                          {config.config_key}
                        </span>
                        {config.description && (
                          <span className="text-xs text-muted-foreground ml-2">
                            {config.description}
                          </span>
                        )}
                      </div>

                      {config.value_type === 'BOOLEAN' ? (
                        <div className="mt-2">
                          <Switch
                            checked={config.config_value === 'true'}
                            onCheckedChange={() => handleToggle(config)}
                            disabled={saving}
                            aria-label={config.config_key}
                          />
                        </div>
                      ) : editingKey === config.config_key ? (
                        <div className="mt-3">
                          {config.secret ? (
                            <Input
                              type="password"
                              value={editValue}
                              onChange={e => setEditValue(e.target.value)}
                              placeholder="新しい値を入力"
                            />
                          ) : config.value_type === 'NUMBER' ? (
                            <Input
                              type="number"
                              value={editValue}
                              onChange={e => setEditValue(e.target.value)}
                            />
                          ) : (
                            <Textarea
                              value={editValue}
                              onChange={e => setEditValue(e.target.value)}
                              rows={3}
                            />
                          )}
                          <div className="mt-2 flex justify-end gap-2">
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={handleCancel}
                              disabled={saving}
                            >
                              キャンセル
                            </Button>
                            <Button size="sm" onClick={handleSave} disabled={saving}>
                              {saving ? '保存中...' : '保存'}
                            </Button>
                          </div>
                        </div>
                      ) : (
                        <div
                          className="mt-2 text-sm text-foreground break-all font-mono p-2 rounded cursor-pointer border border-transparent hover:border-border"
                          onClick={() => handleEdit(config)}
                          title="クリックして編集"
                        >
                          {config.secret ? (
                            <span className="text-muted-foreground italic">(秘匿設定)</span>
                          ) : (
                            config.config_value || (
                              <span className="text-muted-foreground italic">(未設定)</span>
                            )
                          )}
                        </div>
                      )}
                    </div>

                    {config.value_type !== 'BOOLEAN' && editingKey !== config.config_key && (
                      <div className="shrink-0">
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-primary-strong"
                          onClick={() => handleEdit(config)}
                        >
                          編集
                        </Button>
                      </div>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          </Card>
        ))
      )}
    </div>
  );
}
