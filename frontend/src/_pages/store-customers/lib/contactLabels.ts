import type { ContactType } from '@/entities/customer';

export const contactLabels: Record<ContactType, string> = {
  PHONE: '電話',
  EMAIL: 'メール',
  LINE: 'LINE ID',
};
