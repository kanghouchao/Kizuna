import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { applicantApi, applicantAttachmentApi } from '@/entities/applicant';
import { useMe } from '@/entities/user';
import type { PermissionCode } from '@/entities/user';
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
jest.mock('@/entities/user/model/MeContext', () => ({
  ...jest.requireActual('@/entities/user/model/MeContext'),
  useMe: jest.fn(),
}));
jest.mock('@/shared/notify', () => ({ notify: { success: jest.fn(), error: jest.fn() } }));
const mockedUseMe = useMe as jest.MockedFunction<typeof useMe>;
const meWith = (permissions: PermissionCode[]) =>
  mockedUseMe.mockReturnValue({
    me: { email: 'staff@example.com', user_type: 'STAFF', permissions, line_linked: false },
    isLoading: false,
    failure: null,
    reload: jest.fn(),
    setMe: jest.fn(),
  });
const allPermissions: PermissionCode[] = [
  'RECRUITMENT_VIEW',
  'RECRUITMENT_MANAGE',
  'RECRUITMENT_DECIDE',
  'CAST_MANAGE',
  'RECRUITMENT_ATTACHMENT_VIEW',
  'RECRUITMENT_ATTACHMENT_MANAGE',
];
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
    retention_periods: { rejected_months: null, withdrawn_months: null, hired_months: null },
  });
  (applicantApi.history as jest.Mock).mockResolvedValue({ rows: [], nextCursor: null });
  meWith(allPermissions);
});
test('保持方針は終端状態ごとの月数を示し、未設定は自動匿名化しないと示す', async () => {
  (applicantApi.policy as jest.Mock).mockResolvedValue({
    retention_periods: { rejected_months: 6, withdrawn_months: null, hired_months: 0 },
  });
  render(<ApplicantDetailPage />);
  const section = (await screen.findByRole('heading', { name: '情報保持方針' })).closest(
    'section'
  ) as HTMLElement;
  const value = (label: string) =>
    within(section).getByText(label, { selector: 'dt' }).nextElementSibling;
  await waitFor(() => expect(value('不採用')).toHaveTextContent('保持 6 か月'));
  expect(value('辞退')).toHaveTextContent('自動匿名化しない');
  expect(value('採用')).toHaveTextContent('保持 0 か月');
});
test('保持期間がすべて未設定なら三つとも自動匿名化しないと示す', async () => {
  render(<ApplicantDetailPage />);
  const section = (await screen.findByRole('heading', { name: '情報保持方針' })).closest(
    'section'
  ) as HTMLElement;
  await waitFor(() => expect(within(section).getAllByText('自動匿名化しない')).toHaveLength(3));
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
  meWith(allPermissions.filter(code => code !== 'RECRUITMENT_DECIDE'));
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

test('採用済みで未登録なら CAST_MANAGE 保持者にだけ在籍登録への導線を出す', async () => {
  (applicantApi.get as jest.Mock).mockResolvedValue({
    ...detail,
    status: 'HIRED',
    enrollment_id: null,
  });
  const { unmount } = render(<ApplicantDetailPage />);
  expect(await screen.findByRole('link', { name: '在籍登録' })).toHaveAttribute(
    'href',
    '/store/applicants/a/enrollment'
  );
  unmount();
  meWith(allPermissions.filter(code => code !== 'CAST_MANAGE'));
  render(<ApplicantDetailPage />);
  expect(await screen.findByText('在籍は未登録です。')).toBeVisible();
  expect(screen.queryByRole('link', { name: '在籍登録' })).toBeNull();
});
test('在籍登録済みなら在籍画面へ移動できる', async () => {
  (applicantApi.get as jest.Mock).mockResolvedValue({
    ...detail,
    status: 'HIRED',
    enrollment_id: 'e1',
  });
  render(<ApplicantDetailPage />);
  expect(await screen.findByRole('link', { name: '在籍を開く' })).toHaveAttribute(
    'href',
    '/store/casts/e1/edit'
  );
  expect(screen.queryByRole('link', { name: '在籍登録' })).toBeNull();
});
test('採用前は在籍の欄を出さない', async () => {
  render(<ApplicantDetailPage />);
  expect(await screen.findByRole('button', { name: '選考状態を変更' })).toBeVisible();
  expect(screen.queryByRole('heading', { name: '在籍' })).toBeNull();
});
test('匿名化済みなら匿名化の表示を出し、在籍登録の導線を出さない', async () => {
  (applicantApi.get as jest.Mock).mockResolvedValue({
    ...detail,
    name: '匿名化済み応募者',
    status: 'HIRED',
    enrollment_id: null,
    anonymized_at: '2026-10-30T00:15:00+09:00',
  });
  render(<ApplicantDetailPage />);
  expect(await screen.findByText('匿名化済み')).toBeVisible();
  expect(screen.getByText(/保持期間の満了により .* に匿名化しました/)).toBeVisible();
  expect(
    screen.getByText('在籍は未登録です。匿名化済みのため在籍登録はできません。')
  ).toBeVisible();
  expect(screen.queryByRole('link', { name: '在籍登録' })).toBeNull();
});
test('未匿名化なら匿名化の表示を出さない', async () => {
  render(<ApplicantDetailPage />);
  expect(await screen.findByRole('button', { name: '選考状態を変更' })).toBeVisible();
  expect(screen.queryByText('匿名化済み')).toBeNull();
});
