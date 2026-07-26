import '@testing-library/jest-dom';

// Radix のプリミティブは要素の採寸に ResizeObserver を使う（フォーム内 Checkbox が
// 描画する hidden な BubbleInput など）。jsdom には未実装のため、全テスト共通の
// 最小スタブを供給する。
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver = ResizeObserverStub as unknown as typeof ResizeObserver;

// Radix の Select は content のマウント時 effect で選択済み項目をビューへスクロールする。
// jsdom には scrollIntoView が無く、開き方に依らず同じ effect で落ちるため空実装を与える。
Element.prototype.scrollIntoView = jest.fn();
