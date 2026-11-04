import { expect } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { APP_URL } from "../base-url";
import { latestMailTo } from "./mailpit";

const { Given, When, Then } = createBdd();

// 会員は削除 API が無く残留する。タイムスタンプ付きのメールで run を隔離する。
let memberEmail = "";

// 会員ポータルの画面とその見出し。見出しが見えることで、案内が内容を隠していないことも確かめる。
const PORTAL_PAGES: [path: string, heading: RegExp][] = [
  ["/member/", /さん$/],
  ["/member/points/", /^ポイント$/],
  ["/member/visits/", /^来店履歴$/],
  ["/member/reservations/", /^予約$/],
  ["/member/reservations/new/", /^予約申請$/],
  ["/member/receipts/", /^伝票の取り込み$/],
  ["/member/notification-settings/", /^通知の設定$/],
  ["/member/withdrawal/", /^退会$/],
];

Given("会員登録画面を開く", async ({ page }) => {
  await page.goto("about:blank");
  await page.context().clearCookies({ name: "refresh_token" });
  await page.goto(`${APP_URL}/auth/register`);
});

When("新しいメールアドレスで会員登録する", async ({ page }) => {
  const suffix = Date.now();
  memberEmail = `member-verification-e2e-${suffix}@kizuna.test`;
  await page.getByLabel("メールアドレス", { exact: true }).fill(memberEmail);
  await page.getByLabel("パスワード", { exact: true }).fill("pass12345");
  await page.getByLabel("表示名", { exact: true }).fill(`E2E検証会員-${suffix}`);
  await page.getByRole("button", { name: "登録する", exact: true }).click();
  await expect(page).toHaveURL(/\/member\/?$/, { timeout: 15000 });
});

Then("会員ポータルの全画面に未検証の案内が内容を隠さずに出る", async ({ page }) => {
  for (const [path, heading] of PORTAL_PAGES) {
    await page.goto(`${APP_URL}${path}`);
    const banner = page.getByRole("status").filter({ hasText: "メールアドレスの確認が済んでいません" });
    await expect(banner, path).toBeVisible({ timeout: 15000 });
    await expect(banner, path).toContainText(memberEmail);
    const title = page.getByRole("heading", { level: 1, name: heading });
    await expect(title, path).toBeVisible();
    // 案内は流れの中に置かれ、見出しの上にある（重ねて隠さない）。
    const bannerBox = await banner.boundingBox();
    const titleBox = await title.boundingBox();
    expect(bannerBox!.y + bannerBox!.height, path).toBeLessThanOrEqual(titleBox!.y);
  }
});

Then("登録したメールアドレスに確認画面のリンクつきの検証メールが届く", async ({ request }) => {
  const mail = await latestMailTo(request, memberEmail);
  expect(mail.subject).toBe("メールアドレスの確認のお願い");
  // 接頭辞は文字列のまま比べ、APP_URL を正規表現へ埋め込まない（メタ文字の逃がし漏れが起きない）。
  const prefix = `${APP_URL}/auth/verify-email#`;
  expect(mail.firstLink?.slice(0, prefix.length)).toBe(prefix);
  expect(mail.firstLink?.slice(prefix.length)).toMatch(/^[A-Za-z0-9_-]{43}$/);
});
