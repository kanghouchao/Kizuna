import { expect, type Page } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { PLATFORM_URL } from "../base-url";
import {
  ADMIN_PASSWORD,
  STORE_HEADERS,
  STORE1_ID,
  activateEmergencyElevation,
  revokeEmergencyElevation,
  getAuthorizedStores,
  loginAsStoreAdmin,
  createPermissionRole,
  createPlatformStaffFixture,
  loginPlatformUser,
  loginViaUiAndEnterStore,
} from "./store-api";
const { Given, When, Then } = createBdd();
const questionPrompt =
  "利用体験の記録。" +
  "受付時の説明や案内について、紙面に記載された内容をそのまま記録します。".repeat(
    4,
  );
let email = "",
  secret = "",
  token = "",
  storeId = "",
  surveyId = "",
  revisionId = "",
  answerId = "";
const headers = () => ({ ...STORE_HEADERS, Authorization: `Bearer ${token}` });
const revisionPath = () =>
  `/api/store/surveys/${surveyId}/revisions/${revisionId}`;
async function confirm(page: Page) {
  const dialog = page.getByRole("alertdialog");
  await expect(dialog).toBeVisible();
  await dialog.getByRole("button", { name: "確定する", exact: true }).click();
  await expect(dialog).toBeHidden();
}
Given(
  "既定権限を持たないアンケート担当者を用意する",
  async ({ page, request }) => {
    storeId = await loginViaUiAndEnterStore(page);
    const owner = await loginPlatformUser(
      request,
      "admin@kizuna.test",
      ADMIN_PASSWORD,
    );
    expect(
      (
        await request.get("/api/store/surveys", {
          headers: { ...STORE_HEADERS, Authorization: `Bearer ${owner}` },
        })
      ).status(),
    ).toBe(403);
    const suffix = Date.now().toString();
    email = `survey-${suffix}@kizuna.test`;
    secret = `Survey-${suffix}-fixture`;
    const role = await createPermissionRole(
      request,
      owner,
      `アンケート担当-${suffix}`,
      [
        "SURVEY_VIEW",
        "SURVEY_MANAGE",
        "SURVEY_RECORD",
        "PLATFORM_MENU_VIEW",
        "STORE_MENU_VIEW",
      ],
    );
    await createPlatformStaffFixture(request, owner, email, secret, [role]);
    token = await loginPlatformUser(request, email, secret);
    expect(
      (
        await request.get("/api/store/surveys", { headers: STORE_HEADERS })
      ).status(),
    ).toBe(401);
    expect(
      (
        await request.get("/api/store/surveys", {
          headers: { Authorization: `Bearer ${token}` },
        })
      ).status(),
    ).toBe(403);
  },
);
When(
  "設問を作成し通信結果不明から同じ要求で復帰して受付を開始する",
  async ({ page, $testInfo }) => {
    await page.context().clearCookies();
    await page.goto(`${PLATFORM_URL}/platform/login`);
    await page.getByLabel("メールアドレス", { exact: true }).fill(email);
    await page.getByLabel("パスワード", { exact: true }).fill(secret);
    await page.getByRole("button", { name: "ログイン", exact: true }).click();
    await expect(page).toHaveURL(/\/platform\/dashboard/, { timeout: 15000 });
    await page.goto(`${PLATFORM_URL}/store/${storeId}/surveys`);
    await page
      .getByRole("button", { name: "アンケートを作成", exact: true })
      .click();
    const editor = page.getByRole("dialog", {
      name: "アンケートを作成",
      exact: true,
    });
    await editor
      .getByLabel("題名", { exact: true })
      .fill("担当者が作成した問票");
    await editor.getByLabel("設問文", { exact: true }).fill(questionPrompt);
    await editor
      .getByRole("button", { name: "設問を追加", exact: true })
      .click();
    const second = editor.locator("section").last();
    await second.getByLabel("設問文", { exact: true }).fill("任意の選択式記録");
    await second.getByLabel("回答形式", { exact: true }).click();
    await page.getByRole("option", { name: "単一選択", exact: true }).click();
    await second.getByLabel("回答必須", { exact: true }).click();
    await second
      .getByLabel("選択肢 1", { exact: true })
      .fill("紙面の選択欄に記載された最初の選択肢です。".repeat(4));
    await second
      .getByLabel("選択肢 2", { exact: true })
      .fill("紙面の選択欄に記載された別の選択肢です。".repeat(4));
    await page.setViewportSize({ width: 390, height: 844 });
    expect(
      await editor.evaluate(
        (element) => element.scrollWidth <= element.clientWidth,
      ),
    ).toBeTruthy();
    await page.screenshot({
      path: $testInfo.outputPath("survey-definition-mobile.png"),
      fullPage: true,
      animations: "disabled",
    });
    await page.setViewportSize({ width: 1280, height: 900 });
    let intercepted = false;
    let original: unknown;
    await page.route("**/api/store/surveys", async (route) => {
      if (route.request().method() !== "POST" || intercepted) {
        await route.continue();
        return;
      }
      intercepted = true;
      original = route.request().postDataJSON();
      const committed = await route.fetch();
      expect(committed.status()).toBe(201);
      await route.abort("failed");
    });
    await editor
      .getByRole("button", { name: "下書きを保存", exact: true })
      .click();
    await expect(
      editor.getByText(/結果が確認できません。同じ要求/),
    ).toBeVisible();
    await editor.getByRole("button", { name: "Close", exact: true }).click();
    await expect(editor).toBeHidden();
    await page
      .getByRole("button", { name: "入力画面へ戻る", exact: true })
      .click();
    await expect(editor.getByLabel("題名", { exact: true })).toHaveValue(
      "担当者が作成した問票",
    );
    const [replayed] = await Promise.all([
      page.waitForResponse(
        (r) =>
          r.url().endsWith("/store/surveys") && r.request().method() === "POST",
      ),
      editor
        .getByRole("button", { name: "同じ要求で結果を確認", exact: true })
        .click(),
    ]);
    expect(replayed.status()).toBe(200);
    expect(replayed.request().postDataJSON()).toEqual(original);
    const result = await replayed.json();
    expect(result.operation.replayed).toBe(true);
    surveyId = result.revision.survey_id;
    revisionId = result.revision.id;
    await page.unroute("**/api/store/surveys");
    await expect(editor).toBeHidden();
    await page.getByRole("button", { name: "受付を開始", exact: true }).click();
    const opening = page.getByRole("dialog", {
      name: "受付を開始",
      exact: true,
    });
    await opening.getByLabel("操作理由").fill("入力した設問を確認");
    await opening.getByLabel("操作理由").press("Tab");
    await expect(
      opening.getByRole("button", { name: "確認へ", exact: true }),
    ).toBeFocused();
    await page.keyboard.press("Enter");
    await confirm(page);
    await expect(
      page.getByRole("button", { name: "回答を受付", exact: true }),
    ).toBeVisible();
  },
);
When("回答を人工受付し設問版を終了する", async ({ page, request }) => {
  await page.getByRole("button", { name: "回答を受付", exact: true }).click();
  const intake = page.getByRole("dialog", { name: "回答を受付", exact: true });
  await intake
    .getByLabel(`${questionPrompt}（必須）`, { exact: true })
    .fill("  紙面に書かれた原回答\n二行目を保持する  ");
  await intake.getByLabel("受領日時", { exact: true }).fill("2026-01-01T09:00");
  const [created] = await Promise.all([
    page.waitForResponse(
      (r) =>
        r.url().endsWith(`/${revisionId}/responses`) &&
        r.request().method() === "POST",
    ),
    intake.getByRole("button", { name: "回答を記録", exact: true }).click(),
  ]);
  expect(created.status()).toBe(201);
  const row = (await created.json()).answer;
  answerId = row.id;
  expect(row.intake_source).toBe("STAFF_RECORDED");
  expect(row).not.toHaveProperty("customer_id");
  expect(row).not.toHaveProperty("anonymous");
  await expect(
    page.getByText(/スタッフによる記録。回答者の本人確認は行っていません/),
  ).toBeVisible();
  await page.getByRole("button", { name: "設問版へ戻る", exact: true }).click();
  await page.getByRole("button", { name: "受付を終了", exact: true }).click();
  const closing = page.getByRole("dialog", { name: "受付を終了", exact: true });
  await closing.getByLabel("操作理由").fill("今回の受付を終了");
  await closing.getByRole("button", { name: "確認へ", exact: true }).click();
  await confirm(page);
  await expect(
    page.getByRole("button", { name: "回答を受付", exact: true }),
  ).toHaveCount(0);
  const denied = await request.post(`${revisionPath()}/responses`, {
    headers: headers(),
    data: {
      revision_version: 2,
      received_via: "PAPER",
      received_at: "2026-01-01T00:00:00Z",
      answers: row.answers,
      dedupe_key: "after-closed",
    },
  });
  expect(denied.status()).toBe(409);
});
Then(
  "終了した版の回答を訂正して原文と記録件数を確認できる",
  async ({ page, request, $testInfo }) => {
    await page.getByRole("button", { name: "回答・履歴", exact: true }).click();
    await page.getByRole("button", { name: "回答を訂正", exact: true }).click();
    const correction = page.getByRole("dialog", {
      name: "回答を訂正",
      exact: true,
    });
    await correction
      .getByLabel(`${questionPrompt}（必須）`, { exact: true })
      .fill("訂正した回答");
    await correction.getByLabel("操作理由").fill("紙面の転記誤りを修正");
    await page.setViewportSize({ width: 390, height: 844 });
    expect(
      await correction.evaluate(
        (element) => element.scrollWidth <= element.clientWidth,
      ),
    ).toBeTruthy();
    await page.screenshot({
      path: $testInfo.outputPath("survey-light-mobile.png"),
      fullPage: true,
      animations: "disabled",
    });
    await page.evaluate(() => document.documentElement.classList.add("dark"));
    await page.screenshot({
      path: $testInfo.outputPath("survey-dark-mobile.png"),
      fullPage: true,
      animations: "disabled",
    });
    await page.setViewportSize({ width: 1280, height: 900 });
    await correction
      .getByRole("button", { name: "確認へ", exact: true })
      .click();
    await confirm(page);
    await expect(page.getByText("訂正した回答", { exact: true })).toBeVisible();
    const old = await (
      await request.get(`/api/store/survey-responses/${answerId}`, {
        headers: headers(),
      })
    ).json();
    expect(old.status).toBe("WITHDRAWN");
    expect(old.answers[0].text).toBe(
      "  紙面に書かれた原回答\n二行目を保持する  ",
    );
    const counts = await (
      await request.get(`${revisionPath()}/response-counts`, {
        headers: headers(),
      })
    ).json();
    expect(counts).toMatchObject({
      total_records: 2,
      active_records: 1,
      withdrawn_records: 1,
    });
    const history = await (
      await request.get(`/api/store/survey-responses/${answerId}/history`, {
        headers: headers(),
        params: { size: 1 },
      })
    ).json();
    expect(history.content[0].type).toBe("CORRECTION_LINKED");
    expect(history.next_cursor).toBeTruthy();
    const list = await (
      await request.get(`${revisionPath()}/responses`, { headers: headers() })
    ).json();
    expect(list.content).toHaveLength(2);
    for (const summary of list.content)
      expect(summary).not.toHaveProperty("answers");
    await page
      .getByRole("button", { name: "回答を取り下げ", exact: true })
      .click();
    const withdrawal = page.getByRole("dialog", {
      name: "回答を取り下げ",
      exact: true,
    });
    await withdrawal.getByLabel("操作理由").fill("回答の取り下げ");
    await withdrawal
      .getByRole("button", { name: "確認へ", exact: true })
      .click();
    await confirm(page);
    await expect(page.getByText(/取り下げ済み ／ 設問版/)).toBeVisible();
    expect(
      (
        await (
          await request.get(`${revisionPath()}/response-counts`, {
            headers: headers(),
          })
        ).json()
      ).active_records,
    ).toBe(0);
    await page.screenshot({
      path: $testInfo.outputPath("survey-withdrawal-desktop.png"),
      fullPage: true,
      animations: "disabled",
    });
  },
);
let operator = "",
  elevated = "",
  elevationId = 0;
