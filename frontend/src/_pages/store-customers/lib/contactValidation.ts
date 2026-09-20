import type { ContactType } from '@/entities/customer';

export function validateContactValue(value: string, type: ContactType): true | string {
  if (!value.trim()) return '連絡先を入力してください';
  if (type === 'EMAIL' && !/^[^\s@<>]+@[^\s@<>]+$/.test(value.trim())) {
    return '有効なメールアドレスを入力してください';
  }
  return true;
}
