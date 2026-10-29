import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { applicantApi, applicantAttachmentApi } from '@/entities/applicant';
import { hasPermission } from '@/shared/lib';
import ApplicantDetailPage from '../ui/ApplicantDetailPage';
jest.mock('next/navigation', () => ({ useParams: () => ({ id: 'a' }) }));
jest.mock('@/entities/user', () => ({
  ...jest.requireActual('@/entities/user'),
  useStoreContext: () => ({
    stores: [{ id: 1, name: 'テスト店舗' }],
    currentStoreId: '1',
    currentStore: { id: 1, name: 'テスト店舗' },
    loadFailed: false,
    reload: jest.fn(),
    switchStore: jest.fn(),
  }),
}));
jest.mock('@/entities/applicant', () => ({
  ...jest.requireActual('@/entities/applicant'),
  applicantAttachmentApi: { policy: jest.fn() },
  applicantApi: {
    get: jest.fn(),
    policy: jest.fn(),
    history: jest.fn(),
    update: jest.fn(),
    transition: jest.fn(),
    decide: jest.fn(),
  },
}));
jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  readTokenClaims: () => ({}),
  hasPermission: jest.fn(),
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
    retention_periods: null,
  });
  (applicantApi.history as jest.Mock).mockResolvedValue({ rows: [], nextCursor: null });
  (hasPermission as jest.Mock).mockReturnValue(true);
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

test('採否の確定は RECRUITMENT_DECIDE 保持者にだけ出す', async () => {
  (hasPermission as jest.Mock).mockImplementation(
    (_claims: unknown, code: string) => code !== 'RECRUITMENT_DECIDE'
  );
  render(<ApplicantDetailPage />);
  expect(await screen.findByRole('button', { name: '選考状態を変更' })).toBeVisible();
  expect(screen.queryByRole('heading', { name: '採否の確定' })).toBeNull();
  expect(screen.queryByRole('button', { name: '採否を確定' })).toBeNull();
});
test('面接前は不採用だけを選べ、確認後に確定して終了表示へ切り替える', async () => {
  (applicantApi.decide as jest.Mock).mockResolvedValue({
    ...detail,
    status: 'REJECTED',
    version: 1,
  });
  render(<ApplicantDetailPage />);
  fireEvent.click(await screen.findByLabelText('採否'));
  expect(await screen.findByRole('option', { name: '不採用' })).toBeVisible();
  expect(screen.queryByRole('option', { name: '採用' })).toBeNull();
  fireEvent.click(screen.getByRole('option', { name: '不採用' }));
  fireEvent.change(screen.getByLabelText('確定理由'), { target: { value: '書類で見送り' } });
  fireEvent.click(screen.getByRole('button', { name: '採否を確定' }));
  fireEvent.click(await screen.findByRole('button', { name: '確定する' }));
  await waitFor(() =>
    expect(applicantApi.decide).toHaveBeenCalledWith('a', 0, 'REJECTED', '書類で見送り')
  );
  expect(await screen.findByText('選考は終了しました。編集・再開はできません。')).toBeVisible();
  expect(screen.queryByRole('heading', { name: '採否の確定' })).toBeNull();
  expect(screen.queryByRole('button', { name: '受付情報を編集' })).toBeNull();
});
test('面接済なら採用を選べる', async () => {
  (applicantApi.get as jest.Mock).mockResolvedValue({ ...detail, status: 'INTERVIEWED' });
  render(<ApplicantDetailPage />);
  fireEvent.click(await screen.findByLabelText('採否'));
  expect(await screen.findByRole('option', { name: '採用' })).toBeVisible();
  expect(screen.getByRole('option', { name: '不採用' })).toBeVisible();
});
