import {
  expect,
  type APIRequestContext,
  type BrowserContext,
  type Page,
} from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { randomUUID } from "node:crypto";
import { APP_URL } from "../base-url";
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
  createStaffFixture,
  loginAsStoreAdmin,
  loginUser,
  getAuthorizedStores,
  openLoginForm,
  dayLabel,
  storeUrl,
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
let castContext: BrowserContext | undefined;
let castPage: Page | undefined;
const headers = (auth = token, storeId = STORE1_ID) => ({
  ...STORE_HEADERS,
  "X-Store-ID": storeId,
  Authorization: `Bearer ${auth}`,
});
async function statement(request: APIRequestContext, targetMonth = month) {
  const response = await request.get("/api/remuneration-statements", {
    headers: headers(),
    params: { person_id: personId, month: targetMonth },
  });
  expect(response.status(), await response.text()).toBe(200);
  return response.json();
}
async function openSelfStatement(page: Page) {
  await openLoginForm(page);
  await page.getByLabel("メールアドレス", { exact: true }).fill(castEmail);
  await page.getByLabel("パスワード", { exact: true }).fill(password);
  await page.getByRole("button", { name: "ログイン", exact: true }).click();
  await page.waitForURL(/\/cast\/?$/);
  await page.goto(
    `${APP_URL}/cast/remunerations/monthly?store_id=${STORE1_ID}&month=${month}`,
  );
  await expect(selfSummary(page)).toBeVisible();
}
const selfSummary = (page: Page) =>
  page.getByRole("region", { name: `${Number(month.slice(5, 7))}月の合計` });
// 承諾の操作ボタンも同じ日付で始まるので、見出しの開閉ボタン（先に並ぶ方）に絞る。
const selfDayHeader = (page: Page) =>
  page.getByRole("button", { name: new RegExp(`^${dayLabel(date)}`) }).first();
const selfDay = (page: Page) =>
  page.getByRole("region", { name: `${dayLabel(date)}の明細` });
