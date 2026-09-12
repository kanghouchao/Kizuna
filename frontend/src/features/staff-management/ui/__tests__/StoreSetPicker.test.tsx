import { useState } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import type { PlatformStoreScopeType } from '@/entities/user';
import { StoreSetPicker } from '../StoreSetPicker';

it('フォーム文脈なしで特典規則の店舗範囲を選べる', () => {
  const changed = jest.fn();
  function Harness() {
    const [value, setValue] = useState<{
      storeScopeType: PlatformStoreScopeType;
      storeIds: number[];
    }>({ storeScopeType: 'ALL_STORES', storeIds: [] });
    return (
      <StoreSetPicker
        label="適用店舗"
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
  expect(screen.getByRole('group', { name: '適用店舗' })).toBeInTheDocument();
  fireEvent.click(screen.getByLabelText('個別店舗'));
  fireEvent.click(screen.getByLabelText('店舗A'));
  expect(changed).toHaveBeenLastCalledWith({ storeScopeType: 'SPECIFIC_STORES', storeIds: [9] });
  fireEvent.click(screen.getByLabelText('全店舗'));
  expect(changed).toHaveBeenLastCalledWith({ storeScopeType: 'ALL_STORES', storeIds: [] });
});
