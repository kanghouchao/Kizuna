import { useState } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import type { BenefitRuleStoreScopeType } from '@/entities/benefit-rule';
import { BenefitRuleStorePicker } from '../ui/BenefitRuleStorePicker';

it('フォーム文脈なしで特典規則の適用店舗を選べる', () => {
  const changed = jest.fn();
  function Harness() {
    const [value, setValue] = useState<{
      storeScopeType: BenefitRuleStoreScopeType;
      storeIds: number[];
    }>({ storeScopeType: 'ALL_STORES', storeIds: [] });
    return (
      <BenefitRuleStorePicker
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
  expect(screen.getByRole('group', { name: '適用店舗（発火を拾う範囲）' })).toBeInTheDocument();
  fireEvent.click(screen.getByRole('radio', { name: '個別店舗' }));
  fireEvent.click(screen.getByRole('checkbox', { name: '店舗A' }));
  expect(changed).toHaveBeenLastCalledWith({ storeScopeType: 'SPECIFIC_STORES', storeIds: [9] });
  fireEvent.click(screen.getByRole('radio', { name: '全店舗' }));
  expect(changed).toHaveBeenLastCalledWith({ storeScopeType: 'ALL_STORES', storeIds: [] });
});
