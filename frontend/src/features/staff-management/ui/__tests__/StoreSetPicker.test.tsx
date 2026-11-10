import { useState } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import type { StoreScopeType } from '@/entities/user';
import { StoreSetPicker } from '../StoreSetPicker';

type StoreSet = { storeScopeType: StoreScopeType; storeIds: number[] };

function renderPicker(initial: StoreSet) {
  const changed = jest.fn();
  function Harness() {
    const [value, setValue] = useState<StoreSet>(initial);
    return (
      <StoreSetPicker
        stores={[
          { id: 9, name: '店舗A' },
          { id: 10, name: '店舗B' },
        ]}
        isLoading={false}
        failed={false}
        onReload={jest.fn()}
        {...value}
        onChange={next => {
          setValue(next);
          changed(next);
        }}
      />
    );
  }
  render(<Harness />);
  return changed;
}

it('全店舗の選択肢を持たず、店舗を個別に選ぶ', () => {
  const changed = renderPicker({ storeScopeType: 'SPECIFIC_STORES', storeIds: [] });

  expect(screen.getByRole('group', { name: '担当店舗' })).toBeInTheDocument();
  expect(screen.queryByRole('radio')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('checkbox', { name: '店舗A' }));
  expect(changed).toHaveBeenLastCalledWith({ storeScopeType: 'SPECIFIC_STORES', storeIds: [9] });
});

it('すべて選択はその時点の店舗を個別に選ぶ', () => {
  const changed = renderPicker({ storeScopeType: 'SPECIFIC_STORES', storeIds: [] });

  fireEvent.click(screen.getByRole('button', { name: 'すべて選択' }));
  expect(changed).toHaveBeenLastCalledWith({
    storeScopeType: 'SPECIFIC_STORES',
    storeIds: [9, 10],
  });
});

it('全店舗の授権は店舗を選び直すまで保ち、選び直すと個別の店舗に変わる', () => {
  const changed = renderPicker({ storeScopeType: 'ALL_STORES', storeIds: [] });

  expect(screen.getByText(/現在は全店舗/)).toBeInTheDocument();
  expect(changed).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('checkbox', { name: '店舗B' }));
  expect(changed).toHaveBeenLastCalledWith({ storeScopeType: 'SPECIFIC_STORES', storeIds: [10] });
  expect(screen.queryByText(/現在は全店舗/)).not.toBeInTheDocument();
});
