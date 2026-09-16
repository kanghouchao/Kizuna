import { expect, test, type Page } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { PLATFORM_URL } from "../base-url";
import {
  acceptCastInvitation,
  createCourse,
  loginViaUiAndEnterStore,
  createCast,
  createSpecialService,
  issueCastInvitation,
  loginAsStoreAdmin,
  reviseSpecialService,
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
  await page.goto(`${PLATFORM_URL}/platform/login`);
  await page.getByLabel("メールアドレス", { exact: true }).fill(email);
  await page.getByLabel("パスワード", { exact: true }).fill(password);
  await page.getByRole("button", { name: "ログイン", exact: true }).click();
  await page.getByRole("link", { name: "サービス条件", exact: true }).click();
});
Then("特殊サービスの顧客価格と固定報酬が表示される", async ({ page }) => {
  const card = page
    .getByRole("heading", { name: serviceName, exact: true })
    .locator("..");
  await expect(card).toContainText("顧客価格: 2,000 円");
  await expect(card).toContainText("固定報酬: 1,500 円");
});
When("表示条件を確認して特殊サービスを受諾する", async ({ page }) => {
  await page
    .getByRole("heading", { name: serviceName, exact: true })
    .locator("..")
    .getByRole("button", { name: "受諾する", exact: true })
    .click();
  await page
    .getByRole("button", { name: "確認して受諾する", exact: true })
    .click();
});
Then(
  "特殊サービスが「{word}」と表示される",
  async ({ page }, status: string) => {
    await expect(
      page
        .getByRole("heading", { name: serviceName, exact: true })
        .locator(".."),
    ).toContainText(status);
    if (status === "本人が拒否") {
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
  },
);
When("本人が特殊サービスを拒否する", async ({ page }) => {
  await page
    .getByRole("heading", { name: serviceName, exact: true })
    .locator("..")
    .getByRole("button", { name: "拒否する", exact: true })
    .click();
  await page
    .getByRole("button", { name: "確認して拒否する", exact: true })
    .click();
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
    await managerPage.goto(`${PLATFORM_URL}/store/${orderStore}/orders/create`);
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
    await expect(managerPage.getByRole("dialog")).toContainText(
      "請求額: ¥14,000",
    );
    const [response] = await Promise.all([
      managerPage.waitForResponse(
        (resp) =>
          resp.url().endsWith("/api/store/orders") &&
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
  await page.goto(`${PLATFORM_URL}/store/${orderStore}/orders`);
  const card = page.getByRole("listitem").filter({ hasText: serviceName });
  await expect(card).toContainText("本人拒否・要対応");
  await card.getByRole("button", { name: "編集", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "サービスを開始", exact: true }),
  ).toBeDisabled();
  await page.getByRole("checkbox", { name: new RegExp(serviceName) }).uncheck();
  await page.getByRole("button", { name: "保存", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("請求額: ¥12,000");
  const [updated] = await Promise.all([
    page.waitForResponse(
      (resp) =>
        resp.url().endsWith(`/api/store/orders/${specialOrderId}`) &&
        resp.request().method() === "PUT",
    ),
    page
      .getByRole("button", { name: "この内容を確認して保存", exact: true })
      .click(),
  ]);
  expect(updated.status()).toBe(200);
  await expect(page).toHaveURL(new RegExp(`/store/${orderStore}/orders/?$`));
  await page.goto(
    `${PLATFORM_URL}/store/${orderStore}/orders/${specialOrderId}/edit`,
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
