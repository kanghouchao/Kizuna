import { render, screen } from '@testing-library/react';
import ReviewsSection from '../templates/_sections/ReviewsSection';
import type { Page } from '@/shared/api';
import { PublicReview } from '../model/types';

function makePage(overrides: Partial<Page<PublicReview>> = {}): Page<PublicReview> {
  return { content: [], total_pages: 0, total_elements: 0, size: 10, number: 0, ...overrides };
}

describe('ReviewsSection', () => {
  it('取得に失敗した場合は空一覧と区別して失敗を知らせ、同じページを読み直す導線を出すこと', () => {
    render(<ReviewsSection reviews={null} page={1} />);

    expect(screen.getByRole('alert')).toHaveTextContent('口コミを読み込めませんでした');
    expect(screen.getByRole('link', { name: '再読み込み' })).toHaveAttribute(
      'href',
      '/reviews?page=2'
    );
    expect(screen.queryByText('掲載中の口コミはありません')).not.toBeInTheDocument();
  });

  it('掲出が 0 件の場合は空状態の文言だけを表示すること', () => {
    render(<ReviewsSection reviews={makePage()} page={0} />);

    expect(screen.getByText('掲載中の口コミはありません')).toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('掲出があるのに範囲外のページを開いた場合は最初のページへ戻る導線を出すこと', () => {
    render(
      <ReviewsSection
        reviews={makePage({ number: 1, total_pages: 1, total_elements: 10 })}
        page={1}
      />
    );

    expect(screen.getByText('このページに口コミはありません')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '最初のページへ' })).toHaveAttribute(
      'href',
      '/reviews'
    );
  });
});
