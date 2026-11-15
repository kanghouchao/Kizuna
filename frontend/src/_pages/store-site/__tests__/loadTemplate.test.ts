import { loadTemplatePage } from '../templates/loadTemplate';

// 読み込みの分岐だけを確かめるため、ページの実体は読み込まない
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
    const { templateKey, Page } = await loadTemplatePage('default');

    expect(templateKey).toBe('default');
    expect(Page.name).toBe('MockDefaultTop');
  });

  it('page 引数でテンプレート内の別ページを読み込めること', async () => {
    const { Page } = await loadTemplatePage('default', 'casts');

    expect(Page.name).toBe('MockDefaultCasts');
  });

  it('存在しない templateKey は default の同名ページへ落ち、標識も default になること', async () => {
    const consoleSpy = jest.spyOn(console, 'error').mockImplementation(() => {});

    const { templateKey, Page } = await loadTemplatePage('no-such-template', 'casts');

    expect(templateKey).toBe('default');
    expect(Page.name).toBe('MockDefaultCasts');
    expect(consoleSpy).toHaveBeenCalled();
    consoleSpy.mockRestore();
  });
});
