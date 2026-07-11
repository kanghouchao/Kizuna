import { expect } from '@playwright/test';
import { createBdd } from 'playwright-bdd';

const { Given, When, Then } = createBdd();

Given('店舗 {string} の公開サイトを開く', async ({ page }, _store: string) => {
  await page.goto('/');
  await expect(page.getByRole('button', { name: 'はい' })).toBeVisible();
});

When('年齢確認で「いいえ」を選択する', async ({ page }) => {
  await page.getByRole('button', { name: 'いいえ' }).click();
});

Then('アクセス制限画面が表示される', async ({ page }) => {
  // AgeVerification.tsx の拒否状態は見出し要素ではなく <p> で描画されるため text で断言する。
  await expect(page.getByText('アクセス制限', { exact: true })).toBeVisible();
  await expect(page.getByText('18歳未満の方はご利用いただけません', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'ウィンドウを閉じる', exact: true })).toBeVisible();
});

Then('再読込すると年齢確認ゲートが再表示される', async ({ page }) => {
  // 拒否は localStorage に何も書かないため、再読込するとゲートが最初から再表示される
  // （＝承認が記録されていない＝入場していないことの証明）。
  await page.reload();
  await expect(page.getByRole('button', { name: 'はい' })).toBeVisible();
});

When('年齢確認で「はい」を選択する', async ({ page }) => {
  await page.getByRole('button', { name: 'はい' }).click();
  await expect(page.getByRole('button', { name: 'はい' })).toBeHidden();
});

Then('公開サイトに入場できている', async ({ page }) => {
  await expect(page.getByRole('link', { name: 'キャストを見る', exact: true })).toBeVisible();
});
