import { useState } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import type { StoreScopeType } from '@/entities/user';
import { StoreSetPicker } from '../StoreSetPicker';

it('フォーム文脈なしで授権の店舗集合を選べる', () => {
  const changed = jest.fn();
  function Harness() {
    const [value, setValue] = useState<{
      storeScopeType: StoreScopeType;
      storeIds: number[];
    }>({ storeScopeType: 'ALL_STORES', storeIds: [] });
    return (
      <StoreSetPicker
        stores={[{ id: 9, name: '店舗A' }]}
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
  expect(screen.getByRole('group', { name: '担当店舗' })).toBeInTheDocument();
  fireEvent.click(screen.getByRole('radio', { name: '個別店舗' }));
  fireEvent.click(screen.getByRole('checkbox', { name: '店舗A' }));
  expect(changed).toHaveBeenLastCalledWith({ storeScopeType: 'SPECIFIC_STORES', storeIds: [9] });
  fireEvent.click(screen.getByRole('radio', { name: '全店舗' }));
  expect(changed).toHaveBeenLastCalledWith({ storeScopeType: 'ALL_STORES', storeIds: [] });
});
