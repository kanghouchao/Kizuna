import { randomUUID } from "node:crypto";
import { expect, type Page } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { BASE_URL, PLATFORM_URL } from "../base-url";
import {
  acceptCastInvitation,
  createCast,
  issueCastInvitation,
  loginAsStoreAdmin,
  loginViaUiAndEnterStore,
  STORE1_ID,
  getAuthorizedStores,
  loginPlatformUser,
  acceptExistingCastInvitation,
  withdrawCast,
} from "./store-api";

const { Given, When, Then } = createBdd();
let castId: string;
let castName: string;
let email: string;
let password: string;
let invitation: string;
let adminToken: string;

Given("公開切替用のキャスト編集画面を開く", async ({ page, request }) => {
  const store = await loginViaUiAndEnterStore(page);
  adminToken = await loginAsStoreAdmin(request);
  castName = `公開切替-${randomUUID()}`;
  castId = await createCast(request, adminToken, castName);
  await page.goto(`${PLATFORM_URL}/store/${store}/casts/${castId}/edit`);
  await expect(
    page.getByRole("button", { name: "非公開にする" }),
  ).toBeVisible();
});

async function checkStorefront(page: Page, visible: boolean) {
  const storefront = await page.context().newPage();
  try {
    await storefront.goto(BASE_URL);
    const age = storefront.getByRole("button", { name: "はい" });
    if (await age.isVisible()) await age.click();
    // 公開ページの ISR キャッシュ更新後の表示を検証する。
    await expect(async () => {
      await storefront.goto(`${BASE_URL}/casts`);
      await expect(
        storefront.getByRole("heading", { name: "キャスト一覧", exact: true }),
      ).toBeVisible();
      const name = storefront.getByText(castName, { exact: true });
      if (visible) await expect(name.first()).toBeVisible();
      else await expect(name).toHaveCount(0);
    }).toPass({ timeout: 90000, intervals: [2000, 5000, 10000] });
  } finally {
    await storefront.close();
  }
}

When("公開を解除すると店面から非表示になる", async ({ page, $testInfo }) => {
  $testInfo.setTimeout(240000);
  await checkStorefront(page, true);
  await page.getByRole("button", { name: "非公開にする" }).click();
  await expect(
    page.getByText("公開状態: 非公開", { exact: true }),
  ).toBeVisible();
  await checkStorefront(page, false);
});

Then("再公開すると店面に源氏名が表示される", async ({ page }) => {
  await page.getByRole("button", { name: "公開する", exact: true }).click();
  await expect(page.getByText("公開状態: 公開", { exact: true })).toBeVisible();
  await checkStorefront(page, true);
});

Given(
  "二店舗に在籍するキャストと同店の未受諾招待を用意する",
  async ({ request }) => {
    adminToken = await loginAsStoreAdmin(request);
    email = `cast-ui-${randomUUID()}@kizuna.test`;
    password = randomUUID();
    castId = await createCast(request, adminToken, `店舗選択-${randomUUID()}`);
    await acceptCastInvitation(
      request,
      await issueCastInvitation(request, adminToken, castId),
      email,
      password,
      "店舗選択キャスト",
    );
    const token = await loginPlatformUser(request, email, password);
    const stores = await getAuthorizedStores(request, adminToken);
    const secondStore = stores.find((store) => String(store.id) !== STORE1_ID);
    expect(secondStore).toBeDefined();
    const second = await createCast(
      request,
      adminToken,
      "二店舗目の源氏名",
      String(secondStore!.id),
    );
    const secondInvite = await issueCastInvitation(
      request,
      adminToken,
      second,
      String(secondStore!.id),
    );
    await acceptExistingCastInvitation(request, token, secondInvite);
    const duplicate = await createCast(request, adminToken, "再入店の源氏名");
    invitation = await issueCastInvitation(request, adminToken, duplicate);
  },
);

async function acceptThroughUi(page: Page) {
  await page.context().clearCookies();
  await page.goto(
    `${PLATFORM_URL}/platform/invite#${encodeURIComponent(invitation)}`,
  );
  await page.getByRole("button", { name: "既存アカウントでログイン" }).click();
  await page.getByLabel("メールアドレス").fill(email);
  await page.getByLabel("パスワード").fill(password);
  await page.getByRole("button", { name: "ログインして受諾する" }).click();
}

When(
  "同店の招待を既存アカウントで受諾すると競合が表示される",
  async ({ page }) => {
    await acceptThroughUi(page);
    await expect(
      page.getByText("この店舗には既に有効な在籍があります", { exact: true }),
    ).toBeVisible();
    await expect(page.getByLabel("メールアドレス")).toHaveValue(email);
    await expect(
      page.getByRole("button", { name: "ログインして受諾する" }),
    ).toBeEnabled();
  },
);

async function portalStores(page: Page) {
  await page.context().clearCookies();
  await page.goto(`${PLATFORM_URL}/platform/login`);
  await page.getByLabel("メールアドレス").fill(email);
  await page.getByLabel("パスワード").fill(password);
  await page.getByRole("button", { name: "ログイン", exact: true }).click();
  await expect(page).toHaveURL(/\/cast\/schedule\/?$/);
  await page
    .locator("nav")
    .getByRole("link", { name: "希望提出", exact: true })
    .click();
  const select = page.getByRole("combobox", { name: "店舗", exact: true });
  await expect(select).not.toHaveAttribute("data-placeholder");
  await select.click();
}

Then(
  "退店後はポータルの店舗選択からその店舗が消える",
  async ({ page, request }) => {
    await portalStores(page);
    await expect(page.getByRole("option")).toHaveCount(2);
    await withdrawCast(request, adminToken, castId);
    await page.reload();
    await expect(
      page.getByRole("combobox", { name: "店舗", exact: true }),
    ).not.toHaveAttribute("data-placeholder");
    await page.getByRole("combobox", { name: "店舗", exact: true }).click();
    await expect(page.getByRole("option")).toHaveCount(1);
    await expect(
      page.getByRole("option", { name: "Sample Tenant", exact: true }),
    ).toHaveCount(0);
  },
);

Then("同じ招待を受諾するとポータルで二店舗を選択できる", async ({ page }) => {
  await acceptThroughUi(page);
  await expect(
    page.getByRole("heading", { name: "連携が完了しました", exact: true }),
  ).toBeVisible();
  await portalStores(page);
  await expect(page.getByRole("option")).toHaveCount(2);
  await page
    .getByRole("option", { name: "Sample Tenant", exact: true })
    .click();
  await expect(
    page.getByRole("combobox", { name: "店舗", exact: true }),
  ).toContainText("Sample Tenant");
});
