import { expect, test, type Page } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import {
  acceptCastInvitation,
  createCourse,
  loginViaUiAndEnterStore,
  createCast,
  createSpecialService,
  issueCastInvitation,
  loginAsStoreAdmin,
  reviseSpecialService,
  openLoginForm,
  storeUrl,
  STORE1_ID,
} from "./store-api";

const { Given, When, Then, After } = createBdd();
let managerPage: Page | undefined;
let specialOrderId = "";
let orderStore = "";
let email = "";
let serviceId = "";
let serviceName = "";
const password = "pass12345";

Given(
  "サービス条件確認用の本人と特殊サービスを用意する",
  async ({ request }) => {
    const suffix = Date.now();
    email = `consent-e2e-${suffix}@kizuna.test`;
    serviceName = `本人条件-${suffix}`;
    const token = await loginAsStoreAdmin(request);
    const enrollment = await createCast(request, token, serviceName);
    const invitation = await issueCastInvitation(request, token, enrollment);
    await acceptCastInvitation(
      request,
      invitation,
      email,
      password,
      serviceName,
    );
    serviceId = await createSpecialService(request, token, serviceName);
  },
);
When("本人がサービス条件を開く", async ({ page }) => {
  await openLoginForm(page);
  await page.getByLabel("メールアドレス", { exact: true }).fill(email);
  await page.getByLabel("パスワード", { exact: true }).fill(password);
  await page.getByRole("button", { name: "ログイン", exact: true }).click();
  await page
    .getByRole("navigation", { name: "メニュー" })
    .getByRole("link", { name: "アカウント", exact: true })
    .click();
  await page.getByRole("link", { name: "サービス条件", exact: true }).click();
  await pageToService(page);
});
// 共有の店舗では他の機能が作ったサービスが先に並ぶため、用意したサービスが載るページまで送る。
// ページ番号は画面の状態にしか無く、再読込で 1 ページ目に戻るので、読み直すたびに送り直す。
async function pageToService(page: Page) {
  const heading = page.getByRole("heading", { name: serviceName, exact: true });
  const next = page
    .getByRole("button", { name: "次へ", exact: true })
    .locator("visible=true");
  await expect(heading.or(next).first()).toBeVisible();
  while (!(await heading.isVisible())) {
    await Promise.all([
      page.waitForResponse((r) => r.url().includes("/me/service-conditions")),
      next.click(),
    ]);
    await expect(heading.or(next).first()).toBeVisible();
  }
}
const serviceCard = (page: Page) =>
  page.getByRole("region", { name: serviceName, exact: true });
Then("特殊サービスのお客様料金とあなたの報酬が表示される", async ({ page }) => {
  const card = serviceCard(page);
  await expect(card).toContainText("お客様料金¥2,000");
  await expect(card).toContainText("あなたの報酬¥1,500");
});
// 確定の前に下部シートが店舗・サービス・金額を復唱することも確かめる
async function decide(page: Page, verb: "受諾する" | "拒否する") {
  await serviceCard(page)
    .getByRole("button", { name: verb, exact: true })
    .click();
  const sheet = page.getByRole("dialog", { name: `サービス条件を${verb}` });
  await expect(sheet).toContainText(serviceName);
  await expect(sheet).toContainText("お客様料金");
  await expect(sheet).toContainText("あなたの報酬¥");
  await sheet
    .getByRole("button", { name: `確認して${verb}`, exact: true })
    .click();
  await expect(sheet).toBeHidden();
}
When("表示条件を確認して特殊サービスを受諾する", async ({ page }) => {
  await decide(page, "受諾する");
});
Then(
  "特殊サービスが「{word}」と表示される",
  async ({ page }, status: string) => {
    await expect(serviceCard(page)).toContainText(status);
    if (status === "受諾待ち")
      await expect(serviceCard(page)).toContainText("条件が変わりました");
    if (status === "拒否済み") {
      await page.screenshot({
        animations: "disabled",
        path: test.info().outputPath("service-consent-light.png"),
      });
      await page.emulateMedia({ colorScheme: "dark" });
      await expect(page.locator("html")).toHaveClass(/dark/);
      await page.screenshot({
        animations: "disabled",
        path: test.info().outputPath("service-consent-dark.png"),
      });
      await page.setViewportSize({ width: 390, height: 844 });
      await page.screenshot({
        animations: "disabled",
        path: test.info().outputPath("service-consent-narrow.png"),
      });
    }
  },
);
When(
  "店長が特殊サービスを無料に改定し本人が再取得する",
  async ({ page, request }) => {
    await reviseSpecialService(
      request,
      await loginAsStoreAdmin(request),
      serviceId,
      serviceName,
    );
    await page.reload();
    await pageToService(page);
  },
);
When("本人が特殊サービスを拒否する", async ({ page }) => {
  await decide(page, "拒否する");
});

