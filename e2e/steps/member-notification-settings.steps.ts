import { expect, type Page } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { loginAsStoreAdmin, loginUser, registerMember } from "./store-api";

const { Given, When, Then } = createBdd();

const MEMBER_PASSWORD = "pass12345";
const SETTINGS_PATH = "/api/me/member/notification-settings";
const BUSINESS = "ご利用に関するお知らせ";
const MARKETING = "キャンペーン・おすすめのご案内";

// 会員は削除 API が無く残留する。タイムスタンプ付きのメールで run を隔離する。
let memberEmail = "";

function purposeGroup(page: Page, name: string) {
  return page.getByRole("radiogroup", { name });
}

Given("通知設定検証用の会員を登録する", async ({ request }) => {
  const suffix = Date.now();
  memberEmail = `member-notification-e2e-${suffix}@kizuna.test`;
  await registerMember(request, memberEmail, MEMBER_PASSWORD, `E2E通知会員-${suffix}`);
});

When("通知設定検証用の会員でログインする", async ({ page }) => {
  await page.getByLabel("メールアドレス", { exact: true }).fill(memberEmail);
  await page.getByLabel("パスワード", { exact: true }).fill(MEMBER_PASSWORD);
  await page.getByRole("button", { name: "ログイン", exact: true }).click();
  await expect(page).toHaveURL(/\/member\/?$/, { timeout: 15000 });
});

When("会員ポータルのホームから通知の設定を開く", async ({ page }) => {
  await page.getByRole("link", { name: "通知の設定" }).click();
  await expect(page).toHaveURL(/\/member\/notification-settings\/?$/, {
    timeout: 15000,
  });
});

Then("業務連絡と販促はどちらもまだ選択されていない", async ({ page }) => {
  for (const name of [BUSINESS, MARKETING]) {
    const group = purposeGroup(page, name);
    await expect(group.getByRole("radio", { name: "受け取る" })).not.toBeChecked({
      timeout: 15000,
    });
    await expect(group.getByRole("radio", { name: "受け取らない" })).not.toBeChecked();
  }
  await expect(page.getByText("まだ選択されていません。")).toHaveCount(2);
});

When("業務連絡を {string} にする", async ({ page }, label: string) => {
  const radio = purposeGroup(page, BUSINESS).getByRole("radio", { name: label });
  await Promise.all([
    page.waitForResponse(
      (resp) =>
        resp.url().includes("/me/member/notification-settings/BUSINESS") &&
        resp.request().method() === "PUT",
    ),
    radio.click(),
  ]);
  await expect(radio).toBeChecked();
  // 直前の選択の成功通知がまだ残っていることがあるので、最後に出たものを見る。
  await expect(page.getByText("通知の設定を保存しました").last()).toBeVisible();
});

Then(
  "業務連絡の設定は {string} で、販促は未確認のまま保存されている",
  async ({ request }, status: string) => {
    const token = await loginUser(request, memberEmail, MEMBER_PASSWORD);
    const res = await request.get(SETTINGS_PATH, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(res.status(), await res.text()).toBe(200);
    expect(await res.json()).toEqual({
      business_status: status,
      marketing_status: "UNKNOWN",
    });
  },
);

Then(
  "店長のトークンでは会員の通知設定の取得も変更も 403 になる",
  async ({ request }) => {
    const headers = { Authorization: `Bearer ${await loginAsStoreAdmin(request)}` };
    const read = await request.get(SETTINGS_PATH, { headers });
    expect(read.status(), await read.text()).toBe(403);
    const write = await request.put(`${SETTINGS_PATH}/BUSINESS`, {
      headers,
      data: { status: "ALLOWED" },
    });
    expect(write.status(), await write.text()).toBe(403);
  },
);
