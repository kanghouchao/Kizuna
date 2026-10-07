import { apiClient } from '@/shared/api';
import { applicantApi, allowedApplicantTransitions } from '..';
import type { ApplicantIntake } from '..';
jest.mock('@/shared/api', () => ({
  apiClient: { get: jest.fn(), post: jest.fn(), put: jest.fn() },
  fromSpringPage: (data: unknown) => data,
  fromCursorPage: (data: unknown) => data,
}));
const intake: ApplicantIntake = {
  name: '応募者',
  channel: 'PHONE',
  source_type: 'MEDIA',
  source_media: '媒体A',
  referrer: null,
  assignee: null,
  phone: '000',
  email: null,
  address: null,
  experience: null,
  desired_conditions: null,
};
beforeEach(() => {
  jest.clearAllMocks();
  (apiClient.get as jest.Mock).mockResolvedValue({ data: {} });
  (apiClient.post as jest.Mock).mockResolvedValue({ data: {} });
  (apiClient.put as jest.Mock).mockResolvedValue({ data: {} });
});
test('受付と面接と状態変更は明示した版を送る', async () => {
  await applicantApi.create(intake);
  expect(apiClient.post).toHaveBeenCalledWith('/store/applicants', intake);
  await applicantApi.update('a', 4, intake);
  expect(apiClient.put).toHaveBeenCalledWith('/store/applicants/a', { version: 4, intake });
  const interview = {
    interview_at: '2026-10-01T00:00:00Z',
    interviewer: '担当',
    notes: '面接メモ',
    checklist: { 希望確認: true },
  };
  await applicantApi.interview('a', 5, interview);
  expect(apiClient.put).toHaveBeenCalledWith('/store/applicants/a/interview', {
    version: 5,
    ...interview,
  });
  await applicantApi.transition('a', 6, 'WITHDRAWN', '本人の辞退');
  expect(apiClient.post).toHaveBeenCalledWith('/store/applicants/a/transitions', {
    version: 6,
    status: 'WITHDRAWN',
    reason: '本人の辞退',
  });
});
test('一覧条件と履歴cursorを保持する', async () => {
  await applicantApi.list({ page: 2, size: 20, search: '応募者', status: 'SCREENING' });
  expect(apiClient.get).toHaveBeenCalledWith('/store/applicants', {
    params: { page: 2, size: 20, search: '応募者', status: 'SCREENING' },
  });
  await applicantApi.history('a', 'cursor');
  expect(apiClient.get).toHaveBeenCalledWith('/store/applicants/a/history', {
    params: { cursor: 'cursor', size: 20 },
  });
  await applicantApi.get('a');
  await applicantApi.policy();
  expect(apiClient.get).toHaveBeenCalledWith('/store/applicants/policy');
});
test('通常操作は採否に遷移せず終了済みを再開しない', () => {
  expect(allowedApplicantTransitions('RECEIVED')).toEqual(['SCREENING', 'WITHDRAWN']);
  expect(allowedApplicantTransitions('SCREENING')).toEqual(['INTERVIEWED', 'WITHDRAWN']);
  expect(allowedApplicantTransitions('INTERVIEWED')).toEqual(['SCREENING', 'WITHDRAWN']);
  for (const status of ['HIRED', 'REJECTED', 'WITHDRAWN'] as const)
    expect(allowedApplicantTransitions(status)).toEqual([]);
});
