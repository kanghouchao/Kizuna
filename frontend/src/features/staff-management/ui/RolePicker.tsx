'use client';

import { RoleResponse } from '@/entities/user';
import { Label } from '@/shared/ui';

interface RolePickerProps {
  roles: RoleResponse[];
  isLoading: boolean;
  roleIds: number[];
  onChange: (roleIds: number[]) => void;
}

/**
 * ロールのチェックボックス複数選択（ロールはデータ — 選択肢は GET /platform/roles から取得）。
 * 一覧の取得は親（モーダル）が行い、要約表示と選択肢で同じデータを共有する。
 */
export function RolePicker({ roles, isLoading, roleIds, onChange }: RolePickerProps) {
  const toggle = (id: number) => {
    onChange(roleIds.includes(id) ? roleIds.filter(roleId => roleId !== id) : [...roleIds, id]);
  };

  return (
    <div>
      <span className="mb-1 block text-sm font-medium text-foreground">ロール</span>
      <div className="space-y-1 rounded-md border p-3">
        {isLoading ? (
          <p className="text-sm text-muted-foreground">読み込み中...</p>
        ) : (
          roles.map(role => (
            <Label key={role.id} className="font-normal">
              <input
                type="checkbox"
                checked={roleIds.includes(role.id)}
                onChange={() => toggle(role.id)}
              />
              {role.name}
            </Label>
          ))
        )}
      </div>
      <p className="mt-1 text-xs text-muted-foreground">
        1 つ以上を選択してください（兼務は複数選択）。
      </p>
    </div>
  );
}
