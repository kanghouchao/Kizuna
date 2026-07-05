import { loadTemplatePage } from '../templates/loadTemplate';

// default 模版の各ページは next/headers 依存の Server Component のため実体を読み込まない
jest.mock('../templates/default/page', () => ({
  __esModule: true,
  default: function MockDefaultTop() {
    return null;
  },
}));
jest.mock('../templates/default/casts', () => ({
  __esModule: true,
  default: function MockDefaultCasts() {
    return null;
  },
}));

describe('loadTemplatePage', () => {
  it('存在する templateKey は TOP（page）を返すこと', async () => {
    const component = await loadTemplatePage('default');

    expect(component.name).toBe('MockDefaultTop');
  });

  it('page 引数で模版内の別ページを読み込めること', async () => {
    const component = await loadTemplatePage('default', 'casts');

    expect(component.name).toBe('MockDefaultCasts');
  });

  it('存在しない templateKey は default の同名ページにフォールバックすること', async () => {
    const consoleSpy = jest.spyOn(console, 'error').mockImplementation(() => {});

    const component = await loadTemplatePage('no-such-template', 'casts');

    expect(component.name).toBe('MockDefaultCasts');
    expect(consoleSpy).toHaveBeenCalled();
    consoleSpy.mockRestore();
  });
});
