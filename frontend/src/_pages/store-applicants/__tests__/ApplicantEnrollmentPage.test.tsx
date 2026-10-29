import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { applicantApi } from '@/entities/applicant';
import { notify } from '@/shared/notify';
import ApplicantEnrollmentPage from '../ui/ApplicantEnrollmentPage';
const push = jest.fn();
jest.mock('next/navigation', () => ({
  useParams: () => ({ id: 'a' }),
  useRouter: () => ({ push, back: jest.fn() }),
}));
jest.mock('@/entities/applicant', () => ({
  ...jest.requireActual('@/entities/applicant'),
  applicantApi: { get: jest.fn(), enroll: jest.fn() },
}));
jest.mock('@/shared/notify', () => ({ notify: { success: jest.fn(), error: jest.fn() } }));
const hired = {
  id: 'a',
  name: '非公開の氏名',
  channel: 'WEB',
  source_type: 'DIRECT',
  phone: '090-0000-0000',
  status: 'HIRED',
  version: 3,
  modified_by: 9,
  created_at: '2026-10-01T00:00:00Z',
  updated_at: '2026-10-01T00:00:00Z',
  enrollment_id: null,
};
const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tokyo' }).format(new Date());
beforeEach(() => {
  jest.clearAllMocks();
  (applicantApi.get as jest.Mock).mockResolvedValue(hired);
});
test('源氏名は応募者の氏名から写さず、入店日は当日を既定とする', async () => {
  render(<ApplicantEnrollmentPage />);
  expect(await screen.findByLabelText('源氏名 *')).toHaveValue('');
  expect(screen.getByLabelText('入店日 *')).toHaveValue(today);
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  expect(await screen.findByText('源氏名を入力してください')).toBeVisible();
  expect(applicantApi.enroll).not.toHaveBeenCalled();
});
test('取得した版で登録し、詳細へ戻る', async () => {
  (applicantApi.enroll as jest.Mock).mockResolvedValue({ ...hired, enrollment_id: 'e1' });
  render(<ApplicantEnrollmentPage />);
  fireEvent.change(await screen.findByLabelText('源氏名 *'), { target: { value: 'あおい' } });
  fireEvent.change(screen.getByLabelText('入店日 *'), { target: { value: '2026-11-01' } });
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await waitFor(() =>
    expect(applicantApi.enroll).toHaveBeenCalledWith(
      'a',
      3,
      expect.objectContaining({ name: 'あおい', started_on: '2026-11-01', status: 'ENROLLED' })
    )
  );
  const sent = (applicantApi.enroll as jest.Mock).mock.calls[0][2];
  expect(JSON.stringify(sent)).not.toContain('090');
  expect(notify.success).toHaveBeenCalledWith('在籍を登録しました');
  expect(push).toHaveBeenCalledWith('/store/applicants/a');
});
test('重複登録の拒否は入力を残して通知する', async () => {
  (applicantApi.enroll as jest.Mock).mockRejectedValue({
    isAxiosError: true,
    response: { status: 409, data: { error: 'この応募者は既に在籍登録されています' } },
  });
  render(<ApplicantEnrollmentPage />);
  fireEvent.change(await screen.findByLabelText('源氏名 *'), { target: { value: 'あおい' } });
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await waitFor(() => expect(notify.error).toHaveBeenCalled());
  expect(push).not.toHaveBeenCalled();
  expect(screen.getByLabelText('源氏名 *')).toHaveValue('あおい');
  expect(screen.getByRole('button', { name: '保存する' })).toBeEnabled();
});
test('登録済み・採用前の応募者にはフォームを出さない', async () => {
  (applicantApi.get as jest.Mock).mockResolvedValue({ ...hired, enrollment_id: 'e1' });
  const { unmount } = render(<ApplicantEnrollmentPage />);
  expect(await screen.findByRole('link', { name: '在籍を開く' })).toHaveAttribute(
    'href',
    '/store/casts/e1/edit'
  );
  expect(screen.queryByLabelText('源氏名 *')).toBeNull();
  unmount();
  (applicantApi.get as jest.Mock).mockResolvedValue({ ...hired, status: 'INTERVIEWED' });
  render(<ApplicantEnrollmentPage />);
  expect(await screen.findByText('在籍登録は採用済みの応募者だけに行えます')).toBeVisible();
  expect(screen.queryByLabelText('源氏名 *')).toBeNull();
});
