import { getAllTemplateMetas, getTemplateMeta } from '../model/templateMeta';

describe('getTemplateMeta', () => {
  it('既知の模版キーはそのメタを返すこと', () => {
    expect(getTemplateMeta('default').name).toBe('デフォルト');
  });

  it('modern 模版キーはそのメタを返すこと', () => {
    expect(getTemplateMeta('modern').name).toBe('モダン');
  });

  it('未知の模版キーは default にフォールバックすること', () => {
    expect(getTemplateMeta('no-such-template').key).toBe('default');
  });

  it('modern 模版はカード表示用の description / thumbnail を持つこと', () => {
    const meta = getTemplateMeta('modern');
    expect(meta.description.length).toBeGreaterThan(0);
    expect(meta.thumbnail.length).toBeGreaterThan(0);
  });
});

describe('getAllTemplateMetas', () => {
  it('3 模版すべてを配列で返すこと', () => {
    expect(getAllTemplateMetas().map(m => m.key)).toEqual(['default', 'modern', 'classic']);
  });
});
