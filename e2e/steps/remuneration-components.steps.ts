import { expect, type APIRequestContext } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { randomUUID } from "node:crypto";
import { PLATFORM_URL } from "../base-url";
import {
  ADMIN_PASSWORD,
  STORE1_ID,
  STORE_HEADERS,
  acceptCastInvitation,
  acceptExistingCastInvitation,
  createCast,
  createCourse,
  deleteService,
  createAgreedOrder,
  completeAgreedOrder,
  issueCastInvitation,
  withdrawCast,
  recordAttendance,
  createPermissionRole,
  createPlatformStaffFixture,
  loginAsStoreAdmin,
  loginPlatformUser,
  getAuthorizedStores,
} from "./store-api";
const { Given, When, Then, After } = createBdd();
let token = "",
  manager = "",
  castToken = "",
  staffEmail = "",
  password = "",
  castEmail = "",
  name = "",
  date = "",
  month = "",
  nextDate = "",
  nextMonth = "",
  secondEnrollment = "",
  otherStore = "";
let personId = 0;
let courseId = "";
const headers = (auth = token, storeId = STORE1_ID) => ({
  ...STORE_HEADERS,
  "X-Store-ID": storeId,
  Authorization: `Bearer ${auth}`,
});
async function statement(request: APIRequestContext, targetMonth = month) {
  const response = await request.get("/api/store/remuneration-statements", {
    headers: headers(),
    params: { person_id: personId, month: targetMonth },
  });
  expect(response.status(), await response.text()).toBe(200);
  return response.json();
}
Given(
  "保証検証の本人に重複する歴史在籍の四時間実績と受注がある",
  async ({ request, $testInfo }) => {
    $testInfo.setTimeout(120000);
    manager = await loginAsStoreAdmin(request);
    const owner = await loginPlatformUser(
      request,
      "admin@kizuna.test",
      ADMIN_PASSWORD,
    );
    const suffix = randomUUID();
    password = randomUUID();
    staffEmail = `remuneration-${suffix}@example.test`;
    castEmail = `remuneration-cast-${suffix}@example.test`;
    name = `長い源氏名でも保証とボーナスの月次内訳を確認する本人-${suffix.slice(0, 8)}`;
    const role = await createPermissionRole(
      request,
      owner,
      `保証検証-${suffix}`,
      [
        "ORDER_MANAGE",
        "ORDER_SET_MANAGE",
        "STORE_VIEW",
        "STORE_MENU_VIEW",
        "PLATFORM_MENU_VIEW",
        "REMUNERATION_VIEW",
        "GUARANTEE_MANAGE",
        "BONUS_AWARD",
        "REMUNERATION_CORRECT",
      ],
    );
    await createPlatformStaffFixture(
      request,
      owner,
      staffEmail,
      password,
      [role],
      [Number(STORE1_ID)],
    );
    token = await loginPlatformUser(request, staffEmail, password);
    otherStore = String(
      (await getAuthorizedStores(request, manager)).find(
        (s) => String(s.id) !== STORE1_ID,
      )!.id,
    );
    const today = new Intl.DateTimeFormat("en-CA", {
      timeZone: "Asia/Tokyo",
    }).format(new Date());
    date = new Date(
      Date.UTC(Number(today.slice(0, 4)), Number(today.slice(5, 7)) - 1, 0),
    )
      .toISOString()
      .slice(0, 10);
    month = date.slice(0, 7);
    nextDate = today.slice(0, 7) + "-01";
    nextMonth = nextDate.slice(0, 7);
    const first = await createCast(request, manager, name + "旧");
    await acceptCastInvitation(
      request,
      await issueCastInvitation(request, manager, first),
      castEmail,
      password,
      name,
    );
    castToken = await loginPlatformUser(request, castEmail, password);
    courseId = await createCourse(request, manager, "保証根拠コース");
    const order = await createAgreedOrder(
      request,
      manager,
      first,
      courseId,
      "保証根拠顧客",
      { businessDate: date },
    );
    await completeAgreedOrder(request, manager, order);
    await recordAttendance(request, manager, {
      cast_id: first,
      actual_start_at: date + "T22:00:00",
      actual_end_at: nextDate + "T01:00:00",
    });
    await withdrawCast(request, manager, first);
    secondEnrollment = await createCast(request, manager, name);
    await acceptExistingCastInvitation(
      request,
      castToken,
      await issueCastInvitation(request, manager, secondEnrollment),
    );
    await recordAttendance(request, manager, {
      cast_id: secondEnrollment,
      actual_start_at: date + "T23:00:00",
      actual_end_at: nextDate + "T02:00:00",
    });
    const candidates = await request.get(
      "/api/store/monthly-remunerations/casts",
      { headers: headers(), params: { search: name } },
    );
    expect(candidates.status()).toBe(200);
    personId = (await candidates.json()).content[0].person_id;
  },
);
When(
  "権限のある担当が画面で日額とボーナスを記録する",
  async ({ page, request, $testInfo }) => {
    await page.goto(PLATFORM_URL + "/platform/login");
    await page.getByLabel("メールアドレス", { exact: true }).fill(staffEmail);
    await page.getByLabel("パスワード", { exact: true }).fill(password);
    await page.getByRole("button", { name: "ログイン", exact: true }).click();
    await page.waitForURL((url) => url.pathname !== "/platform/login");
    await page.goto(
      `${PLATFORM_URL}/store/${STORE1_ID}/orders/monthly-remunerations`,
    );
    await page.getByRole("combobox", { name: "キャスト本人" }).click();
    await page.getByPlaceholder("源氏名で検索").fill(name);
    await page.getByRole("option", { name, exact: false }).click();
    await page.getByLabel("対象月").fill(month);
    await page.getByRole("button", { name: "照会", exact: true }).click();
    await page
      .getByRole("button", { name: "保証・ボーナスを含む月次明細" })
      .click();
    await expect(page.getByText("日額保証は未設定です。")).toBeVisible();
    await expect(
      page.getByText(
        "日額未設定または退勤記録待ちの日があります。現在計算できる保証の小計は",
        { exact: false },
      ),
    ).toBeVisible();
    await page.getByRole("button", { name: "保証条件を追加・停止" }).click();
    let dialog = page.getByRole("dialog", {
      name: "日額保証の登録",
      exact: true,
    });
    await dialog.getByLabel("適用開始日").fill(date);
    await dialog.getByLabel("日額（円）").fill("10000");
    await dialog.getByLabel("理由・説明").fill("日額契約");
    const competing = await request.post("/api/store/remuneration-guarantees", {
      headers: headers(),
      data: {
        person_id: personId,
        effective_from: month + "-01",
        state: "ACTIVE",
        daily_amount: 9000,
        reason: "先行した契約",
        expected_version: 0,
        request_id: randomUUID(),
      },
    });
    expect(competing.status(), await competing.text()).toBe(201);
    await dialog.getByRole("button", { name: "保存する" }).click();
    await expect(
      page.getByText("保証条件が更新されています。再読み込みしてください", {
        exact: true,
      }),
    ).toBeVisible();
    await expect(dialog).toBeVisible();
    const notice = page.getByRole("dialog", {
      name: "保証条件が更新されています。再読み込みしてください",
      exact: true,
    });
    await notice.hover();
    await expect(notice).toHaveCSS("opacity", "1");
    await page.screenshot({
      animations: "disabled",
      path: $testInfo.outputPath("remuneration-conflict-notice.png"),
    });
    await notice.getByRole("button", { name: "通知を閉じる" }).click();
    await expect(notice).not.toBeVisible();
    await page.keyboard.press("Escape");
    await expect(dialog).not.toBeVisible();
    await page.getByRole("button", { name: "内訳を再照会" }).click();
    await page.getByRole("button", { name: "保証条件を追加・停止" }).click();
    dialog = page.getByRole("dialog", { name: "日額保証の登録", exact: true });
    await dialog.getByLabel("適用開始日").fill(date);
    await dialog.getByLabel("日額（円）").fill("10000");
    await dialog.getByLabel("理由・説明").fill("日額契約");
    await dialog.getByRole("button", { name: "保存する" }).click();
    await expect(dialog).not.toBeVisible();
    await page.getByRole("button", { name: "ボーナスを記録" }).click();
    dialog = page.getByRole("dialog", { name: "ボーナスの登録", exact: true });
    await dialog.getByLabel("帰属日").fill(date);
    await dialog.getByLabel("付与額（円）").fill("1500");
    await dialog.getByLabel("理由・説明").fill("特別付与");
    await dialog.getByRole("button", { name: "保存する" }).click();
    await expect(dialog).not.toBeVisible();
  },
);
Then(
  "保証不足分とボーナスが原月の内訳に反映される",
  async ({ page, request, $testInfo }) => {
    const data = await statement(request);
    expect(data.order_total).toBe(7000);
    expect(data.guarantee_total).toBe(3000);
    expect(data.total).toBe(11500);
    expect(data.days.at(-1).closed_duration).toBe("PT4H");
    await expect(page.getByText("¥11,500", { exact: true })).toBeVisible();
    await page
      .getByRole("heading", { name: "受注報酬・保証・ボーナス", exact: true })
      .scrollIntoViewIfNeeded();
    await page.screenshot({
      animations: "disabled",
      path: $testInfo.outputPath("remuneration-store-light.png"),
      fullPage: true,
    });
    await page.evaluate(() => document.documentElement.classList.add("dark"));
    await page.screenshot({
      animations: "disabled",
      path: $testInfo.outputPath("remuneration-store-dark.png"),
      fullPage: true,
    });
    await page.evaluate(() =>
      document.documentElement.classList.remove("dark"),
    );
  },
);
When("担当がボーナスを訂正して誤記として取り消す", async ({ page }) => {
  await page.getByRole("button", { name: "ボーナスを訂正" }).click();
  let dialog = page.getByRole("dialog", {
    name: "ボーナスの訂正",
    exact: true,
  });
  await dialog.getByLabel("付与額（円）").fill("2500");
  await dialog.getByLabel("訂正理由（内部記録）").fill("転記訂正");
  await dialog.getByRole("button", { name: "保存する" }).click();
  await expect(dialog).not.toBeVisible();
  await expect(page.getByText("¥12,500", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "ボーナスを取り消す" }).click();
  dialog = page.getByRole("dialog", { name: "ボーナスの取消", exact: true });
  await dialog.getByLabel("取消理由").fill("誤記取消");
  await dialog.getByRole("button", { name: "取消内容を確認" }).click();
  await page
    .getByRole("alertdialog")
    .getByRole("button", { name: "取り消す", exact: true })
    .click();
  await expect(dialog).not.toBeVisible();
  await page.getByRole("button", { name: "ボーナスの変更履歴" }).click();
  await expect(
    page.getByRole("dialog", { name: "変更履歴", exact: true }),
  ).toContainText("転記訂正");
  await expect(
    page.getByRole("dialog", { name: "変更履歴", exact: true }),
  ).toContainText("誤記取消");
  await page.keyboard.press("Escape");
});
Then(
  "本人も原月の最新内訳を安全に参照できる",
  async ({ request, browser, page: platformPage, $testInfo }) => {
    await platformPage.goto(
      PLATFORM_URL + "/platform/orders/monthly-remunerations",
    );
    await platformPage
      .getByRole("combobox", { name: "店舗", exact: true })
      .click();
    await platformPage.getByRole("option").first().click();
    await platformPage
      .getByRole("combobox", { name: "キャスト本人", exact: true })
      .click();
    await platformPage.getByPlaceholder("源氏名で検索").fill(name);
    await platformPage.getByRole("option", { name, exact: false }).click();
    await platformPage.getByLabel("対象月").fill(month);
    await platformPage
      .getByRole("button", { name: "照会", exact: true })
      .click();
    await platformPage
      .getByRole("button", { name: "保証・ボーナスを含む月次明細" })
      .click();
    await expect(
      platformPage.getByText("¥10,000", { exact: true }).first(),
    ).toBeVisible();
    await expect(
      platformPage.getByRole("button", { name: "ボーナスを記録" }),
    ).toHaveCount(0);
    await platformPage
      .getByRole("heading", { name: "受注報酬・保証・ボーナス", exact: true })
      .scrollIntoViewIfNeeded();
    await platformPage.screenshot({
      animations: "disabled",
      path: $testInfo.outputPath("remuneration-platform.png"),
      fullPage: true,
    });
    await withdrawCast(request, manager, secondEnrollment);
    const response = await request.get(
      "/api/platform/me/remuneration-statements",
      {
        headers: { Authorization: `Bearer ${castToken}` },
        params: { store_id: STORE1_ID, person_id: 999999, month },
      },
    );
    expect(response.status(), await response.text()).toBe(200);
    const data = await response.json();
    expect(data.person_id).toBe(personId);
    expect(data.total).toBe(10000);
    expect(data.bonus_total).toBe(0);
    expect(JSON.stringify(data)).not.toContain("actor_id");
    expect(JSON.stringify(data)).not.toContain("転記訂正");
    const context = await browser.newContext({
      viewport: { width: 390, height: 844 },
    });
    const page = await context.newPage();
    await page.goto(PLATFORM_URL + "/platform/login");
    await page.getByLabel("メールアドレス", { exact: true }).fill(castEmail);
    await page.getByLabel("パスワード", { exact: true }).fill(password);
    await page.getByRole("button", { name: "ログイン", exact: true }).click();
    await page.waitForURL(/\/cast\//);
    await page.goto(PLATFORM_URL + "/cast/remunerations/monthly");
    await page.getByRole("combobox", { name: "店舗" }).click();
    await page.getByRole("option").first().click();
    await page.getByLabel("対象月").fill(month);
    await page.getByRole("button", { name: "照会", exact: true }).click();
    await page
      .getByRole("button", { name: "保証・ボーナスを含む月次明細" })
      .click();
    await expect(
      page.getByText("¥10,000", { exact: true }).first(),
    ).toBeVisible();
    await page
      .getByRole("heading", { name: "受注報酬・保証・ボーナス", exact: true })
      .scrollIntoViewIfNeeded();
    await page.screenshot({
      animations: "disabled",
      path: $testInfo.outputPath("remuneration-self.png"),
      fullPage: true,
    });
    await context.close();
  },
);
When("担当が翌月から保証を停止して未終了実績を残す", async ({ request }) => {
  const first = await request.post("/api/store/remuneration-guarantees", {
    headers: headers(),
    data: {
      person_id: personId,
      effective_from: date,
      state: "ACTIVE",
      daily_amount: 10000,
      reason: "日額契約",
      expected_version: 0,
      request_id: randomUUID(),
    },
  });
  expect(first.status(), await first.text()).toBe(201);
  const stopped = await request.post("/api/store/remuneration-guarantees", {
    headers: headers(),
    data: {
      person_id: personId,
      effective_from: nextDate,
      state: "STOPPED",
      daily_amount: null,
      reason: "保証停止",
      expected_version: 1,
      request_id: randomUUID(),
    },
  });
  expect(stopped.status(), await stopped.text()).toBe(201);
  await recordAttendance(request, manager, {
    cast_id: secondEnrollment,
    actual_start_at: nextDate + "T22:00:00",
    actual_end_at: null,
  });
});
Then("停止日は未終了でも保証額ゼロで月合計を表示する", async ({ request }) => {
  const data = await statement(request, nextMonth);
  expect(data.guarantee_total).toBe(0);
  expect(data.total).toBe(0);
  expect(data.days[0].guarantee_status).toBe("STOPPED");
  expect(data.days[0].attendance_incomplete).toBe(true);
});
Then(
  "同じボーナス要求は一度だけ反映され異内容の再送は拒否される",
  async ({ request }) => {
    const body = {
      person_id: personId,
      award_date: date,
      amount: 1234,
      reason: "一度だけ付与",
      request_id: randomUUID(),
    };
    const create = () =>
      request.post("/api/store/bonus-awards", {
        headers: headers(),
        data: body,
      });
    const [a, b] = await Promise.all([create(), create()]);
    expect(a.status(), await a.text()).toBe(201);
    expect(b.status(), await b.text()).toBe(201);
    expect(await a.json()).toEqual(await b.json());
    expect((await statement(request)).bonus_total).toBe(1234);
    const conflict = await request.post("/api/store/bonus-awards", {
      headers: headers(),
      data: { ...body, amount: 1235 },
    });
    expect(conflict.status()).toBe(409);
  },
);
Then("授権外店舗と無権限の内訳操作は拒否される", async ({ request }) => {
  for (const [auth, storeId] of [
    [manager, STORE1_ID],
    [token, otherStore],
  ]) {
    const response = await request.get("/api/store/remuneration-statements", {
      headers: headers(auth, storeId),
      params: { person_id: personId, month },
    });
    expect(response.status()).toBe(403);
  }
  const platform = await request.get("/api/platform/remuneration-statements", {
    headers: { Authorization: `Bearer ${token}` },
    params: { person_id: personId, store_id: STORE1_ID, month },
  });
  expect(platform.status(), await platform.text()).toBe(200);
  const foreign = await request.get("/api/platform/remuneration-statements", {
    headers: { Authorization: `Bearer ${token}` },
    params: { person_id: personId, store_id: otherStore, month },
  });
  expect(foreign.status()).toBe(403);
  const noPerson = await request.post("/api/store/bonus-awards", {
    headers: headers(),
    data: {
      person_id: 999999999,
      award_date: date,
      amount: 1000,
      reason: "誤った本人",
      request_id: randomUUID(),
    },
  });
  expect(noPerson.status()).toBe(404);
});

After({ tags: "@remuneration-components" }, async ({ request }) => {
  if (!courseId) return;
  await deleteService(request, manager, courseId, 1);
  courseId = "";
});
