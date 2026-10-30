import { render, screen } from '@testing-library/react';
import type { ApplicantDetail } from '@/entities/applicant';
import { ApplicantDecisionForm } from '../ui/ApplicantDecisionForm';
const interview = {
  interview_at: '2026-10-01T00:00:00Z',
  interviewer: '担当',
  notes: null,
  checklist: {},
};
function renderForm(status: ApplicantDetail['status'], withInterview: boolean, canManage = true) {
  const applicant = {
    id: 'a',
    status,
    version: 0,
    interview: withInterview ? interview : null,
  } as ApplicantDetail;
  render(
    <ApplicantDecisionForm
      applicant={applicant}
      canManage={canManage}
      onSave={jest.fn()}
      busy={false}
      onDirtyChange={jest.fn()}
    />
  );
}
test.each([
  ['RECEIVED', false, '面接記録を登録し、選考状態を選考中→面接済に変更すると採用を選べます。'],
  ['RECEIVED', true, '選考状態を選考中→面接済に変更すると採用を選べます。'],
  ['SCREENING', false, '面接記録を登録し、選考状態を面接済に変更すると採用を選べます。'],
  ['SCREENING', true, '選考状態を面接済に変更すると採用を選べます。'],
] as const)('%s（面接記録あり: %s）は採用までの手順を示す', (status, withInterview, steps) => {
  renderForm(status, withInterview);
  expect(
    screen.getByText(
      `採用は面接済の応募者だけに確定できます。${steps}確定後は編集・再開できません。`
    )
  ).toBeVisible();
});
test('面接済では採用を選べるので手順を示さない', () => {
  renderForm('INTERVIEWED', true);
  expect(screen.getByText('確定後は編集・再開できません。')).toBeVisible();
  expect(screen.queryByText(/採用を選べます/)).toBeNull();
});
test('管理権限が無い利用者には手順の操作を担当者に依頼するよう促す', () => {
  renderForm('RECEIVED', false, false);
  expect(
    screen.getByText(
      '採用は面接済の応募者だけに確定できます。面接記録を登録し、選考状態を選考中→面接済に変更すると採用を選べます。これらの操作は採用の管理権限を持つ担当者に依頼してください。確定後は編集・再開できません。'
    )
  ).toBeVisible();
});
