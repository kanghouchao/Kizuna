import { getTemplateMeta } from '../model/templateMeta';

describe('getTemplateMeta', () => {
  it('既知の模版キーはそのメタを返すこと', () => {
    expect(getTemplateMeta('default').name).toBe('デフォルト');
  });

  it('未知の模版キーは default にフォールバックすること', () => {
    expect(getTemplateMeta('no-such-template').key).toBe('default');
  });
});
