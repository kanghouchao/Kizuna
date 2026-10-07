import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ApplicantIntakeForm } from '../ui/ApplicantIntakeForm';
import { ApplicantInterviewForm } from '../ui/ApplicantInterviewForm';
test('氏名が空なら登録せず関連付けたエラーを表示する', async () => {
  const save = jest.fn();
  render(<ApplicantIntakeForm onSave={save} />);
  fireEvent.click(screen.getByRole('button', { name: '受付情報を保存' }));
  expect(await screen.findByText('氏名を入力してください')).toBeVisible();
  expect(save).not.toHaveBeenCalled();
  expect(screen.getByLabelText('氏名')).toHaveAttribute('aria-invalid', 'true');
});
test('詳細の状態や面接記録を受付更新に混入させない', async () => {
  const save = jest.fn().mockResolvedValue(undefined);
  const detail = {
    name: '応募者',
    channel: 'WEB' as const,
    source_type: 'DIRECT' as const,
    source_media: '',
    referrer: '',
    assignee: '',
    phone: '',
    email: '',
    address: '',
    experience: '',
    desired_conditions: '',
    status: 'HIRED',
    interview: { notes: '機微情報' },
  };
  render(<ApplicantIntakeForm initial={detail} onSave={save} />);
  fireEvent.click(screen.getByRole('button', { name: '受付情報を保存' }));
  await waitFor(() => expect(save).toHaveBeenCalled());
  expect(save.mock.calls[0][0]).not.toHaveProperty('status');
  expect(save.mock.calls[0][0]).not.toHaveProperty('interview');
});
test('面接日時と担当者を要求しチェック項目を追加できる', async () => {
  const save = jest.fn();
  render(<ApplicantInterviewForm onSave={save} />);
  fireEvent.click(screen.getByRole('button', { name: '面接記録を保存' }));
  expect(await screen.findByText('面接日時を入力してください')).toBeVisible();
  expect(await screen.findByText('面接担当者を入力してください')).toBeVisible();
  fireEvent.click(screen.getByRole('button', { name: '確認項目を追加' }));
  expect(screen.getByLabelText('確認項目1の名前')).toBeVisible();
  expect(save).not.toHaveBeenCalled();
});
