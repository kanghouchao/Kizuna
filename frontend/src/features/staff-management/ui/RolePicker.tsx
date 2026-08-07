'use client';

import { useId, type Ref } from 'react';
import { RoleSummaryResponse } from '@/entities/user';
import {
  FormControl,
  FormDescription,
  FormItem,
  FormMessage,
  Label,
  RegionError,
} from '@/shared/ui';

interface RolePickerProps {
  roles: RoleSummaryResponse[];
  isLoading: boolean;
  /** 目録の取得に失敗した状態。選択肢が無いのが事実なのか読めなかったのかを区別する。 */
  failed: boolean;
  /** 目録の取り直し。 */
  onReload: () => void;
  roleIds: number[];
  onChange: (roleIds: number[]) => void;
  /**
   * 検証で最初の問題になったときに焦点を受ける先。handleSubmit は登録済みの ref を焦点にするため、
   * 値だけを受け取った組は他の症状を出さずに焦点移動だけを失う。
   */
  ref?: Ref<HTMLInputElement>;
}

/**
 * ロールのチェックボックス複数選択（ロールはデータ — 選択肢は GET /platform/roles から取得）。
 * 一覧の取得は親（モーダル）が行い、要約表示と選択肢で同じデータを共有する。
 *
 * 必須は「N のうち 1 つ以上」＝組の性質で、各項目に aria-required を撒くと「全部入れよ」に
 * なってしまう。要求は組の見出しと説明が担い、指摘も個々の項目ではなく組に紐づく。
 * そのため FormField の render の中でしか描けない（外では FormMessage が文脈を失う）。
 */
export function RolePicker({
  roles,
  isLoading,
  failed,
  onReload,
  roleIds,
  onChange,
  ref,
}: RolePickerProps) {
  const labelId = useId();
  const toggle = (id: number) => {
    onChange(roleIds.includes(id) ? roleIds.filter(roleId => roleId !== id) : [...roleIds, id]);
  };
  // 焦点は組の先頭が受ける
  const firstId = roles.find(role => role.id !== undefined)?.id;

  return (
    <FormItem className="gap-1">
      <span id={labelId} className="block text-sm font-medium text-foreground">
        ロール
      </span>
      <FormControl>
        <div role="group" aria-labelledby={labelId} className="space-y-1 rounded-md border p-3">
          {isLoading ? (
            <p className="text-sm text-muted-foreground">読み込み中...</p>
          ) : failed ? (
            // 空の組を見せると「ロールが 1 つも無い」と読めてしまう。読めなかったことを名乗る。
            // 送信は塞がない — 選べないまま提出すれば、上の rules が欄の傍で止める。
            <RegionError message="ロール一覧の取得に失敗しました" onRetry={onReload} />
          ) : (
            roles.map(role => {
              const id = role.id;
              if (id === undefined) return null;
              return (
                <Label key={id} className="font-normal">
                  <input
                    type="checkbox"
                    checked={roleIds.includes(id)}
                    onChange={() => toggle(id)}
                    ref={id === firstId ? ref : undefined}
                  />
                  {role.name}
                </Label>
              );
            })
          )}
        </div>
      </FormControl>
      <FormDescription className="text-xs">
        1 つ以上を選択してください（兼務は複数選択）。
      </FormDescription>
      <FormMessage />
    </FormItem>
  );
}
