import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { applicantApi, applicantAttachmentApi } from '@/entities/applicant';
import ApplicantDetailPage from '../ui/ApplicantDetailPage';
jest.mock('next/navigation', () => ({ useParams: () => ({ storeId: '1', id: 'a' }) }));
jest.mock('@/entities/applicant', () => ({
  ...jest.requireActual('@/entities/applicant'),
  applicantAttachmentApi: { policy: jest.fn() },
  applicantApi: {
    get: jest.fn(),
    policy: jest.fn(),
    history: jest.fn(),
    update: jest.fn(),
    transition: jest.fn(),
  },
}));
jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  readTokenClaims: () => ({}),
  hasPermission: () => true,
}));
jest.mock('@/shared/notify', () => ({ notify: { success: jest.fn(), error: jest.fn() } }));
const detail = {
  id: 'a',
  name: '応募者',
  channel: 'WEB',
  source_type: 'DIRECT',
  status: 'RECEIVED',
  version: 0,
  modified_by: 9,
  created_at: '2026-10-01T00:00:00Z',
  updated_at: '2026-10-01T00:00:00Z',
};
beforeEach(() => {
  jest.clearAllMocks();
  (applicantAttachmentApi.policy as jest.Mock).mockResolvedValue({ configured: false });
  (applicantApi.get as jest.Mock).mockResolvedValue(detail);
  (applicantApi.policy as jest.Mock).mockResolvedValue({
    final_decision_configured: false,
    retention_periods: null,
  });
  (applicantApi.history as jest.Mock).mockResolvedValue({ rows: [], nextCursor: null });
});
test('未保存の受付編集を面接編集への移動で黙って破棄しない', async () => {
  render(<ApplicantDetailPage />);
  fireEvent.click(await screen.findByRole('button', { name: '受付情報を編集' }));
  fireEvent.change(screen.getByLabelText('氏名'), { target: { value: '編集中の応募者' } });
  fireEvent.click(screen.getByRole('button', { name: '面接記録を編集' }));
  expect(await screen.findByText('未保存の入力を破棄しますか？')).toBeVisible();
  fireEvent.click(screen.getByRole('button', { name: 'キャンセル' }));
  expect(screen.getByLabelText('氏名')).toHaveValue('編集中の応募者');
});
test('保存中に別編集へ移動させず失敗時の入力を保持する', async () => {
  let reject: (error: Error) => void = () => {};
  (applicantApi.update as jest.Mock).mockImplementation(
    () =>
      new Promise((_, fail) => {
        reject = fail;
      })
  );
  render(<ApplicantDetailPage />);
  fireEvent.click(await screen.findByRole('button', { name: '受付情報を編集' }));
  fireEvent.change(screen.getByLabelText('氏名'), { target: { value: '保存待ちの応募者' } });
  fireEvent.click(screen.getByRole('button', { name: '受付情報を保存' }));
  await waitFor(() => expect(applicantApi.update).toHaveBeenCalled());
  expect(screen.getByRole('button', { name: '面接記録を編集' })).toBeDisabled();
  expect(screen.getByLabelText('氏名')).toBeDisabled();
  await act(async () => reject(new Error('stale')));
  expect(screen.getByLabelText('氏名')).toHaveValue('保存待ちの応募者');
  expect(screen.getByRole('button', { name: '面接記録を編集' })).toBeEnabled();
});

test('状態変更の未保存理由も編集切替前に確認する', async () => {
  render(<ApplicantDetailPage />);
  fireEvent.change(await screen.findByLabelText('変更理由'), { target: { value: '保存前の理由' } });
  fireEvent.click(screen.getByRole('button', { name: '面接記録を編集' }));
  expect(await screen.findByText('未保存の入力を破棄しますか？')).toBeVisible();
  fireEvent.click(screen.getByRole('button', { name: 'キャンセル' }));
  expect(screen.getByLabelText('変更理由')).toHaveValue('保存前の理由');
});
test('状態変更の応答待ちに別の編集を開けず失敗時も理由を保持する', async () => {
  let reject: (error: Error) => void = () => {};
  (applicantApi.transition as jest.Mock).mockImplementation(
    () =>
      new Promise((_, fail) => {
        reject = fail;
      })
  );
  render(<ApplicantDetailPage />);
  fireEvent.click(await screen.findByLabelText('変更先'));
  fireEvent.click(await screen.findByRole('option', { name: '選考中' }));
  fireEvent.change(screen.getByLabelText('変更理由'), { target: { value: '選考理由' } });
  fireEvent.click(screen.getByRole('button', { name: '選考状態を変更' }));
  await waitFor(() => expect(applicantApi.transition).toHaveBeenCalled());
  expect(screen.getByRole('button', { name: '受付情報を編集' })).toBeDisabled();
  expect(screen.getByRole('button', { name: '面接記録を編集' })).toBeDisabled();
  expect(screen.getByRole('button', { name: '再読み込み' })).toBeDisabled();
  expect(screen.getByLabelText('変更理由')).toBeDisabled();
  await act(async () => reject(new Error('stale')));
  expect(screen.getByLabelText('変更理由')).toHaveValue('選考理由');
  expect(screen.getByRole('button', { name: '受付情報を編集' })).toBeEnabled();
});
