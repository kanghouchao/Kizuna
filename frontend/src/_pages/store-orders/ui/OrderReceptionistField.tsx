'use client';

import { useState } from 'react';
import { useParams } from 'next/navigation';
import { useFormContext } from 'react-hook-form';
import { orderApi } from '@/entities/order';
import { useResource } from '@/shared/lib';
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  RegionError,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui';

type OrderReceptionistFieldProps =
  | { scene: 'create' | 'confirm' }
  | { scene: 'edit'; originalId: number | undefined; originalName: string | undefined };

const UNCHANGED = '__unchanged__';
const receptionistLabel = (id: string, name?: string) => name?.trim() || `受付担当 ID: ${id}`;

export function OrderReceptionistField(props: OrderReceptionistFieldProps) {
  const { control } = useFormContext<{ receptionist_id: string }>();
  const { storeId } = useParams();
  const { data, isLoading, failure, reload } = useResource(
    () => orderApi.listReceptionists(),
    [storeId]
  );
  const [selected, setSelected] = useState<{ id: string; label: string } | null>(null);
  const originalId =
    props.scene === 'edit' && props.originalId != null ? String(props.originalId) : '';
  const unchangedLabel =
    props.scene === 'edit'
      ? originalId
        ? '変更しない'
        : '未設定'
      : props.scene === 'create'
        ? '自分（既定）'
        : '未設定（自分が受付なら自動で補われます）';
  const items = [
    { value: UNCHANGED, label: unchangedLabel },
    ...(data ?? [])
      .filter(candidate => candidate.id != null)
      .map(candidate => ({
        value: String(candidate.id),
        label: receptionistLabel(String(candidate.id), candidate.display_name),
      })),
  ];

  return (
    <FormField
      control={control}
      name="receptionist_id"
      render={({ field }) => {
        const unchanged = field.value === originalId || !field.value;
        const label = unchanged
          ? originalId && props.scene === 'edit'
            ? receptionistLabel(originalId, props.originalName)
            : unchangedLabel
          : selected?.id === field.value
            ? selected.label
            : (items.find(item => item.value === field.value)?.label ??
              receptionistLabel(field.value));
        return (
          <FormItem>
            <FormLabel>{props.scene === 'confirm' ? '受付担当' : '受付'}</FormLabel>
            <Select
              items={items}
              value={unchanged ? UNCHANGED : field.value}
              onValueChange={(value, details) => {
                // 候補の背景更新による内部通知は、操作者の担当変更として扱わない。
                if (details.reason !== 'item-press' || value === null) return;
                const item = items.find(item => item.value === value);
                if (!item) return;
                setSelected({ id: value, label: item.label });
                field.onChange(value === UNCHANGED ? originalId : value);
              }}
            >
              <FormControl>
                <SelectTrigger className="w-full min-w-0" onBlur={field.onBlur} ref={field.ref}>
                  <SelectValue>
                    <span className="truncate">{label}</span>
                  </SelectValue>
                </SelectTrigger>
              </FormControl>
              <SelectContent>
                {items.map(item => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {isLoading ? (
              <p className="text-sm text-muted-foreground">読み込み中...</p>
            ) : failure !== null ? (
              <RegionError message="受付担当者の取得に失敗しました" onRetry={() => void reload()} />
            ) : data?.length === 0 ? (
              <p className="text-sm text-muted-foreground">受付担当の候補がいません</p>
            ) : null}
            <FormMessage />
          </FormItem>
        );
      }}
    />
  );
}
