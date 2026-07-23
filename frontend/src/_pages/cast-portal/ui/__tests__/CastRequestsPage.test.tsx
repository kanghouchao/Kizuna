import { render, screen } from '@testing-library/react';
import { CastRequestsPage } from '../CastRequestsPage';

describe('CastRequestsPage', () => {
  it('準備中プレースホルダを表示する', () => {
    render(<CastRequestsPage />);

    expect(screen.getByText('希望提出は準備中です')).toBeInTheDocument();
  });
});
