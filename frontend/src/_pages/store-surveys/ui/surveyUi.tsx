import { useEffect, useRef } from 'react';
import { Operation, RevisionStatus, AnswerStatus, ReceivedVia } from '@/entities/survey';
import {
  Button,
  Label,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui';
export const revisionLabels: Record<RevisionStatus, string> = {
  DRAFT: '下書き',
  OPEN: '受付中',
  CLOSED: '受付終了',
};
export const answerLabels: Record<AnswerStatus, string> = {
  ACTIVE: '有効',
  WITHDRAWN: '取り下げ済み',
};
export const viaLabels: Record<ReceivedVia, string> = {
  PAPER: '紙面',
  VERBAL: '口頭',
  EXISTING_RECORD: '既存記録',
};
export const operationLabels: Record<Operation, string> = {
  DRAFT_CREATED: '下書きを作成',
  DRAFT_REPLACED: '下書きを更新',
  OPENED: '受付を開始',
  CLOSED: '受付を終了',
  RECEIVED: '回答を受付',
  WITHDRAWN: '回答を取り下げ',
  CORRECTION_RECEIVED: '訂正回答を受付',
  CORRECTION_LINKED: '訂正先を記録',
};
export const time = (value: string) => new Date(value).toLocaleString('ja-JP');
export function requestKey() {
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), v =>
    v.toString(16).padStart(2, '0')
  ).join('');
}
export function accessError(error: unknown) {
  if (error && typeof error === 'object' && 'response' in error) {
    const status = (error as { response?: { status?: number } }).response?.status;
    if (status === 401 || status === 403) return '現在の閲覧・操作権限を確認してください';
  }
  return null;
}
export function useGuardedRead(onDenied: (message: string) => void) {
  const active = useRef(true);
  const generations = useRef(new Map<string, number>());
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  return async <T,>(fetch: () => Promise<T>, channel = 'main'): Promise<T> => {
    const generation = (generations.current.get(channel) ?? 0) + 1;
    generations.current.set(channel, generation);
    try {
      return await fetch();
    } catch (error) {
      const denied = accessError(error);
      if (active.current && generations.current.get(channel) === generation && denied)
        onDenied(denied);
      throw error;
    }
  };
}
export function Choice({
  id,
  label,
  value,
  items,
  onChange,
  disabled,
  optional = false,
}: {
  id: string;
  label: string;
  value: string;
  items: Record<string, string>;
  onChange: (value: string) => void;
  disabled?: boolean;
  optional?: boolean;
}) {
  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      <Select
        value={value || null}
        onValueChange={v => onChange(v ?? '')}
        items={items}
        disabled={disabled}
      >
        <SelectTrigger id={id}>
          <SelectValue placeholder="選択してください" />
        </SelectTrigger>
        <SelectContent>
          {Object.entries(items).map(([key, text]) => (
            <SelectItem key={key} value={key}>
              {text}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {optional && value && (
        <Button type="button" variant="ghost" disabled={disabled} onClick={() => onChange('')}>
          選択を解除
        </Button>
      )}
    </div>
  );
}
export type View =
  | { kind: 'catalogue' }
  | { kind: 'survey'; sid: string }
  | { kind: 'revision'; sid: string; rid: string }
  | { kind: 'answer'; aid: string };
