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
