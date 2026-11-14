import { getAllTemplateMetas } from '../model/templateMeta';

describe('getAllTemplateMetas', () => {
  it('3 テンプレートすべてを配列で返すこと', () => {
    expect(getAllTemplateMetas().map(m => m.key)).toEqual(['default', 'modern', 'classic']);
  });

  it('宣言は標識・名前・説明・サムネイルだけを持つこと', () => {
    for (const meta of getAllTemplateMetas()) {
      expect(Object.keys(meta).sort()).toEqual(['description', 'key', 'name', 'thumbnail']);
      expect(meta.name.length).toBeGreaterThan(0);
      expect(meta.description.length).toBeGreaterThan(0);
      expect(meta.thumbnail.length).toBeGreaterThan(0);
    }
  });
});
