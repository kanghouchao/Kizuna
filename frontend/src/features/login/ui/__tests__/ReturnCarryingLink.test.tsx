import { render, screen, waitFor } from '@testing-library/react';
import { ReturnCarryingLink } from '../ReturnCarryingLink';

describe('ReturnCarryingLink', () => {
  afterEach(() => {
    window.history.pushState({}, '', '/');
  });

  it('今の URL の next と断片を行き先へ引き継ぐ', async () => {
    // 伝票の QR から来た未登録の人がログイン画面から会員登録へ移っても、登録後に申領画面へ戻れるようにする
    window.history.pushState({}, '', '/auth/login?reason=expired&next=%2Fmember%2Freceipts#tok3n');

    render(<ReturnCarryingLink href="/auth/register">新規会員登録</ReturnCarryingLink>);

    await waitFor(() =>
      expect(screen.getByRole('link', { name: '新規会員登録' })).toHaveAttribute(
        'href',
        '/auth/register?next=%2Fmember%2Freceipts#tok3n'
      )
    );
  });

  it('next が無ければ行き先のまま', () => {
    window.history.pushState({}, '', '/auth/login#tok3n');

    render(<ReturnCarryingLink href="/auth/register">新規会員登録</ReturnCarryingLink>);

    expect(screen.getByRole('link', { name: '新規会員登録' })).toHaveAttribute(
      'href',
      '/auth/register'
    );
  });
});