const elevatedDefinition = {
  title: "昇格中の問票",
  questions: [
    {
      question_key: "q1",
      type: "TEXT",
      prompt: "記録する内容",
      required: true,
      options: [],
    },
  ],
  dedupe_key: "survey-elevated",
};
Given(
  "通常のアンケート権限を持たない緊急昇格担当者を用意する",
  async ({ request }) => {
    const owner = await loginPlatformUser(
        request,
        "admin@kizuna.test",
        ADMIN_PASSWORD,
      ),
      suffix = Date.now().toString();
    const login = `survey-elevation-${suffix}@kizuna.test`,
      password = `Survey-elevation-${suffix}-fixture`;
    const role = await createPermissionRole(
      request,
      owner,
      `問票緊急調査-${suffix}`,
      ["EMERGENCY_ELEVATE", "PLATFORM_MENU_VIEW"],
    );
    const stores = await getAuthorizedStores(
      request,
      await loginAsStoreAdmin(request),
    );
    const other = stores.find(
      (s: { id: number }) => String(s.id) !== STORE1_ID,
    )!.id;
    await createPlatformStaffFixture(
      request,
      owner,
      login,
      password,
      [role],
      [other],
    );
    operator = await loginPlatformUser(request, login, password);
    expect(
      (
        await request.get("/api/store/surveys", {
          headers: { ...STORE_HEADERS, Authorization: `Bearer ${operator}` },
        })
      ).status(),
    ).toBe(403);
    const elevation = await activateEmergencyElevation(
      request,
      operator,
      STORE1_ID,
      "問票の緊急調査",
      password,
    );
    elevated = elevation.token;
    elevationId = elevation.id;
    expect(
      (
        await request.get("/api/store/surveys", {
          headers: {
            ...STORE_HEADERS,
            "X-Store-ID": String(other),
            Authorization: `Bearer ${elevated}`,
          },
        })
      ).status(),
    ).toBe(403);
  },
);
When("対象店舗で設問版を作成して同じ要求を再生する", async ({ request }) => {
  const auth = { ...STORE_HEADERS, Authorization: `Bearer ${elevated}` };
  expect(
    (
      await request.post("/api/store/surveys", {
        headers: auth,
        data: elevatedDefinition,
      })
    ).status(),
  ).toBe(201);
  const replay = await request.post("/api/store/surveys", {
    headers: auth,
    data: elevatedDefinition,
  });
  expect(replay.status()).toBe(200);
  expect((await replay.json()).operation.replayed).toBe(true);
});
Then(
  "昇格撤回後は問票も受付済み要求の再生も利用できない",
  async ({ request }) => {
    await revokeEmergencyElevation(request, operator, elevationId);
    const auth = { ...STORE_HEADERS, Authorization: `Bearer ${elevated}` };
    expect(
      (await request.get("/api/store/surveys", { headers: auth })).status(),
    ).toBe(401);
    expect(
      (
        await request.post("/api/store/surveys", {
          headers: auth,
          data: elevatedDefinition,
        })
      ).status(),
    ).toBe(401);
  },
);
