import { expect, type APIRequestContext } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { APP_URL } from "../base-url";
import {
  adjustCustomerPoints,
  createCustomer,
  deleteCustomer,
  linkMemberToCustomer,
  loginAsStoreAdmin,
  loginUser,
  registerMember,
  STORE_HEADERS,
  openLoginForm,
} from "./store-api";

const { Given, When, Then, After } = createBdd();

const MEMBER_PASSWORD = "pass12345";
const GRANTED_POINTS = 120;

// 退会済みの MEMBER User は残留する（削除 API が無い）。タイムスタンプ付きメールで run を隔離し、顧客は明示削除する。
let memberEmail = "";
let memberCode = "";
let createdCustomerId = "";

/** 本人のポイント残高と明細（GET /api/me/points/*）。会員トークンで読む。 */
async function memberPoints(request: APIRequestContext, token: string) {
  const headers = { Authorization: `Bearer ${token}` };
  const balance = await request.get("/api/me/points/balance", { headers });
  expect(balance.ok(), await balance.text()).toBeTruthy();
  const entries = await request.get("/api/me/points/entries", { headers });
  expect(entries.ok(), await entries.text()).toBeTruthy();
  return {
    balance: (await balance.json()).balance as number,
    entries: (await entries.json()).content as Array<{
      entry_type: string;
      amount: number;
    }>,
  };
}

async function withdrawViaApi(request: APIRequestContext, token: string) {
  const res = await request.post("/api/me/member/withdrawal", {
    headers: { Authorization: `Bearer ${token}` },
    data: { reason: "e2e の退会検証" },
  });
  expect(res.status(), await res.text()).toBe(204);
}

Given(
  "退会検証用の会員を登録し、店舗側で顧客台帳に紐づけてポイントを付与する",
  async ({ request }) => {
    const suffix = Date.now();
    memberEmail = `member-withdrawal-e2e-${suffix}@kizuna.test`;
    memberCode = await registerMember(
      request,
      memberEmail,
      MEMBER_PASSWORD,
      `E2E退会会員-${suffix}`,
    );
    const adminToken = await loginAsStoreAdmin(request);
    createdCustomerId = await createCustomer(
      request,
      adminToken,
      `E2E退会顧客-${suffix}`,
    );
    await linkMemberToCustomer(
      request,
      adminToken,
      createdCustomerId,
      memberCode,
    );
    await adjustCustomerPoints(
      request,
      adminToken,
      createdCustomerId,
      GRANTED_POINTS,
      "退会検証の付与",
    );
  },
);

When("退会検証用の会員でログインする", async ({ page }) => {
  await page.getByLabel("メールアドレス", { exact: true }).fill(memberEmail);
  await page.getByLabel("パスワード", { exact: true }).fill(MEMBER_PASSWORD);
  await page.getByRole("button", { name: "ログイン", exact: true }).click();
  await expect(page).toHaveURL(/\/member\/?$/, { timeout: 15000 });
});

Then("会員ポータルのホームに退会の導線がある", async ({ page }) => {
  await expect(page.getByRole("link", { name: "退会する" })).toBeVisible({
    timeout: 15000,
  });
});

When("退会画面を開く", async ({ page }) => {
  await page.getByRole("link", { name: "退会する" }).click();
  await expect(page).toHaveURL(/\/member\/withdrawal\/?$/, { timeout: 15000 });
  await expect(
    page.getByRole("heading", { name: "退会すると", level: 2 }),
  ).toBeVisible();
});

Then("確認欄に「退会」と入力するまで退会ボタンは押せない", async ({ page }) => {
  const button = page.getByRole("button", { name: "退会する" });
  await expect(button).toBeDisabled();
  await page.getByLabel("確認のため「退会」と入力").fill("退");
  await expect(button).toBeDisabled();
});

When(
  "理由 {string} と確認語を入力して退会する",
  async ({ page }, reason: string) => {
    await page.getByLabel("退会理由").fill(reason);
    await page.getByLabel("確認のため「退会」と入力").fill("退会");
    const button = page.getByRole("button", { name: "退会する" });
    await expect(button).toBeEnabled();
    await Promise.all([
      page.waitForResponse(
        (resp) =>
          resp.url().endsWith("/me/member/withdrawal") &&
          resp.request().method() === "POST" &&
          resp.status() === 204,
        { timeout: 15000 },
      ),
      button.click(),
    ]);
  },
);

Then("ログイン画面に退会完了の案内が表示される", async ({ page }) => {
  await expect(page).toHaveURL(/\/auth\/login/, { timeout: 15000 });
  await expect(
    page.getByText("退会の手続きが完了しました。ご利用ありがとうございました"),
  ).toBeVisible({ timeout: 15000 });
});

Then("退会した会員の資格情報ではログインできない", async ({ request }) => {
  const res = await request.post("/api/auth/login", {
    data: { email: memberEmail, password: MEMBER_PASSWORD },
  });
  expect(res.status()).toBe(401);
});

Then(
  "退会した会員の顧客台帳との紐づけは解除され顧客行は残る",
  async ({ request }) => {
    const adminToken = await loginAsStoreAdmin(request);
    const res = await request.get(
      `/api/customers/${createdCustomerId}/member-link`,
      { headers: { ...STORE_HEADERS, Authorization: `Bearer ${adminToken}` } },
    );
    // 有効な紐づけが無いときは 404（CustomerMemberLinkService.current）。
    expect(res.status()).toBe(404);
    const customer = await request.get(
      `/api/customers/${createdCustomerId}`,
      { headers: { ...STORE_HEADERS, Authorization: `Bearer ${adminToken}` } },
    );
    expect(customer.ok(), await customer.text()).toBeTruthy();
  },
);

When("退会検証用の会員が API で退会する", async ({ request }) => {
  const token = await loginUser(request, memberEmail, MEMBER_PASSWORD);
  const before = await memberPoints(request, token);
  expect(before.balance).toBe(GRANTED_POINTS);
  await withdrawViaApi(request, token);
  // 旧トークンは同じ取引の停止で即時に失効する。
  const stale = await request.get("/api/me/member", {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(stale.status()).toBe(401);
});

Then(
  "同じメールアドレスで登録すると新しい会員コードが発行され残高は 0 である",
  async ({ request }) => {
    const newCode = await registerMember(
      request,
      memberEmail,
      MEMBER_PASSWORD,
      "E2E再登録会員",
    );
    expect(newCode).not.toBe(memberCode);
    const token = await loginUser(request, memberEmail, MEMBER_PASSWORD);
    const points = await memberPoints(request, token);
    expect(points.balance).toBe(0);
    expect(points.entries).toHaveLength(0);
  },
);

Then("新しい会員の資格情報でログインできる", async ({ page }) => {
  await openLoginForm(page);
  await page.getByLabel("メールアドレス", { exact: true }).fill(memberEmail);
  await page.getByLabel("パスワード", { exact: true }).fill(MEMBER_PASSWORD);
  await page.getByRole("button", { name: "ログイン", exact: true }).click();
  await expect(page).toHaveURL(/\/member\/?$/, { timeout: 15000 });
});

// 後片付けの失敗は握らない — 共有の店舗に顧客を残したまま緑にすると、次の run の断言が残骸で動く。
After(async ({ request }) => {
  const customerId = createdCustomerId;
  memberEmail = "";
  memberCode = "";
  createdCustomerId = "";
  if (customerId) {
    const adminToken = await loginAsStoreAdmin(request);
    await deleteCustomer(request, adminToken, customerId);
  }
});
