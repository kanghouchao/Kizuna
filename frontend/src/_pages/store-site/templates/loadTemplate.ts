import type { ComponentType } from 'react';

/**
 * templateKey に対応する模版ページを動的に読み込む共有 helper。
 *
 * 未実装の templateKey（例: 過去に保存された modern / classic）でも公開サイトを
 * 404 にせず default 模版へフォールバックする（issue #223 Phase 1 のバグ修正）。
 * Phase 2 以降、多ページ路由（/casts 等）もこの helper を経由する。
 */
export async function loadTemplatePage(templateKey: string): Promise<ComponentType> {
  try {
    const { default: TemplateComponent } = await import(`./${templateKey}/page`);
    return TemplateComponent;
  } catch (e) {
    console.error(`模版「${templateKey}」の読み込みに失敗したため default を表示します:`, e);
    const { default: DefaultTemplate } = await import('./default/page');
    return DefaultTemplate;
  }
}
