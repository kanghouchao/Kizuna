import { useState } from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { useResourceInitialization } from '../hooks/useResourceInitialization';
import type { ResourceSuccess } from '../hooks/useKeyedResource';

const displayed: Array<{ name: string; reason: string }> = [];

function Form({ success }: { success: ResourceSuccess<{ name: string; reason: string }> | null }) {
  const [values, setValues] = useState({ name: '', reason: '' });
  const ready = useResourceInitialization(success, data => setValues(data));
  if (ready) displayed.push(values);
  return ready ? (
    <>
      <input
        aria-label="名前"
        value={values.name}
        onChange={event => setValues({ ...values, name: event.target.value })}
      />
      <input
        aria-label="理由"
        value={values.reason}
        onChange={event => setValues({ ...values, reason: event.target.value })}
      />
    </>
  ) : (
    <p>読み込み中</p>
  );
}

test('初期表示から値が揃い、同じ成功結果と新しい callback では入力を消さず、新しい結果は全項目を置き換える', () => {
  displayed.length = 0;
  const success = { key: ['item', 1], data: { name: '初期値', reason: '最初の理由' } };
  const { rerender } = render(<Form success={success} />);
  expect(displayed[0]).toEqual({ name: '初期値', reason: '最初の理由' });
  expect(screen.getByLabelText('名前')).toHaveValue('初期値');
  fireEvent.change(screen.getByLabelText('名前'), { target: { value: '編集中' } });
  fireEvent.change(screen.getByLabelText('理由'), { target: { value: '新しい理由' } });
  rerender(<Form success={success} />);
  expect(screen.getByLabelText('名前')).toHaveValue('編集中');
  expect(screen.getByLabelText('理由')).toHaveValue('新しい理由');
  act(() => rerender(<Form success={{ key: ['item', 1], data: { name: '最新', reason: '' } }} />));
  expect(screen.getByLabelText('名前')).toHaveValue('最新');
  expect(screen.getByLabelText('理由')).toHaveValue('');
  rerender(<Form success={null} />);
  expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
});
