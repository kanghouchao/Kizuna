import { EMAIL_PATTERN, integerRule } from '../validation';

/**
 * この規則は noValidate で執行が止まった原生 type="email" の代役であり、原生が弾いていたものを
 * 通してしまうと、サーバの @Email から toast で返る——欄の傍に文言を出すという目的の裏返しになる。
 */
describe('EMAIL_PATTERN', () => {
  it.each([
    ['ごく普通の宛先', 'user@example.com'],
    ['副アドレス表記と多段ドメイン', 'user.name+tag@example.co.jp'],
    ['ハイフン入りドメイン', 'user@my-store.example.com'],
  ])('%s は通す（%s）', (_name, address) => {
    expect(EMAIL_PATTERN.test(address)).toBe(true);
  });

  it.each([
    ['ラベルが空のドメイン', 'user@foo..com'],
    ['ドットで始まるドメイン', 'user@.example.com'],
    ['ドットで終わるドメイン', 'user@example.com.'],
    ['下線入りドメイン', 'user@foo_bar.com'],
    ['ハイフンで始まるラベル', 'user@-example.com'],
  ])('原生 type="email" が弾いていた %s は弾く（%s）', (_name, address) => {
    expect(EMAIL_PATTERN.test(address)).toBe(false);
  });

  it.each([
    ['@ が無い', 'no-at-sign'],
    ['ドメインにドットが無い', 'user@localhost'],
    ['局所部が空', '@example.com'],
    ['空白入り', 'user name@example.com'],
  ])('従来どおり %s は弾く（%s）', (_name, address) => {
    expect(EMAIL_PATTERN.test(address)).toBe(false);
  });
});

/**
 * 欄によって「空」の姿が違う（valueAsNumber は NaN、素の register は空文字）ため、
 * どの姿でも通ることを固定する。通さないと required の無い任意欄が空のまま送れなくなる。
 */
describe('integerRule', () => {
  const rule = integerRule('人数');

  it.each([
    ['数値の小数', 1.5],
    ['文字列の小数', '1.5'],
    ['負の小数', -0.5],
  ])('%s は文言を返す（%s）', (_name, value) => {
    expect(rule(value)).toBe('人数は整数で入力してください');
  });

  it.each([
    ['数値の整数', 3],
    ['文字列の整数', '3'],
    ['負の整数（範囲は min の担当）', -2],
    ['零', 0],
  ])('%s は通す（%s）', (_name, value) => {
    expect(rule(value)).toBe(true);
  });

  it.each([
    ['valueAsNumber の空欄', NaN],
    ['素の register の空欄', ''],
    ['未設定', undefined],
    ['null', null],
  ])('%s は通す（未入力を弾くのは required の仕事）', (_name, value) => {
    expect(rule(value)).toBe(true);
  });

  it('文言は欄の名前を名乗る', () => {
    expect(integerRule('表示順')(1.5)).toBe('表示順は整数で入力してください');
  });
});
