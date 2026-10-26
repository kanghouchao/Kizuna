import { expect, type APIRequestContext } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { APP_URL } from "../base-url";
import {
  adjustCustomerPoints,
  createCustomer,
  deleteCustomer,
  linkMemberToCustomer,
  loginAsStoreAdmin,
  registerMember,
} from "./store-api";

const { Given, When, Then, After } = createBdd();

const MEMBER_PASSWORD = "pass12345";
const GRANTED_POINTS = 120;

// 退会済みの MEMBER User は残留する（削除 API が無い）。タイムスタンプ付きメールで run を隔離し、顧客は明示削除する。
let memberEmail = "";
let memberCode = "";
let memberDisplayName = "";
let createdCustomerId = "";

async function searchMembers(page: import("@playwright/test").Page, query: string) {
  await page.getByLabel("検索語").fill(query);
  await page.getByRole("button", { name: "検索", exact: true }).click();
}

Given(
  "コンソール検証用の会員を登録し、店舗側で顧客台帳に紐づけてポイントを付与する",
  async ({ request }) => {
    const suffix = Date.now();
    memberEmail = `member-console-e2e-${suffix}@kizuna.test`;
    memberDisplayName = `E2Eコンソール会員-${suffix}`;
    memberCode = await registerMember(request, memberEmail, MEMBER_PASSWORD, memberDisplayName);
    const adminToken = await loginAsStoreAdmin(request);
    createdCustomerId = await createCustomer(request, adminToken, `E2Eコンソール顧客-${suffix}`);
    await linkMemberToCustomer(request, adminToken, createdCustomerId, memberCode);
    await adjustCustomerPoints(request, adminToken, createdCustomerId, GRANTED_POINTS, "コンソール検証の付与");
  },
);

When("会員一覧をコンソール検証用の会員のメールアドレスで検索する", async ({ page }) => {
  await searchMembers(page, memberEmail);
});

When(
  "会員一覧の状態を {string} に切り替えてコンソール検証用の会員のメールアドレスで検索する",
  async ({ page }, status: string) => {
    // 詳細から戻って一覧で切り替える。状態の切替は検索ボタンを待たずに適用される
    await page.goto(`${APP_URL}/admin/members`);
    await page.getByRole("combobox", { name: "状態" }).click();
    await page.getByRole("option", { name: status, exact: true }).click();
    await searchMembers(page, memberEmail);
  },
);

Then("会員一覧にコンソール検証用の会員が表示される", async ({ page }) => {
  await expect(page.getByRole("link", { name: memberCode, exact: true })).toBeVisible();
  await expect(page.getByText(memberEmail)).toBeVisible();
});

Then("会員一覧に該当する会員がいない", async ({ page }) => {
  await expect(page.getByText("該当する会員がいません")).toBeVisible();
  await expect(page.getByRole("link", { name: memberCode, exact: true })).toHaveCount(0);
});

When("会員一覧からコンソール検証用の会員の詳細を開く", async ({ page }) => {
  await page.getByRole("link", { name: memberCode, exact: true }).click();
  await expect(page).toHaveURL(/\/admin\/members\/\d+$/);
});

Then("会員詳細に残高 120 pt と店舗1の関連が表示される", async ({ page }) => {
  await expect(page.getByRole("heading", { name: "会員詳細" })).toBeVisible();
  await expect(page.getByText(`${GRANTED_POINTS} pt`, { exact: true }).first()).toBeVisible();
  await expect(page.getByText(memberEmail)).toBeVisible();
  // 店舗1の関連（顧客名）。管理者は全店舗の作用域なので担当外の注記は出ない
  await expect(page.getByText(/E2Eコンソール顧客-/)).toBeVisible();
  await expect(page.getByText(/担当外の/)).toHaveCount(0);
});

When(
  "会員詳細で理由 {string} と確認語を入力して退会させる",
  async ({ page }, reason: string) => {
    await page.getByRole("button", { name: "退会させる" }).click();
    const dialog = page.getByRole("dialog");
    const execute = dialog.getByRole("button", { name: "退会させる" });
    await expect(execute).toBeDisabled();
    await dialog.getByLabel("退会理由").fill(reason);
    await dialog.getByLabel("確認のため「退会」と入力").fill("退会");
    await expect(execute).toBeEnabled();
    await execute.click();
    await expect(dialog).toHaveCount(0);
  },
);

Then("会員詳細に退会情報（理由と操作者）が読み取り専用で表示される", async ({ page }) => {
  await expect(page.getByRole("heading", { name: "退会情報" })).toBeVisible();
  await expect(page.getByText("本人からの電話依頼")).toBeVisible();
  // 操作者は統括（初期設定で登録した表示名ではなく、本人ではないことを確かめる）
  await expect(page.getByText("本人", { exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "退会させる" })).toHaveCount(0);
});

Then("会員詳細の明細に退会消去が並ぶ", async ({ page }) => {
  await expect(page.getByText("退会消去", { exact: true })).toBeVisible();
  await expect(page.getByText("-120", { exact: true })).toBeVisible();
});

Then("コンソール検証用の会員の資格情報ではログインできない", async ({ request }) => {
  const response = await request.post("/api/auth/login", {
    data: { email: memberEmail, password: MEMBER_PASSWORD },
  });
  expect(response.status()).toBe(401);
});

async function storeAdminGet(request: APIRequestContext, path: string) {
  const token = await loginAsStoreAdmin(request);
  return request.get(path, { headers: { Authorization: `Bearer ${token}` } });
}

Then("店長のトークンでは会員一覧 API と会員詳細 API が 403 になる", async ({ request }) => {
  const list = await storeAdminGet(request, "/api/members");
  expect(list.status(), await list.text()).toBe(403);
  const detail = await storeAdminGet(request, "/api/members/1");
  expect(detail.status(), await detail.text()).toBe(403);
});

After(async ({ request }) => {
  const customerId = createdCustomerId;
  memberEmail = "";
  memberCode = "";
  memberDisplayName = "";
  createdCustomerId = "";
  if (customerId) {
    const adminToken = await loginAsStoreAdmin(request);
    await deleteCustomer(request, adminToken, customerId);
  }
});
