'use client';

import type { Ref } from 'react';
import { useResource } from '@/shared/lib';
import {
  FormControl,
  RegionError,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui';
import { mediumApi } from '../api/medium';
import type { MediumOption } from '../model/types';

/**
 * 記録に付ける媒体の選択欄。FormItem の中で FormLabel / FormMessage と並べて使う。
 *
 * 候補は有効な媒体だけなので、記録が既に持つ停止中の媒体（current）は値を保てるよう「（停止中）」として足す。
 */
export function MediumSelect({
  value,
  onChange,
  current,
  disabled,
  triggerRef,
}: {
  value: string;
  onChange: (value: string) => void;
  current?: MediumOption | null;
  disabled?: boolean;
  triggerRef?: Ref<HTMLButtonElement>;
}) {
  const options = useResource(() => mediumApi.options(), []);
  const items = (options.data ?? []).map(o => ({ value: o.id, label: o.name }));
  if (current && !items.some(o => o.value === current.id))
    items.push({
      value: current.id,
      label: options.data ? `${current.name}（停止中）` : current.name,
    });
  return (
    <>
      <Select
        required
        value={value || null}
        onValueChange={v => onChange(v ?? '')}
        items={items}
        disabled={disabled || options.isLoading}
      >
        <FormControl>
          <SelectTrigger ref={triggerRef} className="w-full min-w-0">
            <SelectValue placeholder={options.isLoading ? '読み込み中...' : '媒体を選択'} />
          </SelectTrigger>
        </FormControl>
        <SelectContent className="max-w-(--available-width)">
          {items.map(o => (
            <SelectItem key={o.value} value={o.value} className="break-all">
              {o.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {options.failure ? (
        <RegionError
          message="媒体の選択肢を取得できませんでした。"
          onRetry={() => void options.reload()}
        />
      ) : (
        options.data &&
        items.length === 0 && (
          <p className="text-sm">
            選択できる媒体がありません。広告費の担当者に媒体の登録を依頼してください。
          </p>
        )
      )}
    </>
  );
}
