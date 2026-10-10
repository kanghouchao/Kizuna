import { expect, type APIRequestContext, type Page } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { randomUUID } from "node:crypto";
import { Client } from "pg";
import { zipEntry } from "./report-export";
import { PLATFORM_URL } from "../base-url";
import {
  ADMIN_PASSWORD,
  loginViaUiAndEnterStore,
  STORE_HEADERS,
  loginPlatformUser,
  loginAsStoreAdmin,
  getAuthorizedStores,
  createPermissionRole,
  createStoreStaffFixture,
  createCast,
  createCourse,
  completeAgreedOrder,
  getOrder,
  invalidateOrder,
  cancelOrder,
  submitConfirmedStoreRequest,
} from "./store-api";
const { Given, When, Then } = createBdd();
const base = "/api/store/advertising-costs",
  months = "/api/store/advertising-cost-months";
let storeId = "",
  token = "",
  reader = "",
  exporter = "",
  foreign = "",
  email = "",
  readerEmail = "",
  orderReader = "",
  orderReaderEmail = "",
  password = "",
  month = "",
  current: any,
  original: any;
const h = (t = token, store = storeId) => ({
  ...STORE_HEADERS,
  "X-Store-ID": store,
  Authorization: `Bearer ${t}`,
});
const values = {
  category: "SALES",
  media_name: "媒体",
  agency_name: null,
  plan_name: "標準",
  inquiry_count: null,
  amount: 100,
};
async function add(
  request: APIRequestContext,
  overrides: Record<string, unknown> = {},
) {
  const r = await request.post(base, {
    headers: h(),
    data: { ...values, month, request_id: randomUUID(), ...overrides },
  });
  expect(r.status(), await r.text()).toBe(201);
  return r.json();
}
async function summary(request: APIRequestContext, m = month) {
  const r = await request.get(`${months}/${m}`, { headers: h() });
  expect(r.status(), await r.text()).toBe(200);
  return r.json();
}
async function selectMonth(page: Page, m: string) {
  await page.getByLabel("対象月", { exact: true }).fill(m);
  await page.getByRole("button", { name: "表示", exact: true }).click();
  await expect(
    page.getByText(`${m} の登録済み費用。`, { exact: false }),
  ).toBeVisible();
}
async function login(page: Page, address = email) {
  await page.goto(`${PLATFORM_URL}/platform/login`);
  await page.getByLabel("メールアドレス", { exact: true }).fill(address);
  await page.getByLabel("パスワード", { exact: true }).fill(password);
  await page.getByRole("button", { name: "ログイン", exact: true }).click();
  await expect(page).toHaveURL(/\/store\/\d+\//);
  await page.goto(`${PLATFORM_URL}/store/${storeId}/advertising-costs`);
  await expect(page.getByRole("heading", { name: "広告費管理" })).toBeVisible();
  await selectMonth(page, month);
}
Given(
  "広告費検証用の権限と店舗がある",
  async ({ request, page, $testInfo }) => {
    $testInfo.setTimeout(120000);
    storeId = await loginViaUiAndEnterStore(page);
    const owner = await loginPlatformUser(
      request,
      "admin@kizuna.test",
      ADMIN_PASSWORD,
    );
    const manager = await loginAsStoreAdmin(request);
    foreign = String(
      (await getAuthorizedStores(request, manager)).find(
        (s) => String(s.id) !== storeId,
      )!.id,
    );
    const suffix = randomUUID();
    password = randomUUID();
    month = `${($testInfo.title.includes("受注あたり") ? 1980 : $testInfo.title.includes("媒体別") ? 1970 : $testInfo.title.includes("ページ") ? 1950 : $testInfo.title.includes("応答") ? 1960 : 1940) - $testInfo.retry}-09`;
    const tokens: string[] = [];
    for (const [index, perms] of [
      [
        "ADVERTISING_COST_VIEW",
        "ADVERTISING_COST_MANAGE",
        "ADVERTISING_COST_EXPORT",
        ...($testInfo.title.includes("受注あたり") ? ["ORDER_MANAGE"] : []),
      ],
      ["ADVERTISING_COST_VIEW"],
      ["ADVERTISING_COST_EXPORT"],
      ...($testInfo.title.includes("受注あたり")
        ? [["ADVERTISING_COST_VIEW", "ORDER_MANAGE"]]
        : []),
    ].entries()) {
      const role = await createPermissionRole(
        request,
        owner,
        `広告費-${index}-${suffix}`,
        ["STORE_VIEW", "STORE_MENU_VIEW", ...perms],
      );
      const address = `advertising-${index}-${suffix}@example.test`;
      if (index === 0) email = address;
      if (index === 1) readerEmail = address;
      if (index === 3) orderReaderEmail = address;
      await createStoreStaffFixture(
        request,
        manager,
        address,
        password,
        [role],
        index === 1 || index === 3
          ? [Number(storeId)]
          : [Number(storeId), Number(foreign)],
        storeId,
      );
      tokens.push(await loginPlatformUser(request, address, password));
    }
    [token, reader, exporter, orderReader] = tokens;
  },
);
When("広告費を登録変更コピー削除する", async ({ request }) => {
  const key = randomUUID();
  original = await add(request, { request_id: key });
  const updated = {
    ...values,
    category: "RECRUITMENT",
    inquiry_count: 0,
    amount: 300,
    version: original.version,
    reason: "内訳修正",
    request_id: randomUUID(),
  };
  const edit = await request.put(`${base}/${original.id}`, {
    headers: h(),
    data: updated,
  });
  expect(edit.status(), await edit.text()).toBe(200);
  current = await edit.json();
  const replay = await request.post(base, {
    headers: h(),
    data: { ...values, month, request_id: key },
  });
  expect(replay.status()).toBe(201);
  expect(await replay.json()).toEqual(original);
  const stale = await request.put(`${base}/${current.id}`, {
    headers: h(),
    data: { ...updated, request_id: randomUUID() },
  });
  expect(stale.status()).toBe(409);
  const target = month.slice(0, 4) + "-10";
  const copy = {
    source_version: 2,
    target_version: 0,
    reason: "翌月予定",
    request_id: randomUUID(),
  };
  const copies = await Promise.all(
    [0, 1].map(() =>
      request.post(`${months}/${target}/copies`, { headers: h(), data: copy }),
    ),
  );
  for (const c of copies) expect(c.status(), await c.text()).toBe(201);
  expect(await copies[0].json()).toEqual(await copies[1].json());
  const rows = await request.get(base, {
    headers: h(),
    params: { month: target },
  });
  const copied = (await rows.json()).content;
  expect(copied).toHaveLength(1);
  expect(copied[0]).toMatchObject({
    inquiry_count: null,
    amount: 300,
    category: "RECRUITMENT",
  });
  expect(
    (
      await request.post(`${months}/${target}/copies`, {
        headers: h(),
        data: { ...copy, target_version: 1, request_id: randomUUID() },
      })
    ).status(),
  ).toBe(409);
  const removal = {
    version: current.version,
    reason: "重複登録",
    request_id: randomUUID(),
  };
  for (let i = 0; i < 2; i++)
    expect(
      (
        await request.delete(`${base}/${current.id}`, {
          headers: h(),
          data: removal,
        })
      ).status(),
    ).toBe(204);
});
Then("広告費の履歴と冪等性と店舗分離が守られる", async ({ request }) => {
  expect((await summary(request)).recorded_total_amount).toBe(0);
  const history = await request.get(`${months}/${month}/changes`, {
    headers: h(),
    params: { size: 2 },
  });
  expect(history.status(), await history.text()).toBe(200);
  const page = await history.json();
  expect(page.content).toHaveLength(2);
  expect(page.next_cursor).toBeTruthy();
  const detail = await request.get(
    `${months}/${month}/changes/${page.content[0].id}`,
    { headers: h() },
  );
  expect(await detail.json()).toMatchObject({
    reason: "重複登録",
    before: { amount: 300, inquiry_count: 0 },
    after: null,
  });
  expect(
    (await request.get(`${base}/${current.id}`, { headers: h() })).status(),
  ).toBe(404);
  expect(
    (
      await request.get(`${months}/${month}/changes/${page.content[0].id}`, {
        headers: h(token, foreign),
      })
    ).status(),
  ).toBe(404);
  expect(
    (
      await request.get(`${months}/${month}`, { headers: h(token, foreign) })
    ).status(),
  ).toBe(200);
  expect(
    (
      await request.post(base, {
        headers: h(reader),
        data: { ...values, month, request_id: randomUUID() },
      })
    ).status(),
  ).toBe(403);
  expect(
    (
      await request.get(`${base}/exports`, {
        headers: h(reader),
        params: { month, format: "csv" },
      })
    ).status(),
  ).toBe(403);
  expect(
    (
      await request.get(`${base}/exports`, {
        headers: h(exporter),
        params: { month, format: "csv" },
      })
    ).status(),
  ).toBe(403);
  for (const bad of [
    { category: 0 },
    { amount: "10" },
    { amount: 1.5 },
    { media_name: 3 },
    { month: "0000-01" },
  ])
    expect(
      (
        await request.post(base, {
          headers: h(),
          data: { ...values, month, request_id: randomUUID(), ...bad },
        })
      ).status(),
    ).toBe(400);
});
When("広告費を2001行用意する", async ({ request }) => {
  const seed = await add(request, {
    media_name: "=媒体先頭",
    inquiry_count: 0,
  });
  if (process.env.PGHOST !== "database")
    throw new Error("専用 E2E DB でのみ実行できます");
  const db = new Client({ application_name: "advertising389-bdd" });
  await db.connect();
  try {
    await db.query(
      `INSERT INTO t_advertising_costs(id,store_id,month,category,media_name,agency_name,plan_name,inquiry_count,amount,deleted,revision,version,created_at,updated_at) SELECT ('ad389-' || $2 || '-' || lpad(n::text,4,'0')),store_id,month,category,('=媒体' || lpad(n::text,4,'0')),NULL,NULL,CASE WHEN n%2=0 THEN 0 ELSE NULL END,100,false,0,0,created_at,updated_at FROM t_advertising_costs CROSS JOIN generate_series(1,2000) n WHERE id=$1`,
      [seed.id, randomUUID()],
    );
  } finally {
    await db.end();
  }
});
Then(
  "広告費のCSVとExcelに全行と型と未計測が保存される",
  async ({ request }) => {
    const csv = await request.get(`${base}/exports`, {
      headers: h(),
      params: { month, format: "csv" },
    });
    expect(csv.status(), await csv.text()).toBe(200);
    const bytes = await csv.body();
    expect(bytes.subarray(0, 3).toString("hex")).toBe("efbbbf");
    const text = bytes.toString();
    expect(text).toContain("'=媒体0001");
    expect(text).toContain("'=媒体1000");
    expect(text).toContain("'=媒体2000");
    expect(text.split("\r\n").filter((l) => l.includes("'=媒体"))).toHaveLength(
      2001,
    );
    const xlsx = await request.get(`${base}/exports`, {
      headers: h(),
      params: { month, format: "xlsx" },
    });
    expect(xlsx.status(), await xlsx.text()).toBe(200);
    const book = await xlsx.body();
    expect(zipEntry(book, "xl/workbook.xml")).toContain("広告費明細");
    const sheet = zipEntry(book, "xl/worksheets/sheet2.xml");
    expect(sheet.match(/<row /g)).toHaveLength(2002);
    expect(sheet).toContain("=媒体2000");
    expect(sheet).toContain('t="n"');
    expect(sheet).toContain('t="inlineStr"');
    expect((await summary(request)).recorded_total_amount).toBe(200100);
    for (const format of ["csv", "xlsx"]) {
      const response = await request.get(
        "/api/store/advertising-media-summaries/exports",
        { headers: h(), params: { month, format } },
      );
      expect(response.status(), await response.text()).toBe(200);
      const bytes = await response.body();
      if (format === "csv") {
        const text = bytes.toString();
        expect(
          text.split("\r\n").filter((line) => line.includes("'=媒体")),
        ).toHaveLength(2001);
        expect(text).toContain("'=媒体0001");
        expect(text).toContain("'=媒体1000");
        expect(text).toContain("'=媒体2000");
      } else {
        const sheet = zipEntry(bytes, "xl/worksheets/sheet2.xml");
        expect(sheet.match(/<row /g)).toHaveLength(2002);
        expect(sheet).toContain("=媒体2000");
        expect(sheet).not.toContain("<f>");
      }
    }
  },
);
When(
  "広告費画面で取消と二重送信と応答消失を操作する",
  async ({ page, request }) => {
    await login(page);
    await page
      .getByRole("button", { name: "広告費を登録", exact: true })
      .click();
    await page.getByLabel("媒体", { exact: true }).fill("破棄する入力");
    await page.getByRole("button", { name: "閉じる", exact: true }).click();
    await page
      .getByRole("button", { name: "広告費を登録", exact: true })
      .click();
    await expect(page.getByLabel("媒体", { exact: true })).toHaveValue("");
    await page.getByRole("button", { name: "保存する" }).click();
    await expect(page.getByText("媒体を入力してください")).toBeVisible();
    await page.getByLabel("媒体", { exact: true }).fill("応答消失検証");
    await page.getByLabel("金額（円）").fill("100");
    let count = 0;
    const requests: string[] = [];
    await page.route("**/api/store/advertising-costs", async (route) => {
      if (route.request().method() !== "POST") return route.continue();
      count++;
      requests.push(route.request().postData()!);
      await route.fetch();
      await route.abort("failed");
    });
    await page.getByRole("button", { name: "保存する" }).click();
    await expect(
      page.getByText("操作結果をまだ確認できません。", { exact: false }),
    ).toBeVisible();
    await page.getByRole("button", { name: "閉じる", exact: true }).click();
    expect(count).toBe(1);
    expect((await summary(request)).entry_count).toBe(1);
    await page.unroute("**/api/store/advertising-costs");
    await page.reload();
    await selectMonth(page, month);
    await expect(
      page.getByRole("button", { name: "同じ要求で結果を確認" }),
    ).toBeVisible();
    page.on("request", (req) => {
      if (
        req.method() === "POST" &&
        req.url().endsWith("/api/store/advertising-costs")
      )
        requests.push(req.postData()!);
    });
    await page.getByRole("tab", { name: "媒体別集計", exact: true }).click();
    await expect(
      page.getByRole("heading", { name: "媒体別集計", exact: true }),
    ).toBeVisible();
    await page.getByRole("tab", { name: "費用記録", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "同じ要求で結果を確認" }),
    ).toBeVisible();
    await page.getByRole("button", { name: "同じ要求で結果を確認" }).click();
    await expect(
      page.getByRole("button", { name: "同じ要求で結果を確認" }),
    ).toBeHidden();
    expect(requests).toHaveLength(2);
    expect(requests[0]).toBe(requests[1]);
    expect((await summary(request)).entry_count).toBe(1);
  },
);
Then(
  "広告費の画面切替と権限とテーマが安全に表示される",
  async ({ page, request, $testInfo }) => {
    await expect(page.getByText("応答消失検証", { exact: true })).toBeVisible();
    let finish!: () => void;
    const pendingSave = new Promise<void>((r) => {
      finish = r;
    });
    await page.route("**/api/store/advertising-costs", async (route) => {
      if (route.request().method() !== "POST") return route.continue();
      await pendingSave;
      const response = await route.fetch();
      await route.fulfill({ response });
    });
    await page
      .getByRole("button", { name: "広告費を登録", exact: true })
      .click();
    await page.getByLabel("媒体", { exact: true }).fill("切替中に保存");
    await page.getByLabel("金額（円）").fill("200");
    await page.getByRole("button", { name: "保存する" }).click();
    await expect(
      page.getByText("送信中です。", { exact: false }),
    ).toBeVisible();
    await page.getByRole("button", { name: "閉じる", exact: true }).click();
    await selectMonth(page, month.slice(0, 4) + "-11");
    await selectMonth(page, month);
    await expect(page.getByText("応答消失検証", { exact: true })).toBeVisible();
    await expect(page.getByText("切替中に保存", { exact: true })).toBeHidden();
    finish();
    await expect(page.getByText("切替中に保存", { exact: true })).toBeVisible();
    await expect(page.getByLabel("登録額合計", { exact: true })).toHaveText(
      "300円",
    );
    await page.unroute("**/api/store/advertising-costs");
    let release!: () => void;
    const gate = new Promise<void>((r) => {
      release = r;
    });
    await page.route("**/api/store/advertising-costs?**", async (route) => {
      const u = new URL(route.request().url());
      if (u.searchParams.get("month") === month) {
        const response = await route.fetch();
        await gate;
        await route.fulfill({ response });
      } else await route.continue();
    });
    const other = month.slice(0, 4) + "-12";
    await selectMonth(page, other);
    await selectMonth(page, month);
    await selectMonth(page, other);
    release();
    await expect(
      page.getByText("この月の広告費は登録されていません"),
    ).toBeVisible();
    await expect(page.getByText("応答消失検証", { exact: true })).toBeHidden();
    await page.unroute("**/api/store/advertising-costs?**");
    await page.goto(`${PLATFORM_URL}/store/${foreign}/advertising-costs`);
    await selectMonth(page, month);
    await expect(page.getByText("応答消失検証", { exact: true })).toBeHidden();
    await page.goto(`${PLATFORM_URL}/store/${storeId}/advertising-costs`);
    await selectMonth(page, month);
    await expect(page.getByText("応答消失検証", { exact: true })).toBeVisible();
    for (const theme of ["light", "dark"]) {
      await page
        .getByRole("button", { name: "表示モード", exact: true })
        .click();
      await page
        .getByRole("menuitemradio", {
          name: theme === "dark" ? "ダーク" : "ライト",
          exact: true,
        })
        .click();
      await page.screenshot({
        path: $testInfo.outputPath(`advertising-${theme}.png`),
        fullPage: true,
        animations: "disabled",
      });
    }
    await page.setViewportSize({ width: 760, height: 900 });
    await page
      .getByRole("button", { name: "広告費を登録", exact: true })
      .focus();
    await page.keyboard.press("Enter");
    await expect(page.getByRole("dialog")).toBeVisible();
    await page
      .getByLabel("媒体", { exact: true })
      .fill("非常に長い媒体名".repeat(20));
    await page.getByLabel("金額（円）").fill("0");
    await page.screenshot({
      path: $testInfo.outputPath("advertising-narrow.png"),
      fullPage: true,
      animations: "disabled",
    });
    await page.keyboard.press("Escape");
    await expect(page.getByRole("dialog")).toBeHidden();
    await login(page, readerEmail);
    await expect(
      page.getByRole("button", { name: "広告費を登録", exact: true }),
    ).toBeHidden();
    await expect(
      page.getByRole("button", { name: "CSV 全件出力" }),
    ).toBeHidden();
    expect(
      (
        await request.get(`${base}/exports`, {
          headers: h(reader),
          params: { month, format: "csv" },
        })
      ).status(),
    ).toBe(403);
  },
);

