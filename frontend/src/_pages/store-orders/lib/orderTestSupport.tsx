import { fireEvent, screen } from '@testing-library/react';
import type { OrderPreview } from '@/entities/order';

export const course = {
  service_id: 'course-1',
  revision_id: 'r1',
  revision_number: 1,
  name: '基本',
  duration_minutes: 60,
  price: 12000,
  remuneration: 7000,
  adoption_basis: 'CURRENT_SETTING' as const,
  adopted_at: '2026-09-15T00:00:00Z',
};
export const preview: OrderPreview = {
  confirmation_token: 'confirmed',
  course,
  fee_lines: [],
  total_fee: 12000,
  total_duration_minutes: 60,
  total_remuneration: 7000,
};
export function courseApiMocks() {
  return {
    courseCandidates: jest
      .fn()
      .mockResolvedValue({ rows: [course], page: 0, pageCount: 1, total: 1 }),
    courseRevisions: jest.fn().mockResolvedValue({ content: [course] }),
    previewCreate: jest.fn().mockResolvedValue(preview),
    previewUpdate: jest.fn().mockResolvedValue(preview),
    previewCorrection: jest.fn().mockResolvedValue(preview),
    previewConfirmation: jest.fn().mockResolvedValue(preview),
  };
}
export async function chooseCourse() {
  const picker = await screen
    .findByRole('combobox', { name: 'コース' }, { timeout: 500 })
    .catch(() => null);
  if (!picker) return;
  fireEvent.click(picker);
  const option = await screen.findByRole('option', { name: /基本/ });
  fireEvent.pointerDown(option);
  fireEvent.click(option);
}
export async function confirmPreview() {
  const button = await screen
    .findByRole('button', { name: 'この内容を確認して保存' }, { timeout: 500 })
    .catch(() => null);
  if (button) fireEvent.click(button);
}
export function pointsPreview(points: OrderPreview['points']): OrderPreview {
  return { ...preview, points };
}
