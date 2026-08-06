import '@testing-library/jest-dom';
import { webcrypto } from 'crypto';
import { TextEncoder } from 'util';

// jsdom の Crypto は getRandomValues のみで SubtleCrypto を持たず、TextEncoder も公開しない。
// PKCE の code_challenge（SHA-256）を検証できるよう Node の実装を補う。
if (!globalThis.crypto) {
  Object.defineProperty(globalThis, 'crypto', { value: webcrypto, configurable: true });
} else if (!globalThis.crypto.subtle) {
  Object.defineProperty(globalThis.crypto, 'subtle', {
    value: webcrypto.subtle,
    configurable: true,
  });
}
if (!globalThis.TextEncoder) {
  Object.defineProperty(globalThis, 'TextEncoder', { value: TextEncoder, configurable: true });
}

// プリミティブは要素の採寸に ResizeObserver を使う。jsdom には未実装のため、
// 全テスト共通の最小スタブを供給する。
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver = ResizeObserverStub as unknown as typeof ResizeObserver;

// Select は popup のマウント時 effect で選択済み項目をビューへスクロールする。
// jsdom には scrollIntoView が無く、開き方に依らず同じ effect で落ちるため空実装を与える。
Element.prototype.scrollIntoView = jest.fn();

// Switch / Checkbox は引き金のクリックを隠し input へ PointerEvent として中継する
// （修飾キーの状態を運ぶため click() ではなく構築したイベントを使う）。jsdom は
// PointerEvent を実装しないので、構築だけ通るよう MouseEvent で代用する。
if (!globalThis.PointerEvent) {
  globalThis.PointerEvent =
    class PointerEventStub extends MouseEvent {} as unknown as typeof PointerEvent;
}
