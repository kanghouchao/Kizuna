import { loadTemplatePage } from '../templates/loadTemplate';

// default 模版ページは next/headers 依存の Server Component のため実体を読み込まない
jest.mock('../templates/default/page', () => ({
  __esModule: true,
  default: function MockDefaultTemplate() {
    return null;
  },
}));

describe('loadTemplatePage', () => {
  it('存在する templateKey はその模版コンポーネントを返すこと', async () => {
    const component = await loadTemplatePage('default');

    expect(component.name).toBe('MockDefaultTemplate');
  });

  it('存在しない templateKey は default にフォールバックすること（公開站 404 の回帰）', async () => {
    const consoleSpy = jest.spyOn(console, 'error').mockImplementation(() => {});

    const component = await loadTemplatePage('no-such-template');

    expect(component.name).toBe('MockDefaultTemplate');
    expect(consoleSpy).toHaveBeenCalled();
    consoleSpy.mockRestore();
  });
});
