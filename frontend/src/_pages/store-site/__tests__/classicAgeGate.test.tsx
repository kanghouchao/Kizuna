import { render, screen, waitFor } from '@testing-library/react';
import AgeGate from '../templates/classic/AgeGate';

jest.mock('../templates/classic/fonts', () => ({ classicRootClass: 'classic-site' }));

describe('classic の年齢確認', () => {
  beforeEach(() => localStorage.clear());

  it('開いた直後のフォーカスは「いいえ」にあり、Enter 一つで同意にならない', async () => {
    render(<AgeGate storeName="店舗A" />);

    await screen.findByRole('alertdialog', { name: '年齢確認' });

    await waitFor(() => expect(screen.getByRole('button', { name: 'いいえ' })).toHaveFocus());
    expect(screen.getByRole('button', { name: 'はい' })).not.toHaveFocus();
  });
});