When(
  "店長が受諾済み特殊サービスを選び受注を保存する",
  async ({ browser, request }) => {
    const context = await browser.newContext();
    managerPage = await context.newPage();
    orderStore = await loginViaUiAndEnterStore(managerPage);
    const token = await loginAsStoreAdmin(request);
    const courseName = `特殊サービス用コース-${Date.now()}`;
    await createCourse(request, token, courseName, orderStore);
    await managerPage.goto(storeUrl(STORE1_ID, `/orders/create`));
    await managerPage.getByLabel("お客様名", { exact: true }).fill(serviceName);
    await managerPage.getByLabel("キャスト *", { exact: true }).click();
    await managerPage.getByPlaceholder("名前で検索").fill(serviceName);
    await managerPage.getByRole("option", { name: serviceName }).click();
    await managerPage
      .getByRole("combobox", { name: "コース", exact: true })
      .click();
    await managerPage
      .getByLabel("コースを検索", { exact: true })
      .fill(courseName);
    await managerPage
      .getByRole("option", { name: new RegExp(courseName) })
      .click();
    await managerPage
      .getByRole("checkbox", { name: new RegExp(serviceName) })
      .check();
    await managerPage
      .getByRole("button", { name: "登録する", exact: true })
      .click();
    await expect(
      managerPage
        .getByRole("region", { name: "料金明細" })
        .getByRole("row", { name: /^合計（請求額）/ })
        .getByRole("cell")
        .nth(2),
    ).toHaveText("¥14,000");
    const [response] = await Promise.all([
      managerPage.waitForResponse(
        (resp) =>
          resp.url().endsWith("/api/orders") &&
          resp.request().method() === "POST",
      ),
      managerPage
        .getByRole("button", { name: "この内容を確認して保存", exact: true })
        .click(),
    ]);
    expect(response.status()).toBe(201);
    specialOrderId = (await response.json()).id;
  },
);

Then("店長が一覧の要対応から特殊サービスを修復し開始する", async () => {
  const page = managerPage!;
  await page.goto(storeUrl(STORE1_ID, `/orders`));
  const card = page.getByRole("listitem").filter({ hasText: serviceName });
  await expect(card).toContainText("本人拒否・要対応");
  await card.getByRole("button", { name: "編集", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "サービスを開始", exact: true }),
  ).toBeDisabled();
  await page.getByRole("checkbox", { name: new RegExp(serviceName) }).uncheck();
  await page.getByRole("button", { name: "保存", exact: true }).click();
  await expect(
    page
      .getByRole("region", { name: "料金明細" })
      .getByRole("row", { name: /^合計（請求額）/ })
      .getByRole("cell")
      .nth(2),
  ).toHaveText("¥12,000");
  const [updated] = await Promise.all([
    page.waitForResponse(
      (resp) =>
        resp.url().endsWith(`/api/orders/${specialOrderId}`) &&
        resp.request().method() === "PUT",
    ),
    page
      .getByRole("button", { name: "この内容を確認して保存", exact: true })
      .click(),
  ]);
  expect(updated.status()).toBe(200);
  await expect(page).toHaveURL(/\/store\/\d+\/orders\/?$/);
  await page.goto(
    storeUrl(STORE1_ID, `/orders/${specialOrderId}/edit`),
  );
  await expect(page.getByLabel("サービスの進行")).toContainText("処置済み");
  await page
    .getByLabel("開始の理由", { exact: true })
    .fill("修復内容を確認して提供開始");
  await page
    .getByRole("button", { name: "サービスを開始", exact: true })
    .click();
  await expect(page.getByLabel("サービスの進行")).toContainText("サービス中");
});

After(async () => {
  if (managerPage) await managerPage.context().close();
  managerPage = undefined;
});