/** 月を一度動かして戻し、原月の明細を取り直す。 */
async function refetchSelfMonth(page: Page) {
  await page.getByRole("button", { name: "次の月", exact: true }).click();
  await page.getByRole("button", { name: "前の月", exact: true }).click();
  await expect(selfSummary(page)).toBeVisible();
}
Given(
  "保証検証の本人に重複する歴史在籍の四時間実績と受注がある",
  async ({ request, $testInfo }) => {
    $testInfo.setTimeout(120000);
    manager = await loginAsStoreAdmin(request);
    const owner = await loginUser(
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
        "ORDER_SET_VIEW",


        "REMUNERATION_VIEW",
        "GUARANTEE_MANAGE",
        "BONUS_AWARD",
        "REMUNERATION_CORRECT",
      ],
    );
    await createStaffFixture(
      request,
      owner,
      staffEmail,
      password,
      [role],
      [Number(STORE1_ID)],
    );
    token = await loginUser(request, staffEmail, password);
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
    castToken = await loginUser(request, castEmail, password);
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
      await issueCastInvitation(request, manager, secondEnrollment),
      castEmail,
      password,
    );
    await recordAttendance(request, manager, {
      cast_id: secondEnrollment,
      actual_start_at: date + "T23:00:00",
      actual_end_at: nextDate + "T02:00:00",
    });
    const candidates = await request.get(
      "/api/monthly-remunerations/casts",
      { headers: headers(), params: { search: name } },
    );
    expect(candidates.status()).toBe(200);
    personId = (await candidates.json()).content[0].person_id;
  },
);
When(
  "権限のある担当が画面で日額とボーナスを記録する",
  async ({ page, request, $testInfo }) => {
    await openLoginForm(page);
    await page.getByLabel("メールアドレス", { exact: true }).fill(staffEmail);
    await page.getByLabel("パスワード", { exact: true }).fill(password);
    await page.getByRole("button", { name: "ログイン", exact: true }).click();
    await page.waitForURL((url) => url.pathname !== "/auth/login");
    await page.goto(
      storeUrl(STORE1_ID, `/orders/monthly-remunerations`),
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
    const competing = await request.post("/api/remuneration-guarantees", {
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
  async ({ request, browser, page: adminPage, $testInfo }) => {
    await adminPage.goto(
      storeUrl(STORE1_ID, "/orders/monthly-remunerations"),
    );
    await adminPage
      .getByRole("combobox", { name: "キャスト本人", exact: true })
      .click();
    await adminPage.getByPlaceholder("源氏名で検索").fill(name);
    await adminPage.getByRole("option", { name, exact: false }).click();
    await adminPage.getByLabel("対象月").fill(month);
    await adminPage
      .getByRole("button", { name: "照会", exact: true })
      .click();
    await adminPage
      .getByRole("button", { name: "保証・ボーナスを含む月次明細" })
      .click();
    await expect(
      adminPage.getByText("¥10,000", { exact: true }).first(),
    ).toBeVisible();
    await adminPage
      .getByRole("heading", { name: "受注報酬・保証・ボーナス", exact: true })
      .scrollIntoViewIfNeeded();
    await adminPage.screenshot({
      animations: "disabled",
      path: $testInfo.outputPath("remuneration-admin.png"),
      fullPage: true,
    });
    await withdrawCast(request, manager, secondEnrollment);
    const response = await request.get(
      "/api/me/remuneration-statements",
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
    await openSelfStatement(page);
    const summary = selfSummary(page);
    await expect(summary).toContainText("¥10,000");
    await expect(summary).toContainText("受注報酬¥7,000");
    await expect(summary).toContainText("日額保証の不足分¥3,000");
    await expect(summary).toContainText("ボーナス¥0");
    await expect(selfDay(page)).toContainText("日額 ¥10,000 に対する不足分¥3,000");
    await expect(page.getByRole("main")).not.toContainText("転記訂正");
    await expect(page.getByRole("main")).not.toContainText("誤記取消");
    await page.screenshot({
      animations: "disabled",
      path: $testInfo.outputPath("remuneration-self.png"),
      fullPage: true,
    });
    await context.close();
  },
);
When("担当が翌月から保証を停止して未終了実績を残す", async ({ request }) => {
  const first = await request.post("/api/remuneration-guarantees", {
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
  const stopped = await request.post("/api/remuneration-guarantees", {
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
      request.post("/api/bonus-awards", {
        headers: headers(),
        data: body,
      });
    const [a, b] = await Promise.all([create(), create()]);
    expect(a.status(), await a.text()).toBe(201);
    expect(b.status(), await b.text()).toBe(201);
    // 一方は初回、他方は受領の再送。再送は読み直した現在の状態で、初回応答と同じになる。
    expect(await a.json()).toEqual(await b.json());
    expect((await statement(request)).bonus_total).toBe(1234);
    const conflict = await request.post("/api/bonus-awards", {
      headers: headers(),
      data: { ...body, amount: 1235 },
    });
    expect(conflict.status()).toBe(409);
    // 訂正の後の再送は初回の写しではなく対象の現在の状態を返し、変更 ID は作成時の受領から組む。
    const created = await a.json();
    const correction = await request.post(
      `/api/bonus-awards/${created.bonus.id}/corrections`,
      {
        headers: headers(),
        data: {
          award_date: date,
          amount: 2345,
          reason: body.reason,
          correction_reason: "金額訂正",
          expected_version: created.bonus.version,
          request_id: randomUUID(),
        },
      },
    );
    expect(correction.status(), await correction.text()).toBe(201);
    const corrected = await correction.json();
    const replay = await create();
    expect(replay.status(), await replay.text()).toBe(201);
    const replayed = await replay.json();
    expect(replayed.bonus).toEqual(corrected.bonus);
    expect(replayed.change_id).toBe(created.change_id);
    expect((await statement(request)).bonus_total).toBe(2345);
  },
);
Then("授権外店舗と無権限の内訳操作は拒否される", async ({ request }) => {
  const denied = await request.get("/api/remuneration-statements", {
    headers: headers(token, otherStore),
    params: { person_id: personId, month },
  });
  expect(denied.status()).toBe(403);
  // 店長は既定授与で REMUNERATION_VIEW を持つ。
  const managed = await request.get("/api/remuneration-statements", {
    headers: headers(manager, STORE1_ID),
    params: { person_id: personId, month },
  });
  expect(managed.status(), await managed.text()).toBe(200);
  // 店舗スタッフには REMUNERATION_VIEW の既定授与が無い。
  const staff = await loginUser(
    request,
    "yamada.jiro@kizuna.test",
    ADMIN_PASSWORD,
  );
  const unprivileged = await request.get("/api/remuneration-statements", {
    headers: headers(staff, STORE1_ID),
    params: { person_id: personId, month },
  });
  expect(unprivileged.status()).toBe(403);
  const noPerson = await request.post("/api/bonus-awards", {
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
  await castContext?.close();
  castContext = undefined;
  castPage = undefined;
  if (!courseId) return;
  await deleteService(request, manager, courseId, 1);
  courseId = "";
});

When("ボーナスが保存された直後に応答を失う", async ({ page, request }) => {
  await openLoginForm(page);
  await page.getByLabel("メールアドレス", { exact: true }).fill(staffEmail);
  await page.getByLabel("パスワード", { exact: true }).fill(password);
  await page.getByRole("button", { name: "ログイン", exact: true }).click();
  await page.waitForURL((url) => url.pathname !== "/auth/login");
  await page.goto(
    storeUrl(STORE1_ID, `/orders/monthly-remunerations`),
  );
  await selectPerson(page, name);
  await page.getByLabel("対象月").fill(month);
  await page.getByRole("button", { name: "照会", exact: true }).click();
  await page
    .getByRole("button", { name: "保証・ボーナスを含む月次明細" })
    .click();
  await page.getByRole("button", { name: "ボーナスを記録" }).click();
  const dialog = page.getByRole("dialog", {
    name: "ボーナスの登録",
    exact: true,
  });
  await dialog.getByLabel("帰属日").fill(date);
  await dialog.getByLabel("付与額（円）").fill("1500");
  await dialog.getByLabel("理由・説明").fill("応答喪失の検証");
  lostRequests = [];
  await page.route("**/api/bonus-awards", async (route) => {
    if (route.request().method() !== "POST") {
      await route.continue();
      return;
    }
    lostRequests.push(route.request().postDataJSON());
    const committed = await route.fetch();
    expect(committed.status()).toBe(201);
    await route.abort("failed");
  });
  await dialog.getByRole("button", { name: "保存する" }).click();
  await expect(
    dialog.getByRole("button", { name: "元の内容で結果を確認" }),
  ).toBeVisible();
  await expect(dialog.getByLabel("付与額（円）")).toBeDisabled();
  await expect(dialog.getByLabel("理由・説明")).toBeDisabled();
  await dialog.getByRole("button", { name: "元の内容で結果を確認" }).click();
  await expect(
    dialog.getByRole("button", { name: "元の内容で結果を確認" }),
  ).toBeVisible();
  expect(lostRequests).toHaveLength(2);
  expect(lostRequests[1]).toEqual(lostRequests[0]);
  const data = await statement(request);
  expect(data.bonus_total).toBe(1500);
  expect(data.bonus_awards.content).toHaveLength(1);
  await page.unroute("**/api/bonus-awards");
});
let lostRequests: Record<string, unknown>[] = [];
async function selectPerson(page: Page, personName: string) {
  await page.getByRole("combobox", { name: "キャスト本人" }).click();
  await page.getByPlaceholder("源氏名で検索").fill(personName);
  await page.getByRole("option", { name: personName, exact: false }).click();
}
Then(
  "閉じ直しと本人切替後も元の要求を確認するまで新しい付与を止める",
  async ({ page, request, $testInfo }) => {
    let dialog = page.getByRole("dialog", {
      name: "ボーナスの登録",
      exact: true,
    });
    await dialog.getByRole("button", { name: "閉じる", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "ボーナスを記録" }),
    ).toBeDisabled();
    await page.getByRole("button", { name: "未確認の送信を復元" }).click();
    await expect(dialog.getByLabel("付与額（円）")).toHaveValue("1500");
    await dialog.getByRole("button", { name: "閉じる", exact: true }).click();
    const otherName = `復元対象とは別の本人-${randomUUID().slice(0, 8)}`;
    const other = await createCast(request, manager, otherName);
    await acceptCastInvitation(
      request,
      await issueCastInvitation(request, manager, other),
      `${randomUUID()}@example.test`,
      password,
      otherName,
    );
    await selectPerson(page, otherName);
    await page.getByRole("button", { name: "照会", exact: true }).click();
    const expand = page.getByRole("button", {
      name: "保証・ボーナスを含む月次明細",
    });
    if ((await expand.getAttribute("aria-expanded")) === "false")
      await expand.click();
    await expect(
      page.getByRole("button", { name: "ボーナスを記録" }),
    ).toBeDisabled();
    await expect(
      page.getByText(
        `${name} の送信結果を確認するまで、新しい記録は作成できません。`,
      ),
    ).toBeVisible();
    await page.getByRole("button", { name: "未確認の送信を復元" }).click();
    dialog = page.getByRole("dialog", { name: "ボーナスの登録", exact: true });
    await expect(dialog.getByLabel("理由・説明")).toHaveValue("応答喪失の検証");
    await expect(dialog.getByLabel("付与額（円）")).toBeDisabled();
    for (let i = 0; i < 2; i++) {
      const notice = page
        .getByRole("dialog", {
          name: "保存結果を確認できませんでした。元の内容で結果を確認してください。",
          exact: true,
        })
        .first();
      await notice.hover();
      await notice.getByRole("button", { name: "通知を閉じる" }).click();
      await expect(
        page.getByRole("dialog", {
          name: "保存結果を確認できませんでした。元の内容で結果を確認してください。",
          exact: true,
        }),
      ).toHaveCount(1 - i);
    }
    await page.screenshot({
      animations: "disabled",
      path: $testInfo.outputPath("remuneration-unknown-recovery.png"),
    });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.evaluate(() => document.documentElement.classList.add("dark"));
    await expect(dialog).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true);
    await dialog.getByRole("button", { name: "元の内容で結果を確認" }).focus();
    await page.screenshot({
      animations: "disabled",
      path: $testInfo.outputPath(
        "remuneration-unknown-recovery-dark-mobile.png",
      ),
    });
    await page.evaluate(() =>
      document.documentElement.classList.remove("dark"),
    );
    await page.setViewportSize({ width: 1280, height: 720 });
    const [replayed] = await Promise.all([
      page.waitForRequest(
        (req) =>
          req.url().endsWith("/api/bonus-awards") &&
          req.method() === "POST",
      ),
      dialog.getByRole("button", { name: "元の内容で結果を確認" }).click(),
    ]);
    expect(replayed.postDataJSON()).toEqual(lostRequests[0]);
    await expect(dialog).not.toBeVisible();
    expect((await statement(request)).bonus_awards.content).toHaveLength(1);
    await expect(
      page.getByRole("button", { name: "ボーナスを記録" }),
    ).toBeEnabled();
    await selectPerson(page, name);
    await page.getByRole("button", { name: "照会", exact: true }).click();
    if ((await expand.getAttribute("aria-expanded")) === "false")
      await expand.click();
    await page.getByRole("button", { name: "ボーナスを記録" }).click();
    await dialog.getByLabel("帰属日").fill(date);
    await dialog.getByLabel("付与額（円）").fill("500");
    await dialog.getByLabel("理由・説明").fill("確定後の別付与");
    const [fresh] = await Promise.all([
      page.waitForRequest(
        (req) =>
          req.url().endsWith("/api/bonus-awards") &&
          req.method() === "POST",
      ),
      dialog.getByRole("button", { name: "保存する" }).click(),
    ]);
    expect(fresh.postDataJSON().request_id).not.toBe(
      lostRequests[0].request_id,
    );
    await expect(dialog).not.toBeVisible();
    const data = await statement(request);
    expect(data.bonus_total).toBe(2000);
    expect(data.bonus_awards.content).toHaveLength(2);
  },
);

async function awardBonus(request: APIRequestContext, amount: number) {
  const response = await request.post("/api/bonus-awards", {
    headers: headers(),
    data: {
      person_id: personId,
      award_date: date,
      amount,
      reason: "追加付与",
      request_id: randomUUID(),
    },
  });
  expect(response.status(), await response.text()).toBe(201);
}
Given("担当が原月の日額保証を記録している", async ({ request }) => {
  const response = await request.post("/api/remuneration-guarantees", {
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
  expect(response.status(), await response.text()).toBe(201);
});
When(
  "本人がポータルの月次明細で原月末日を承諾する",
  async ({ browser, $testInfo }) => {
    castContext = await browser.newContext({
      viewport: { width: 390, height: 844 },
    });
    castPage = await castContext.newPage();
    await openSelfStatement(castPage);
    await expect(selfDayHeader(castPage)).toContainText("承諾待ち");
    await selfDay(castPage)
      .getByRole("button", { name: `${dayLabel(date)}を承諾する`, exact: true })
      .click();
    await expect(selfDayHeader(castPage)).toContainText("承諾済み");
    await expect(selfDay(castPage)).toContainText("に承諾しました");
    await expect(selfSummary(castPage)).toContainText("承諾 1 / 1 日");
    await selfDay(castPage).scrollIntoViewIfNeeded();
    await castPage.screenshot({
      animations: "disabled",
      path: $testInfo.outputPath("acceptance-self-light.png"),
    });
  },
);
Then("店舗側の明細に承諾済みが読み取り専用で載る", async ({ request }) => {
  const data = await statement(request);
  const day = data.days.find(
    (d: { business_date: string }) => d.business_date === date,
  );
  expect(day.acceptance_state).toBe("ACCEPTED");
  expect(day.latest_acceptance.order_amount).toBe(7000);
  expect(day.latest_acceptance.guarantee_amount).toBe(3000);
  expect(data.accepted_days).toBe(1);
  expect(JSON.stringify(day.latest_acceptance)).not.toContain("保証根拠顧客");
});
When("承諾後に担当がボーナスを付与する", async ({ request }) => {
  await awardBonus(request, 500);
});
Then("本人の画面は承諾後に変更ありと差分を示す", async ({ $testInfo }) => {
  await refetchSelfMonth(castPage!);
  await expect(selfDayHeader(castPage!)).toContainText("再承諾が必要");
  await expect(selfDay(castPage!)).toContainText("承諾した後に変わりました");
  await expect(selfDay(castPage!)).toContainText("ボーナス¥0 → ¥500");
  await selfDay(castPage!).scrollIntoViewIfNeeded();
  await castPage!.evaluate(() =>
    document.documentElement.classList.add("dark"),
  );
  await castPage!.screenshot({
    animations: "disabled",
    path: $testInfo.outputPath("acceptance-changed-dark.png"),
  });
  await castPage!.evaluate(() =>
    document.documentElement.classList.remove("dark"),
  );
});
When(
  "本人が古い明細のまま再承諾する間に担当がさらにボーナスを付与する",
  async ({ request }) => {
    await awardBonus(request, 300);
    await selfDay(castPage!)
      .getByRole("button", { name: `${dayLabel(date)}を再承諾する`, exact: true })
      .click();
  },
);
Then("承諾は差し戻され明細が最新の差分へ取り直される", async () => {
  await expect(
    castPage!.getByText(
      "警告：明細の内容が変わっていたため、最新の明細を表示しました。内容を確かめてから承諾してください",
    ),
  ).toBeVisible();
  await expect(selfDay(castPage!)).toContainText("ボーナス¥0 → ¥800");
  await expect(selfDayHeader(castPage!)).toContainText("再承諾が必要");
});
When("本人が最新の内容を再承諾する", async () => {
  await selfDay(castPage!)
    .getByRole("button", { name: `${dayLabel(date)}を再承諾する`, exact: true })
    .click();
  await expect(selfDayHeader(castPage!)).toContainText("承諾済み");
});
Then("承諾済みに戻り本人以外の承諾は拒まれる", async ({ request }) => {
  const data = await statement(request);
  const day = data.days.find(
    (d: { business_date: string }) => d.business_date === date,
  );
  expect(day.acceptance_state).toBe("ACCEPTED");
  expect(day.latest_acceptance.bonus_amount).toBe(800);
  const proxy = await request.post("/api/me/statement-acceptances", {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      store_id: Number(STORE1_ID),
      business_date: date,
      content_digest: day.content_digest,
    },
  });
  expect(proxy.status()).toBe(403);
});
