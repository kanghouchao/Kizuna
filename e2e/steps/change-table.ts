import { expect, type Locator } from '@playwright/test';

const escape = (text: string) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/** 変更前後の対照表に「項目・変更前・変更後」がこの順で並ぶ行があることを確かめる。同じ項目は件ごとに出るため値まで含めて一行を特定する。 */
export async function expectChange(scope: Locator, label: string, before: string, after: string) {
  const text = new RegExp(`^${escape(label)}${escape(before)}${escape(after)}$`);
  await expect(scope.getByRole('row').filter({ hasText: text }).first()).toBeVisible();
}
