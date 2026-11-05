import { render, screen } from '@testing-library/react';
import { KeyValueList, KeyValueRow } from '../key-value';

const rowOf = (label: string) => screen.getByText(label).closest('[data-layout]');

describe('KeyValueList', () => {
  it('長い値はラベルの下に置き、短い値はラベルと同じ行に置くこと', () => {
    render(
      <KeyValueList>
        <KeyValueRow label="操作">ROLE_CHANGED</KeyValueRow>
        <KeyValueRow label="理由" long>
          顧客の申し出によりコース時間を訂正。
        </KeyValueRow>
      </KeyValueList>
    );

    expect(rowOf('理由')).toHaveAttribute('data-layout', 'stacked');
    expect(rowOf('操作')).toHaveAttribute('data-layout', 'inline');
  });

  it('ラベルと値を用語と定義の組として描くこと', () => {
    render(
      <KeyValueList>
        <KeyValueRow label="請求総額" money>
          ¥34,800
        </KeyValueRow>
      </KeyValueList>
    );

    expect(screen.getByRole('term')).toHaveTextContent('請求総額');
    expect(screen.getByRole('definition')).toHaveTextContent('¥34,800');
  });
});
