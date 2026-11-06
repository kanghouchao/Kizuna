import { surveyApi, Answer, Operation, Revision, Survey, WriteResponse } from '@/entities/survey';
import { useMe } from '@/entities/user';
import type { PermissionCode } from '@/entities/user';
import { storeSessionToken } from '@/shared/api';
import { setCurrentStore } from '@/shared/lib';

export const api = jest.mocked(surveyApi);

export function permission(...permissions: PermissionCode[]) {
  jest.mocked(useMe).mockReturnValue({
    me: {
      email: 'staff@example.com',
      user_type: 'STAFF',
      permissions,
      line_linked: false,
      email_verified: false,
    },
    isLoading: false,
    failure: null,
    reload: jest.fn(),
    setMe: jest.fn(),
  });
}

export const revision: Revision = {
  id: '20',
  survey_id: '10',
  revision_number: 1,
  title: '店舗の問票',
  status: 'OPEN',
  version: 1,
  created_at: '2026-01-01T00:00:00Z',
  opened_at: '2026-01-01T00:00:00Z',
  questions: [
    {
      question_key: 'q1',
      type: 'TEXT',
      prompt: '担当者が入力した質問',
      required: true,
      options: [],
    },
  ],
  created_by: { id: '1', display_name: '担当' },
  retention_policy: 'NOT_CONFIGURED',
};

export const survey: Survey = {
  id: '10',
  latest_revision_id: '20',
  latest_revision_number: 1,
  latest_title: '店舗の問票',
  latest_status: 'OPEN',
  open_revision_id: '20',
  created_at: '2026-01-01T00:00:00Z',
  created_by: { id: '1', display_name: '担当' },
  retention_policy: 'NOT_CONFIGURED',
};

export const answer: Answer = {
  id: '30',
  survey_id: '10',
  revision_id: '20',
  revision_number: 1,
  intake_source: 'STAFF_RECORDED',
  received_via: 'PAPER',
  received_at: '2026-01-01T00:00:00Z',
  created_at: '2026-01-01T00:00:00Z',
  status: 'ACTIVE',
  version: 0,
  answers: [{ question_key: 'q1', text: '保護された原回答' }],
  recorded_by: { id: '1', display_name: '担当' },
  retention_policy: 'NOT_CONFIGURED',
};

export function fixtures() {
  api.list.mockResolvedValue({ rows: [survey], page: 0, pageCount: 1, total: 1 });
  api.survey.mockResolvedValue(survey);
  api.revisions.mockResolvedValue({ rows: [revision], page: 0, pageCount: 1, total: 1 });
  api.revision.mockResolvedValue(revision);
  api.answers.mockResolvedValue({ rows: [answer], page: 0, pageCount: 1, total: 1 });
  api.answer.mockResolvedValue(answer);
  api.counts.mockResolvedValue({
    survey_id: '10',
    revision_id: '20',
    total_records: 1,
    active_records: 1,
    withdrawn_records: 0,
  });
  api.history.mockResolvedValue({ rows: [], page: 0, pageCount: 0, total: 0 });
}

/** 結果不明の操作は、このタブの本人と現在店舗の作用域でだけ起こせる。 */
export function resetSurveyTest() {
  jest.resetAllMocks();
  storeSessionToken({ token: 't', expires_at: 1, user_id: 7, refresh_expires_at: 2 });
  setCurrentStore('1');
  permission('SURVEY_VIEW');
  api.list.mockResolvedValue({ rows: [], page: 0, pageCount: 0, total: 0 });
}

export const written = (
  type: Operation,
  body: { revision: Revision } | { answer: Answer }
): WriteResponse => ({
  ...body,
  operation: { id: '50', type, resource_id: '20', committed_version: 1, replayed: false },
});