const mediaBase = "/api/store/advertising-media-summaries";
When(
  "同名媒体の費用と問い合わせ人数の入力状態を用意する",
  async ({ request }) => {
    for (const [media_name, inquiry_count] of [
      ["同名", 5],
      ["同名", null],
      ["全空", null],
      ["全空", null],
      ["既知零", 0],
      ["既知零", null],
      ["全部零", 0],
      ["全部零", 0],
      ["ABC", 1],
      ["abc", 2],
      ["ＡＢＣ", 3],
      ['=媒体,"試験"', null],
    ] as const)
      await add(request, { media_name, inquiry_count });
    await add(request, {
      media_name: "同名",
      category: "RECRUITMENT",
      inquiry_count: 9,
      amount: 500,
    });
    for (let i = 0; i < 25; i++)
      await add(request, {
        media_name: `追加媒体${String(i).padStart(2, "0")}`,
        inquiry_count: 0,
        amount: 0,
      });
    await add(request, {
      media_name: "長い媒体名".repeat(35),
      inquiry_count: null,
      amount: 0,
    });
    original = await add(request, {
      media_name: "原月変更",
      inquiry_count: 2,
      amount: 400,
    });
  },
);
Then(
  "媒体別集計と全件出力と古い応答の破棄を確認できる",
  async ({ request, page, $testInfo }) => {
    $testInfo.setTimeout(120000);
    const read = async (t = token, store = storeId) =>
      request.get(mediaBase, {
        headers: h(t, store),
        params: { month, size: 100 },
      });
    const response = await read();
    expect(response.status(), await response.text()).toBe(200);
    const report = await response.json();
    expect(report.recorded_total_amount).toBe(2100);
    const group = (name: string, category = "SALES") =>
      report.rows.content.find(
        (r: any) => r.media_name === name && r.category === category,
      );
    expect(group("同名")).toMatchObject({
      recorded_amount: 200,
      recorded_inquiry_count_sum: 5,
      unrecorded_inquiry_entry_count: 1,
      inquiry_status: "PARTIAL",
    });
    expect(group("同名", "RECRUITMENT")).toMatchObject({
      recorded_amount: 500,
      recorded_inquiry_count_sum: 9,
      inquiry_status: "RECORDED",
    });
    expect(group("全空")).toMatchObject({
      recorded_inquiry_count_sum: null,
      inquiry_status: "UNRECORDED",
    });
    expect(group("既知零")).toMatchObject({
      recorded_inquiry_count_sum: 0,
      inquiry_status: "PARTIAL",
    });
    expect(group("全部零")).toMatchObject({
      recorded_inquiry_count_sum: 0,
      inquiry_status: "RECORDED",
    });
    expect(group("ABC").recorded_inquiry_count_sum).toBe(1);
    expect(group("abc").recorded_inquiry_count_sum).toBe(2);
    expect(group("ＡＢＣ").recorded_inquiry_count_sum).toBe(3);
    expect((await read(reader)).status()).toBe(200);
    expect((await read(reader, foreign)).status()).toBe(403);
    expect((await read(exporter)).status()).toBe(403);
    const isolated = await (await read(token, foreign)).json();
    expect(isolated.entry_count).toBe(0);
    expect(
      (
        await request.get(mediaBase, {
          headers: h("invalid-token"),
          params: { month },
        })
      ).status(),
    ).toBe(401);
    for (const t of [reader, exporter])
      expect(
        (
          await request.get(mediaBase + "/exports", {
            headers: h(t),
            params: { month, format: "csv" },
          })
        ).status(),
      ).toBe(403);
    const invalidQueries: Record<string, string | number | boolean>[] = [
      { month, page: -1 },
      { month, size: 0 },
      { month, sort: "amount" },
      { month: "2026-13" },
    ];
    for (const params of invalidQueries)
      expect(
        (await request.get(mediaBase, { headers: h(), params })).status(),
      ).toBe(400);
    const updated = await request.put(`${base}/${original.id}`, {
      headers: h(),
      data: {
        ...values,
        media_name: "原月変更",
        inquiry_count: 4,
        amount: 600,
        version: original.version,
        reason: "集計訂正",
        request_id: randomUUID(),
      },
    });
    expect(updated.status()).toBe(200);
    original = await updated.json();
    expect((await (await read()).json()).recorded_total_amount).toBe(2300);
    expect(
      (
        await request.delete(`${base}/${original.id}`, {
          headers: h(),
          data: {
            version: original.version,
            reason: "誤登録",
            request_id: randomUUID(),
          },
        })
      ).status(),
    ).toBe(204);
    expect((await (await read()).json()).recorded_total_amount).toBe(1700);
    await login(page);
    await page.getByRole("tab", { name: "媒体別集計", exact: true }).focus();
    await page.keyboard.press("Enter");
    await expect(
      page.getByRole("heading", { name: "媒体別集計", exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole("row", { name: /営業広告 同名/ }),
    ).toContainText("5人");
    await expect(
      page.getByRole("row", { name: /営業広告 全空/ }),
    ).toContainText("全行未入力");
    await expect(
      page.getByRole("row", { name: /営業広告 既知零/ }),
    ).toContainText("0人");
    await page.route(
      "**/api/store/advertising-media-summaries?**",
      async (route) => {
        if (new URL(route.request().url()).searchParams.get("page") === "1")
          return route.abort("failed");
        return route.continue();
      },
    );
    await page
      .getByRole("button", { name: "次へ", exact: true })
      .last()
      .click();
    await expect(
      page
        .getByRole("alert")
        .filter({ hasText: "媒体別集計を取得できませんでした。" }),
    ).toContainText("媒体別集計を取得できませんでした。");
    await expect(
      page.getByRole("navigation", { name: "ページネーション" }),
    ).toHaveCount(0);
    await page.unroute("**/api/store/advertising-media-summaries?**");
    await page
      .getByRole("alert")
      .filter({ hasText: "媒体別集計を取得できませんでした。" })
      .getByRole("button")
      .click();
    await expect(
      page.getByRole("row", { name: /営業広告 同名/ }),
    ).toBeVisible();
    let releasePage!: () => void, enterPage!: () => void;
    const pageGate = new Promise<void>((r) => {
      releasePage = r;
    });
    const pageArrived = new Promise<void>((r) => {
      enterPage = r;
    });
    await page.route(
      "**/api/store/advertising-media-summaries?**",
      async (route) => {
        if (new URL(route.request().url()).searchParams.get("page") !== "1")
          return route.continue();
        const result = await route.fetch();
        enterPage();
        await pageGate;
        await route.fulfill({ response: result }).catch(() => {});
      },
    );
    await page
      .getByRole("button", { name: "次へ", exact: true })
      .last()
      .click();
    await pageArrived;
    await page.getByRole("tab", { name: "費用記録", exact: true }).click();
    await page.getByRole("tab", { name: "媒体別集計", exact: true }).click();
    await expect(
      page.getByRole("row", { name: /営業広告 同名/ }),
    ).toBeVisible();
    releasePage();
    await page.unroute("**/api/store/advertising-media-summaries?**");
    const downloadPromise = page.waitForEvent("download");
    await page.getByRole("button", { name: "CSV 全件出力" }).click();
    expect((await downloadPromise).suggestedFilename()).toBe(
      `advertising-media-summaries-${storeId}-${month}.csv`,
    );
    let releaseExport!: () => void,
      enterExport!: () => void,
      finishExport!: () => void;
    const exportGate = new Promise<void>((r) => {
      releaseExport = r;
    });
    const exportArrived = new Promise<void>((r) => {
      enterExport = r;
    });
    const exportFinished = new Promise<void>((r) => {
      finishExport = r;
    });
    let exportRequests = 0,
      lateDownloads = 0;
    page.on("download", () => {
      lateDownloads++;
    });
    await page.route(
      "**/api/store/advertising-media-summaries/exports?**",
      async (route) => {
        exportRequests++;
        const result = await route.fetch();
        enterExport();
        await exportGate;
        await route.fulfill({ response: result }).catch(() => {});
        finishExport();
      },
    );
    await page.getByRole("button", { name: "CSV 全件出力" }).dblclick();
    await exportArrived;
    await page.getByRole("tab", { name: "費用記録", exact: true }).click();
    await page.getByRole("tab", { name: "媒体別集計", exact: true }).click();
    await expect(
      page.getByRole("row", { name: /営業広告 同名/ }),
    ).toBeVisible();
    releaseExport();
    await exportFinished;
    expect(exportRequests).toBe(1);
    expect(lateDownloads).toBe(0);
    await page.unroute("**/api/store/advertising-media-summaries/exports?**");
    for (const theme of ["light", "dark"]) {
      await page
        .getByRole("button", { name: "表示モード", exact: true })
        .click();
      await page
        .getByRole("menuitemradio", {
          name: theme === "dark" ? "ダーク" : "ライト",
          exact: true,
        })
        .click();
      await page.screenshot({
        path: $testInfo.outputPath(`advertising-media-${theme}.png`),
        fullPage: true,
        animations: "disabled",
      });
    }
    await page
      .getByRole("button", { name: "次へ", exact: true })
      .last()
      .click();
    await expect(
      page.getByRole("cell", { name: "長い媒体名".repeat(35), exact: true }),
    ).toBeVisible();
    await page.setViewportSize({ width: 760, height: 900 });
    await page
      .getByRole("cell", { name: "長い媒体名".repeat(35), exact: true })
      .scrollIntoViewIfNeeded();
    await page.screenshot({
      path: $testInfo.outputPath("advertising-media-narrow.png"),
      fullPage: true,
      animations: "disabled",
    });
    let release!: () => void, entered!: () => void;
    const arrived = new Promise<void>((resolve) => {
      entered = resolve;
    });
    const held = new Promise<void>((resolve) => {
      release = resolve;
    });
    await page.route(
      "**/api/store/advertising-media-summaries?**",
      async (route) => {
        const result = await route.fetch();
        entered();
        await held;
        await route.fulfill({ response: result }).catch(() => {});
      },
    );
    await page.getByRole("tab", { name: "費用記録", exact: true }).click();
    await page.getByRole("tab", { name: "媒体別集計", exact: true }).click();
    await arrived;
    await page.unroute("**/api/store/advertising-media-summaries?**");
    const next = month.replace("-09", "-10");
    await page.getByLabel("対象月", { exact: true }).fill(next);
    await page.getByRole("button", { name: "表示", exact: true }).click();
    await expect(
      page.getByText("この月の広告費は登録されていません", { exact: true }),
    ).toBeVisible();
    release();
    await expect(
      page.getByRole("cell", { name: "同名", exact: true }),
    ).toHaveCount(0);
    await page.getByRole("tab", { name: "費用記録", exact: true }).click();
    await login(page, readerEmail);
    await page.getByRole("tab", { name: "媒体別集計", exact: true }).click();
    await expect(
      page.getByRole("row", { name: /営業広告 同名/ }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "CSV 全件出力" }),
    ).toHaveCount(0);
  },
);

const orderCostBase = "/api/store/advertising-order-costs";
let orderCostWriter = "",
  orderCostInvalidation = "",
  orderCostSeed = "";
When("営業広告と零円を含む有効受注の記録を用意する", async ({ request }) => {
  orderCostWriter = await loginAsStoreAdmin(request);
  const cast = await createCast(
    request,
    orderCostWriter,
    "広告費比較検証",
    storeId,
  );
  const course = await createCourse(
    request,
    orderCostWriter,
    "広告費比較コース",
    storeId,
  );
  async function order(
    media: string | null,
    zero = false,
    state = "COMPLETED",
    date = month + "-01",
  ) {
    const response = await submitConfirmedStoreRequest(
      request,
      orderCostWriter,
      "/api/store/orders/preview",
      "/api/store/orders",
      {
        cast_id: cast,
        course_id: course,
        fee_lines: zero
          ? [{ kind: "DISCOUNT", name: "全額割引", amount: 12000 }]
          : [],
        customer_selection: { mode: "NONE" },
        contact_snapshot: { name: "出力禁止の顧客資料" },
        business_date: date,
        media_name: media,
      },
      "post",
      storeId,
    );
    const id = (await response.json()).id;
    if (state === "COMPLETED" || state === "INVALID") {
      if (zero) {
        const current = await getOrder(request, orderCostWriter, storeId, id);
        await submitConfirmedStoreRequest(
          request,
          orderCostWriter,
          `/api/store/orders/${id}/completion-preview`,
          `/api/store/orders/${id}/completion`,
          {
            expected_version: current.version,
            fee_lines: [{ kind: "DISCOUNT", name: "全額割引", amount: 12000 }],
          },
          "post",
          storeId,
        );
      } else await completeAgreedOrder(request, orderCostWriter, id, storeId);
    }
    if (state === "INVALID")
      await invalidateOrder(
        request,
        orderCostWriter,
        id,
        "未提供の検証",
        storeId,
      );
    if (state === "CANCELLED")
      await cancelOrder(request, orderCostWriter, id, "取消の検証", storeId);
    return id;
  }
  original = await add(request, {
    media_name: "ABC",
    amount: 100,
    inquiry_count: 999,
  });
  orderCostSeed = original.id;
  await add(request, {
    category: "RECRUITMENT",
    media_name: "ABC",
    amount: 10000,
  });
  await add(request, { media_name: "零", amount: 0 });
  await add(request, { media_name: "費用のみ", amount: 500 });
  await add(request, { media_name: "ＡＢＣ", amount: 200 });
  await add(request, { media_name: "abc", amount: 300 });
  await add(request, { media_name: "長い媒体名".repeat(35), amount: 0 });
  await order("ABC");
  orderCostInvalidation = await order("ABC", true);
  await order("ABC");
  await order("ABC", false, "INVALID");
  await order("ABC", false, "CANCELLED");
  await order("ABC", false, "CONFIRMED");
  await order("ABC", false, "COMPLETED", month.replace("-09", "-10") + "-01");
  await order("零", true);
  await order("ＡＢＣ");
  await order("abc");
  await order(" ABC ");
  await order("注文のみ");
  await order(null, true);
  await order(" \t");
});
Then(
  "受注あたり記録費用の意味と全件出力と切替が守られる",
  async ({ request, page, $testInfo }) => {
    const read = (t = token, store = storeId) =>
      request.get(orderCostBase, {
        headers: h(t, store),
        params: { month, size: 100 },
      });
    let response = await read();
    expect(response.status(), await response.text()).toBe(200);
    let report = await response.json();
    expect(report).toMatchObject({
      recorded_sales_amount: 1100,
      cost_entry_count: 6,
      valid_completed_order_count: 10,
      zero_amount_order_count: 3,
      unnamed_media_order_count: 2,
    });
    const row = (name: string) =>
      report.rows.content.find((r: any) => r.media_name === name);
    expect(row("ABC")).toMatchObject({
      cost_per_order: "33.33",
      valid_completed_order_count: 3,
      zero_amount_order_count: 1,
    });
    expect(row("零")).toMatchObject({
      cost_per_order: "0.00",
      recorded_sales_amount: 0,
    });
    expect(row("費用のみ")).toMatchObject({
      cost_per_order: null,
      calculation_status: "NO_VALID_ORDERS",
    });
    expect(row("注文のみ")).toMatchObject({
      recorded_sales_amount: null,
      cost_per_order: null,
      calculation_status: "NO_COST_RECORDS",
    });
    expect(row(" ABC ").calculation_status).toBe("NO_COST_RECORDS");
    expect(row("ＡＢＣ").cost_per_order).toBe("200.00");
    expect(row("abc").cost_per_order).toBe("300.00");
    for (const t of [reader, exporter, orderCostWriter])
      expect((await read(t)).status()).toBe(403);
    expect((await read(orderReader)).status()).toBe(200);
    expect((await read(orderReader, foreign)).status()).toBe(403);
    expect(
      (await (await read(token, foreign)).json()).recorded_sales_amount,
    ).toBeNull();
    expect(
      (
        await request.get(orderCostBase, {
          headers: h("invalid-token"),
          params: { month },
        })
      ).status(),
    ).toBe(401);
    expect(
      (
        await request.get(orderCostBase, {
          headers: { Authorization: `Bearer ${token}`, "X-Role": "platform" },
          params: { month },
        })
      ).status(),
    ).toBe(403);
    expect(
      (
        await request.get(orderCostBase + "/exports", {
          headers: h(orderReader),
          params: { month, format: "csv" },
        })
      ).status(),
    ).toBe(403);
    await invalidateOrder(
      request,
      orderCostWriter,
      orderCostInvalidation,
      "原月への無効化反映",
      storeId,
    );
    report = await (await read()).json();
    expect(row("ABC").cost_per_order).toBe("50.00");
    const updated = await request.put(`${base}/${original.id}`, {
      headers: h(),
      data: {
        ...values,
        media_name: "ABC",
        amount: 200,
        version: original.version,
        reason: "原月の費用訂正",
        request_id: randomUUID(),
      },
    });
    expect(updated.status()).toBe(200);
    original = await updated.json();
    report = await (await read()).json();
    expect(row("ABC").cost_per_order).toBe("100.00");

    await login(page);
    await page
      .getByRole("tab", { name: "受注あたり記録広告費", exact: true })
      .focus();
    await page.keyboard.press("Enter");
    await expect(
      page.getByRole("row", { name: /^ABC .*約 100\.00円/ }),
    ).toContainText("約 100.00円");
    await expect(page.getByRole("row", { name: /^零 / })).toContainText(
      "約 0.00円",
    );
    await expect(page.getByText(/新客単価は未提供/)).toBeVisible();
    await expect(
      page.getByText("前後に空白あり", { exact: true }),
    ).toBeVisible();
    for (const theme of ["ライト", "ダーク"]) {
      await page
        .getByRole("button", { name: "表示モード", exact: true })
        .click();
      await page
        .getByRole("menuitemradio", { name: theme, exact: true })
        .click();
      await page.screenshot({
        path: $testInfo.outputPath(
          `advertising-order-cost-${theme === "ライト" ? "light" : "dark"}.png`,
        ),
        fullPage: true,
        animations: "disabled",
      });
    }
    await page.setViewportSize({ width: 760, height: 900 });
    await page
      .getByRole("cell", { name: "長い媒体名".repeat(35), exact: true })
      .scrollIntoViewIfNeeded();
    await page.screenshot({
      path: $testInfo.outputPath("advertising-order-cost-narrow.png"),
      fullPage: true,
      animations: "disabled",
    });

    const download = page.waitForEvent("download");
    await page.getByRole("button", { name: "CSV 全件出力" }).click();
    expect((await download).suggestedFilename()).toBe(
      `advertising-order-costs-${storeId}-${month}.csv`,
    );
    let release!: () => void, entered!: () => void, finished!: () => void;
    let requests = 0,
      lateDownloads = 0;
    const gate = new Promise<void>((r) => {
      release = r;
    });
    const arrived = new Promise<void>((r) => {
      entered = r;
    });
    const done = new Promise<void>((r) => {
      finished = r;
    });
    page.on("download", () => {
      lateDownloads++;
    });
    await page.route(
      "**/api/store/advertising-order-costs/exports?**",
      async (route) => {
        requests++;
        const result = await route.fetch();
        entered();
        await gate;
        await route.fulfill({ response: result }).catch(() => {});
        finished();
      },
    );
    await page.getByRole("button", { name: "CSV 全件出力" }).dblclick();
    await arrived;
    await page.getByRole("tab", { name: "費用記録", exact: true }).click();
    await page
      .getByRole("tab", { name: "受注あたり記録広告費", exact: true })
      .click();
    await expect(
      page.getByRole("row", { name: /^ABC .*約 100\.00円/ }),
    ).toBeVisible();
    release();
    await done;
    expect(requests).toBe(1);
    expect(lateDownloads).toBe(0);
    await page.unroute("**/api/store/advertising-order-costs/exports?**");

    let releaseRead!: () => void, enterRead!: () => void;
    const readGate = new Promise<void>((r) => {
      releaseRead = r;
    });
    const readArrived = new Promise<void>((r) => {
      enterRead = r;
    });
    await page.route(
      "**/api/store/advertising-order-costs?**",
      async (route) => {
        const result = await route.fetch();
        enterRead();
        await readGate;
        await route.fulfill({ response: result }).catch(() => {});
      },
    );
    await page.getByRole("tab", { name: "媒体別集計", exact: true }).click();
    await page
      .getByRole("tab", { name: "受注あたり記録広告費", exact: true })
      .click();
    await readArrived;
    await page.unroute("**/api/store/advertising-order-costs?**");
    await page
      .getByLabel("対象月", { exact: true })
      .fill(month.replace("-09", "-11"));
    await page.getByRole("button", { name: "表示", exact: true }).click();
    await expect(
      page.getByText("この月に比較対象の媒体記録はありません", { exact: true }),
    ).toBeVisible();
    releaseRead();
    await expect(
      page.getByRole("cell", { name: "ABC", exact: true }),
    ).toHaveCount(0);

    await page.getByLabel("対象月", { exact: true }).fill(month);
    await page.getByRole("button", { name: "表示", exact: true }).click();
    await expect(
      page.getByRole("row", { name: /^ABC .*約 100\.00円/ }),
    ).toBeVisible();
    let releaseStore!: () => void,
      enterStore!: () => void,
      finishStore!: () => void;
    const storeGate = new Promise<void>((r) => {
      releaseStore = r;
    });
    const storeArrived = new Promise<void>((r) => {
      enterStore = r;
    });
    const storeDone = new Promise<void>((r) => {
      finishStore = r;
    });
    await page.route(
      "**/api/store/advertising-order-costs?**",
      async (route) => {
        const result = await route.fetch();
        enterStore();
        await storeGate;
        await route.fulfill({ response: result }).catch(() => {});
        finishStore();
      },
    );
    await page.getByRole("tab", { name: "媒体別集計", exact: true }).click();
    await page
      .getByRole("tab", { name: "受注あたり記録広告費", exact: true })
      .click();
    await storeArrived;
    await page.unroute("**/api/store/advertising-order-costs?**");
    await page.goto(`${PLATFORM_URL}/store/${foreign}/advertising-costs`);
    await selectMonth(page, month);
    await page
      .getByRole("tab", { name: "受注あたり記録広告費", exact: true })
      .click();
    await expect(
      page.getByText("この月に比較対象の媒体記録はありません", { exact: true }),
    ).toBeVisible();
    releaseStore();
    await storeDone;
    await expect(
      page.getByRole("cell", { name: "ABC", exact: true }),
    ).toHaveCount(0);

    await login(page, readerEmail);
    await expect(
      page.getByRole("tab", { name: "受注あたり記録広告費", exact: true }),
    ).toHaveCount(0);
    await login(page, orderReaderEmail);
    await page
      .getByRole("tab", { name: "受注あたり記録広告費", exact: true })
      .click();
    await expect(
      page.getByRole("row", { name: /^ABC .*約 100\.00円/ }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "CSV 全件出力" }),
    ).toHaveCount(0);

    if (process.env.PGHOST !== "database")
      throw new Error("専用 E2E DB でのみ実行できます");
    const db = new Client({ application_name: "order-cost389-bdd" });
    await db.connect();
    const prefix = "order-cost-" + randomUUID();
    try {
      await db.query(
        `INSERT INTO t_advertising_costs(id,store_id,month,category,media_name,amount,deleted,revision,version,created_at,updated_at)
      SELECT ($2 || '-' || n),store_id,month,category,('=検証媒体' || lpad(n::text,4,'0')),0,false,0,0,created_at,updated_at
      FROM t_advertising_costs CROSS JOIN generate_series(1,2001) n WHERE id=$1`,
        [orderCostSeed, prefix],
      );
      for (const format of ["csv", "xlsx"]) {
        response = await request.get(orderCostBase + "/exports", {
          headers: h(),
          params: { month, format },
        });
        expect(response.status(), await response.text()).toBe(200);
        const bytes = await response.body();
        const content =
          format === "csv"
            ? bytes.toString()
            : zipEntry(bytes, "xl/worksheets/sheet2.xml");
        for (const n of ["0001", "1000", "2001"])
          expect(content).toContain("=検証媒体" + n);
        expect(content).not.toContain("出力禁止の顧客資料");
        if (format === "csv") {
          expect(content.startsWith("\ufeff")).toBe(true);
          expect(content).toContain("'=検証媒体");
        } else {
          expect(content).not.toContain("<f>");
          expect(content).toContain('t="inlineStr"');
        }
      }
    } finally {
      await db.end();
    }
    await login(page);
    await page
      .getByRole("tab", { name: "受注あたり記録広告費", exact: true })
      .click();
    await page.route(
      "**/api/store/advertising-order-costs?**",
      async (route) => {
        if (new URL(route.request().url()).searchParams.get("page") === "1")
          return route.abort("failed");
        return route.continue();
      },
    );
    await page
      .getByRole("button", { name: "次へ", exact: true })
      .last()
      .click();
    const alert = page
      .getByRole("alert")
      .filter({ hasText: "受注あたり記録広告費を取得できませんでした。" });
    await expect(alert).toBeVisible();
    await expect(page.getByLabel("受注比較の月合計")).toHaveCount(0);
    await page.unroute("**/api/store/advertising-order-costs?**");
    await alert.getByRole("button").click();
    await expect(
      page.getByRole("cell", { name: "=検証媒体0001", exact: true }),
    ).toBeVisible();
  },
);
