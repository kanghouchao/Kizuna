import { useState } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { StoreSetPicker } from '../StoreSetPicker';

function renderPicker(initial: number[]) {
  const changed = jest.fn();
  function Harness() {
    const [storeIds, setStoreIds] = useState<number[]>(initial);
    return (
      <StoreSetPicker
        stores={[
          { id: 9, name: '店舗A' },
          { id: 10, name: '店舗B' },
        ]}
        isLoading={false}
        failed={false}
        onReload={jest.fn()}
        storeIds={storeIds}
        onChange={next => {
          setStoreIds(next);
          changed(next);
        }}
      />
    );
  }
  render(<Harness />);
  return changed;
}

it('全店舗の選択肢を持たず、店舗を個別に選ぶ', () => {
  const changed = renderPicker([]);

  expect(screen.getByRole('group', { name: '担当店舗' })).toBeInTheDocument();
  expect(screen.queryByRole('radio')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('checkbox', { name: '店舗A' }));
  expect(changed).toHaveBeenLastCalledWith([9]);
});

it('すべて選択はその時点の店舗を個別に選び、すべて解除で空の集合に戻す', () => {
  const changed = renderPicker([]);

  fireEvent.click(screen.getByRole('button', { name: 'すべて選択' }));
  expect(changed).toHaveBeenLastCalledWith([9, 10]);
  fireEvent.click(screen.getByRole('button', { name: 'すべて解除' }));
  expect(changed).toHaveBeenLastCalledWith([]);
});
