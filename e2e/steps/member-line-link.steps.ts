import { expect } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { registerMember } from "./store-api";

const { Given, When, Then } = createBdd();

const MEMBER_PASSWORD = "pass12345";

// 会員は削除 API が無く残留する。タイムスタンプ付きのメールで run を隔離する。
let memberEmail = "";

Given("LINE連携検証用の会員を登録する", async ({ request }) => {
  const suffix = Date.now();
  memberEmail = `member-line-e2e-${suffix}@kizuna.test`;
  await registerMember(request, memberEmail, MEMBER_PASSWORD, `E2E LINE会員-${suffix}`);
});

When("LINE連携検証用の会員でログインする", async ({ page }) => {
  await page.getByLabel("メールアドレス", { exact: true }).fill(memberEmail);
  await page.getByLabel("パスワード", { exact: true }).fill(MEMBER_PASSWORD);
  await page.getByRole("button", { name: "ログイン", exact: true }).click();
  await expect(page).toHaveURL(/\/member\/?$/, { timeout: 15000 });
});

When("アカウントのタブから LINE 連携を開く", async ({ page }) => {
  await page.getByRole("navigation", { name: "メニュー" }).getByRole("link", { name: "アカウント" }).click();
  await expect(page).toHaveURL(/\/member\/account\/?$/, { timeout: 15000 });
  await page.getByRole("link", { name: "LINE連携" }).click();
  await expect(page).toHaveURL(/\/member\/account\/line\/?$/, { timeout: 15000 });
  await expect(page.getByRole("heading", { name: "LINE連携", level: 1 })).toBeVisible();
});

// e2e のスタックは LINE のチャネルを設定しない（LINE_CHANNEL_ID が空）ので、公開設定は無効を返す
Then(
  "LINE 連携が無効な環境では連携の操作が出ず、利用できないと表示される",
  async ({ page }) => {
    await expect(page.getByText("この画面からはLINE連携を利用できません。")).toBeVisible({
      timeout: 15000,
    });
    await expect(page.getByRole("button", { name: "LINEを連携する" })).toHaveCount(0);
  },
);

When("戻る導線でアカウントへ戻る", async ({ page }) => {
  await page.getByRole("link", { name: "戻る" }).click();
  await expect(page).toHaveURL(/\/member\/account\/?$/, { timeout: 15000 });
});

Then("アカウントのタブが選ばれている", async ({ page }) => {
  await expect(
    page.getByRole("navigation", { name: "メニュー" }).getByRole("link", { name: "アカウント" }),
  ).toHaveAttribute("aria-current", "page");
});
