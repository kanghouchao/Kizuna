import { expect, test } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { PLATFORM_URL } from "../base-url";
import {
  acceptCastInvitation,
  createCast,
  createSpecialService,
  issueCastInvitation,
  loginAsStoreAdmin,
  reviseSpecialService,
} from "./store-api";

const { Given, When, Then } = createBdd();
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
