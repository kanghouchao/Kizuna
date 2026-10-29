import { expect, type APIRequestContext, type Page } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { randomUUID } from "node:crypto";
import { Client } from "pg";
import { APP_URL } from "../base-url";
import {
  ADMIN_PASSWORD,
  STORE1_ID,
  STORE_HEADERS,
  createPermissionRole,
  createStaffFixture,
  loginAsStoreAdmin,
  loginUser,
} from "./store-api";

const { Given, When, Then, After } = createBdd();
let token = "";
let managerToken = "";
let ownerToken = "";
let applicantId = "";
let applicantName = "";
const storeId = STORE1_ID;
const intake = () => ({
  name: applicantName,
  channel: "PHONE",
  source_type: "MEDIA",
  source_media: "検証媒体",
  referrer: null,
  assignee: "検証担当",
  phone: "09000000000",
  email: "applicant@example.invalid",
  address: "非公開住所",
  experience: "検証経歴",
  desired_conditions: "検証希望",
});
const headers = () => ({
  ...STORE_HEADERS,
  "X-Store-ID": storeId,
  Authorization: `Bearer ${token}`,
});
const path = () => `/api/applicants/${applicantId}`;
async function current(request: APIRequestContext) {
  const result = await request.get(path(), { headers: headers() });
  expect(result.status()).toBe(200);
  return result.json();
}

async function createRecruiter(
  request: APIRequestContext,
  permissions: string[],
) {
  const suffix = randomUUID();
  const roleId = await createPermissionRole(
    request,
    ownerToken,
    `採用検証-${suffix}`,
    permissions,
  );
  const email = `recruitment-${suffix}@example.invalid`;
  const password = randomUUID();
  const created = await request.post("/api/staff", {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${managerToken}` },
    data: {
      email,
      password,
      display_name: "採用検証担当",
      role_ids: [roleId],
      store_scope_type: "SPECIFIC_STORES",
      store_ids: [Number(STORE1_ID)],
    },
  });
  expect(created.status(), await created.text()).toBe(201);
  const login = await request.post("/api/auth/login", {
    data: { email, password },
  });
  expect(login.status()).toBe(200);
  return { email, password, token: (await login.json()).token as string };
}
async function receive(request: APIRequestContext) {
  const created = await request.post("/api/applicants", {
    headers: headers(),
    data: intake(),
  });
  expect(created.status(), await created.text()).toBe(201);
  return created.json();
}
async function decide(
  request: APIRequestContext,
  id: string,
  data: { version: number; status: string; reason: string },
  authorization = token,
) {
  return request.post(`/api/applicants/${id}/decision`, {
    headers: { ...headers(), Authorization: `Bearer ${authorization}` },
    data,
  });
}
async function interviewed(request: APIRequestContext) {
  let detail = await receive(request);
  applicantId = detail.id;
  const interview = await request.put(`${path()}/interview`, {
    headers: headers(),
    data: {
      version: detail.version,
      interview_at: "2026-10-01T10:30:00+09:00",
      interviewer: "面接担当者",
      notes: "非公開面接メモ",
      checklist: {},
    },
  });
  expect(interview.status(), await interview.text()).toBe(200);
  detail = await interview.json();
  for (const status of ["SCREENING", "INTERVIEWED"]) {
    const moved = await request.post(`${path()}/transitions`, {
      headers: headers(),
      data: { version: detail.version, status, reason: "選考を進める" },
    });
    expect(moved.status(), await moved.text()).toBe(200);
    detail = await moved.json();
  }
  return detail;
}
async function enroll(
  request: APIRequestContext,
  id: string,
  data: Record<string, unknown>,
  authorization = token,
) {
  return request.post(`/api/applicants/${id}/enrollment`, {
    headers: { ...headers(), Authorization: `Bearer ${authorization}` },
    data,
  });
}
async function foreignApplicant(request: APIRequestContext, sourceId: string) {
  const catalog = await (
    await request.get("/api/stores/me", {
      headers: { Authorization: `Bearer ${managerToken}` },
    })
  ).json();
  const otherStore = (Array.isArray(catalog) ? catalog : catalog.content).find(
    (store: { id: number }) => String(store.id) !== storeId,
  );
  expect(otherStore).toBeTruthy();
  const db = new Client();
  await db.connect();
  try {
    const insert = await db.query(
      "INSERT INTO t_applicants (id,store_id,name,channel,source_type,source_media,referrer,status,created_at,updated_at,version,modified_by,edit_sequence) SELECT $1,$2,$3,channel,source_type,source_media,referrer,status,created_at,updated_at,version,modified_by,edit_sequence FROM t_applicants WHERE id=$4 RETURNING id",
      [`outside-${randomUUID()}`, otherStore.id, "別店舗応募者", sourceId],
    );
    return { id: insert.rows[0].id as string, storeId: String(otherStore.id) };
  } finally {
    await db.end();
  }
}

async function loginAsRecruiter(
  page: Page,
  request: APIRequestContext,
  permissions: string[],
) {
  applicantName = `応募検証-${randomUUID()}`;
  managerToken = await loginAsStoreAdmin(request);
  const ownerLogin = await request.post("/api/auth/login", {
    data: { email: "admin@kizuna.test", password: ADMIN_PASSWORD },
  });
  expect(ownerLogin.status()).toBe(200);
  ownerToken = (await ownerLogin.json()).token;
  const {
    email,
    password,
    token: actorToken,
  } = await createRecruiter(request, permissions);
  token = actorToken;
  await page.goto(`${APP_URL}/auth/login`);
  await page.getByLabel("メールアドレス", { exact: true }).fill(email);
  await page.getByLabel("パスワード", { exact: true }).fill(password);
  await page.getByRole("button", { name: "ログイン", exact: true }).click();
  await expect(page).toHaveURL(/\/admin\/dashboard\/?$/, { timeout: 20000 });
}
const RECRUITER_PERMISSIONS = [
  "RECRUITMENT_VIEW",
  "RECRUITMENT_MANAGE",
  "RECRUITMENT_DECIDE",
  "STORE_VIEW",
];

Given(
  "検証専用の採用担当者でログインしている",
  async ({ page, request, $testInfo }) => {
    $testInfo.setTimeout(120000);
    await loginAsRecruiter(page, request, RECRUITER_PERMISSIONS);
  },
);

Given(
  "在籍登録もできる採用担当者でログインしている",
  async ({ page, request, $testInfo }) => {
    $testInfo.setTimeout(120000);
    await loginAsRecruiter(page, request, [
      ...RECRUITER_PERMISSIONS,
      "CAST_MANAGE",
    ]);
  },
);

When("応募者の受付と面接を画面で登録する", async ({ page }) => {
  await page.goto(`${APP_URL}/store/applicants`);
  await page.getByRole("link", { name: "応募者を登録" }).click();
  await page.getByLabel("氏名", { exact: true }).fill(applicantName);
  await page.getByLabel("電話番号", { exact: true }).fill("09000000000");
  await page.getByLabel("住所", { exact: true }).fill("非公開住所");
  await page
    .getByLabel("希望条件", { exact: true })
    .fill("長い希望条件".repeat(40));
  await page.getByRole("button", { name: "受付情報を保存" }).click();
  await expect(
    page.getByRole("heading", { name: applicantName, exact: true }),
  ).toBeVisible();
  applicantId = new URL(page.url()).pathname.split("/").at(-1)!;
  await page.getByRole("button", { name: "受付情報を編集" }).click();
  await page.getByLabel("担当者名（記録用）").fill("採用担当者".repeat(15));
  await page.getByRole("button", { name: "受付情報を保存" }).click();
  await expect(
    page.getByRole("button", { name: "受付情報を編集" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "面接記録を編集" }).click();
  await page.getByLabel("面接日時（端末の時刻）").fill("2026-10-01T10:30");
  await page.getByLabel("面接担当者名（記録用）").fill("面接担当者");
  await page.getByLabel("面接メモ", { exact: true }).fill("非公開面接メモ");
  await page.getByRole("button", { name: "確認項目を追加" }).click();
  await page.getByLabel("確認項目1の名前").fill("希望条件を確認");
  await page.getByLabel("確認項目1の確認済み").check();
  await page.getByRole("button", { name: "面接記録を保存" }).click();
  await expect(
    page.getByRole("button", { name: "面接記録を編集" }),
  ).toBeEnabled();
  await expect(page.getByText("非公開面接メモ", { exact: true })).toBeVisible();
});

Then(
  "応募者の一覧と詳細は公開範囲を区別し両テーマと狭幅で操作できる",
  async ({ page, request, $testInfo }) => {
    const result = await request.get("/api/applicants", {
      headers: headers(),
      params: { search: applicantName },
    });
    expect(result.status()).toBe(200);
    const rows = (await result.json()).content;
    expect(rows).toHaveLength(1);
    for (const key of [
      "phone",
      "email",
      "address",
      "interview",
      "experience",
      "desired_conditions",
      "referrer",
    ])
      expect(rows[0]).not.toHaveProperty(key);
    await expect(
      page.getByRole("heading", { name: "採否の確定", exact: true }),
    ).toBeVisible();
    for (const theme of ["light", "dark"]) {
      await page.evaluate((value) => {
        localStorage.setItem("theme", value);
        document.documentElement.classList.toggle("dark", value === "dark");
      }, theme);
      await page.goto(`${APP_URL}/store/applicants/${applicantId}`);
      await page.setViewportSize({ width: 1280, height: 960 });
      await expect(
        page.getByRole("heading", { name: applicantName, exact: true }),
      ).toBeVisible();
      await expect(
        page.getByRole("button", { name: "面接記録を編集" }),
      ).toBeEnabled();
      await page.getByRole("main").evaluate((element) => {
        element.scrollTop = 0;
      });
      await page.screenshot({
        path: $testInfo.outputPath(`applicant-detail-${theme}.png`),
        fullPage: true,
      });
      await page.goto(`${APP_URL}/store/applicants`);
      await expect(
        page.getByRole("heading", { name: "応募者管理", exact: true }),
      ).toBeVisible();
      await expect(
        page.getByRole("cell", { name: applicantName, exact: true }),
      ).toBeVisible();
      await expect(page.getByText("非公開住所")).toHaveCount(0);
      await page.screenshot({
        path: $testInfo.outputPath(`applicant-list-${theme}.png`),
        fullPage: true,
      });
      await page.goto(`${APP_URL}/store/applicants/${applicantId}`);
    }
    await page.setViewportSize({ width: 390, height: 844 });
    await expect(
      page.getByRole("button", { name: "受付情報を編集" }),
    ).toBeEnabled();
    await page.getByRole("button", { name: "受付情報を編集" }).focus();
    await page.keyboard.press("Enter");
    await expect(page.getByLabel("氏名", { exact: true })).toBeVisible();
    await page.getByLabel("氏名", { exact: true }).scrollIntoViewIfNeeded();
    await expect(page.getByLabel("氏名", { exact: true })).toBeInViewport();
    await page.getByRole("main").evaluate((element) => {
      const shell = element.closest<HTMLElement>(".overflow-x-auto");
      if (shell) shell.scrollLeft = 0;
    });
    await expect(page.getByText("氏名", { exact: true })).toBeInViewport();
    await page.screenshot({
      path: $testInfo.outputPath("applicant-narrow.png"),
      fullPage: true,
    });
    await page
      .getByRole("button", { name: "受付情報を保存" })
      .scrollIntoViewIfNeeded();
    await expect(
      page.getByRole("button", { name: "受付情報を保存" }),
    ).toBeInViewport();
    await page
      .getByRole("button", { name: "編集を閉じる" })
      .scrollIntoViewIfNeeded();
    await expect(
      page.getByRole("button", { name: "編集を閉じる" }),
    ).toBeInViewport();
    await page.screenshot({
      path: $testInfo.outputPath("applicant-narrow-actions.png"),
      fullPage: true,
    });
    await page.getByRole("button", { name: "編集を閉じる" }).click();
    await page.setViewportSize({ width: 1280, height: 960 });
  },
);

When("応募者の選考を進めて本人の辞退を記録する", async ({ page }) => {
  for (const [state, reason] of [
    ["選考中", "選考を開始"],
    ["面接済", "面接を実施"],
    ["辞退", "本人の希望で応募撤回"],
  ]) {
    await page.getByLabel("変更先", { exact: true }).click();
    await page.getByRole("option", { name: state, exact: true }).click();
    await page.getByLabel("変更理由", { exact: true }).fill(reason);
    await page
      .getByRole("button", { name: "選考状態を変更", exact: true })
      .click();
    if (state === "辞退")
      await page
        .getByRole("button", { name: "辞退を記録", exact: true })
        .click();
    await expect(page.getByText(reason, { exact: true })).toBeVisible();
  }
});

Then(
  "理由付き履歴が残り終了済みの応募者は変更できない",
  async ({ page, request }) => {
    await expect(
      page.getByRole("button", { name: "受付情報を編集" }),
    ).toHaveCount(0);
    const detail = await current(request);
    expect(detail.status).toBe("WITHDRAWN");
    const response = await request.post(`${path()}/transitions`, {
      headers: headers(),
      data: {
        version: detail.version,
        status: "SCREENING",
        reason: "再開不可",
      },
    });
    expect(response.status()).toBe(409);
    const history = await request.get(`${path()}/history`, {
      headers: headers(),
    });
    expect(
      (await history.json()).content.map(
        (row: { new_status: string }) => row.new_status,
      ),
    ).toEqual(["WITHDRAWN", "INTERVIEWED", "SCREENING", "RECEIVED"]);
  },
);

Then(
  "応募者APIは店舗外アクセスと通常キャスト権限を拒否する",
  async ({ request }) => {
    applicantId = (await receive(request)).id;
    // 店舗スタッフは CAST_MANAGE を持つが採用権限は既定授与されない。
    const staffToken = await loginUser(
      request,
      "yamada.jiro@kizuna.test",
      ADMIN_PASSWORD,
    );
    const denied = await request.get(path(), {
      headers: { ...STORE_HEADERS, Authorization: `Bearer ${staffToken}` },
    });
    expect(denied.status()).toBe(403);
    const foreign = await foreignApplicant(request, applicantId);
    const forbiddenStore = await request.get(path(), {
      headers: { ...headers(), "X-Store-ID": foreign.storeId },
    });
    expect(forbiddenStore.status()).toBe(403);
    for (const suffix of ["", "/history"])
      expect(
        (
          await request.get(`/api/applicants/${foreign.id}${suffix}`, {
            headers: headers(),
          })
        ).status(),
      ).toBe(404);
    expect(
      (
        await request.put(`/api/applicants/${foreign.id}`, {
          headers: headers(),
          data: { version: 0, intake: intake() },
        })
      ).status(),
    ).toBe(404);
  },
);

Then(
  "応募者APIは旧版と同時変更を拒否し面接前の採用を確定しない",
  async ({ request }) => {
    const original = await current(request);
    const update = await request.put(path(), {
      headers: headers(),
      data: { version: original.version, intake: intake() },
    });
    expect(update.status()).toBe(200);
    const edited = await update.json();
    expect(edited.version).toBe(original.version + 1);
    expect(
      (
        await request.put(path(), {
          headers: headers(),
          data: { version: original.version, intake: intake() },
        })
      ).status(),
    ).toBe(409);
    const data = {
      version: edited.version,
      status: "SCREENING",
      reason: "同時変更検証",
    };
    const concurrent = await Promise.all([
      request.post(`${path()}/transitions`, { headers: headers(), data }),
      request.post(`${path()}/transitions`, { headers: headers(), data }),
    ]);
    expect(concurrent.map((value) => value.status()).sort()).toEqual([
      200, 409,
    ]);
    const detail = await current(request);
    expect(
      (
        await decide(request, applicantId, {
          version: detail.version,
          status: "HIRED",
          reason: "面接前の採用",
        })
      ).status(),
    ).toBe(400);
    expect((await current(request)).status).toBe("SCREENING");
    for (const status of ["HIRED", "REJECTED"]) {
      const body = {
        version: detail.version,
        status,
        reason: "通常遷移の検証",
      };
      expect(
        (
          await request.post(`${path()}/transitions`, {
            headers: headers(),
            data: body,
          })
        ).status(),
      ).toBe(400);
    }
    const history = await request.get(`${path()}/history`, {
      headers: headers(),
      params: { size: 1 },
    });
    const first = await history.json();
    expect(first.content).toHaveLength(1);
    expect(first.next_cursor).toBeTruthy();
    const next = await request.get(`${path()}/history`, {
      headers: headers(),
      params: { size: 1, cursor: first.next_cursor },
    });
    expect((await next.json()).content[0].new_status).toBe("RECEIVED");
  },
);

Then(
  "応募履歴の保存が失敗すると応募者登録もロールバックされる",
  async ({ request }) => {
    const db = new Client();
    await db.connect();
    const name = `rollback-${randomUUID()}`;
    try {
      await db.query(
        "CREATE FUNCTION recruitment_test_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic history failure'; END $$",
      );
      await db.query(
        "CREATE TRIGGER recruitment_test_failure BEFORE INSERT ON t_applicant_status_histories FOR EACH ROW EXECUTE FUNCTION recruitment_test_failure()",
      );
      const failure = await request.post("/api/applicants", {
        headers: headers(),
        data: { ...intake(), name },
      });
      expect(failure.status()).toBe(500);
      expect(
        (
          await db.query("SELECT count(*) FROM t_applicants WHERE name=$1", [
            name,
          ])
        ).rows[0].count,
      ).toBe("0");
    } finally {
      await db.query(
        "DROP TRIGGER IF EXISTS recruitment_test_failure ON t_applicant_status_histories",
      );
      await db.query("DROP FUNCTION IF EXISTS recruitment_test_failure()");
      await db.end();
    }
  },
);

Then("応募者の監査には操作主体と状態だけが記録される", async () => {
  const db = new Client();
  await db.connect();
  try {
    const { rows } = await db.query(
      "SELECT * FROM t_audit_events WHERE target_type=$1 AND target_id=$2 ORDER BY id",
      ["APPLICANT", applicantId],
    );
    expect(rows.map((row) => row.action)).toEqual([
      "APPLICANT_RECEIVED",
      "APPLICANT_INTAKE_UPDATED",
      "APPLICANT_INTERVIEW_UPDATED",
      "APPLICANT_STATUS_CHANGED",
      "APPLICANT_STATUS_CHANGED",
      "APPLICANT_STATUS_CHANGED",
    ]);
    expect(rows.map((row) => row.after_values.version)).toEqual([
      "0",
      "1",
      "2",
      "3",
      "4",
      "5",
    ]);
    for (const row of rows) {
      expect(row.store_id).toBe(storeId);
      expect(row.actor_id).toBeTruthy();
      expect(row.actor_type).toBe("STAFF");
      expect(row.result).toBe("SUCCEEDED");
      expect(Object.keys(row.after_values).sort()).toEqual([
        "status",
        "version",
      ]);
      expect(Object.keys(row.before_values).sort()).toEqual(
        row.action === "APPLICANT_RECEIVED" ? [] : ["status", "version"],
      );
      expect(JSON.stringify(row)).not.toContain(applicantName);
      expect(JSON.stringify(row)).not.toContain("非公開");
    }
  } finally {
    await db.end();
  }
});

Then(
  "汎用監査の保存が失敗すると応募者と履歴もロールバックされる",
  async ({ request }) => {
    const db = new Client();
    await db.connect();
    const name = `audit-rollback-${randomUUID()}`;
    const original = await current(request);
    const histories = (
      await db.query("SELECT count(*) FROM t_applicant_status_histories")
    ).rows[0].count;
    const audits = (
      await db.query(
        "SELECT count(*) FROM t_audit_events WHERE target_type='APPLICANT'",
      )
    ).rows[0].count;
    try {
      await db.query(
        "CREATE FUNCTION recruitment_audit_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.target_type = 'APPLICANT' THEN RAISE EXCEPTION 'synthetic audit failure'; END IF; RETURN NEW; END $$",
      );
      await db.query(
        "CREATE TRIGGER recruitment_audit_failure BEFORE INSERT ON t_audit_events FOR EACH ROW EXECUTE FUNCTION recruitment_audit_failure()",
      );
      const create = await request.post("/api/applicants", {
        headers: headers(),
        data: { ...intake(), name },
      });
      expect(create.status()).toBe(500);
      expect(
        (
          await db.query("SELECT count(*) FROM t_applicants WHERE name=$1", [
            name,
          ])
        ).rows[0].count,
      ).toBe("0");
      const transition = await request.post(`${path()}/transitions`, {
        headers: headers(),
        data: {
          version: original.version,
          status: "WITHDRAWN",
          reason: "監査失敗の検証",
        },
      });
      expect(transition.status()).toBe(500);
      const unchanged = await current(request);
      expect(unchanged.version).toBe(original.version);
      expect(unchanged.status).toBe(original.status);
      expect(
        (await db.query("SELECT count(*) FROM t_applicant_status_histories"))
          .rows[0].count,
      ).toBe(histories);
      expect(
        (
          await db.query(
            "SELECT count(*) FROM t_audit_events WHERE target_type='APPLICANT'",
          )
        ).rows[0].count,
      ).toBe(audits);
    } finally {
      await db.query(
        "DROP TRIGGER IF EXISTS recruitment_audit_failure ON t_audit_events",
      );
      await db.query("DROP FUNCTION IF EXISTS recruitment_audit_failure()");
      await db.end();
    }
  },
);

When(
  "面接済の応募者を画面で採用に確定する",
  async ({ page, request, $testInfo }) => {
    await interviewed(request);
    await page.goto(`${APP_URL}/store/applicants/${applicantId}`);
    await page.getByLabel("採否", { exact: true }).click();
    await page.getByRole("option", { name: "採用", exact: true }).click();
    await page.getByLabel("確定理由", { exact: true }).fill("面接で入店に合意");
    const section = page.locator("section", {
      has: page.getByRole("heading", { name: "採否の確定", exact: true }),
    });
    for (const [width, name] of [
      [1280, "applicant-decision"],
      [390, "applicant-decision-narrow"],
    ] as const) {
      await page.setViewportSize({ width, height: 900 });
      await section.scrollIntoViewIfNeeded();
      await expect(section).toBeInViewport();
      await section.screenshot({ path: $testInfo.outputPath(`${name}.png`) });
    }
    await page.getByRole("button", { name: "採否を確定", exact: true }).click();
    await page.getByRole("button", { name: "確定する", exact: true }).click();
    await expect(
      page.getByText("選考は終了しました。編集・再開はできません。"),
    ).toBeVisible();
    await expect(
      page.getByText("面接で入店に合意", { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole("heading", { name: "採否の確定", exact: true }),
    ).toHaveCount(0);
    await expect(
      page.getByRole("button", { name: "受付情報を編集" }),
    ).toHaveCount(0);
    await page.getByRole("main").evaluate((element) => {
      element.scrollTop = 0;
    });
    await page.screenshot({
      path: $testInfo.outputPath("applicant-hired-narrow.png"),
    });
    await page.setViewportSize({ width: 1280, height: 960 });
  },
);

Then(
  "採用は状態履歴と状態だけの監査を残し再確定できない",
  async ({ request }) => {
    const detail = await current(request);
    expect(detail.status).toBe("HIRED");
    const history = await (
      await request.get(`${path()}/history`, { headers: headers() })
    ).json();
    expect(history.content[0]).toMatchObject({
      previous_status: "INTERVIEWED",
      new_status: "HIRED",
      reason: "面接で入店に合意",
    });
    const db = new Client();
    await db.connect();
    try {
      const { rows } = await db.query(
        "SELECT * FROM t_audit_events WHERE target_type=$1 AND target_id=$2 AND action=$3",
        ["APPLICANT", applicantId, "APPLICANT_DECIDED"],
      );
      expect(rows).toHaveLength(1);
      expect(rows[0].before_values).toEqual({
        status: "INTERVIEWED",
        version: String(detail.version - 1),
      });
      expect(rows[0].after_values).toEqual({
        status: "HIRED",
        version: String(detail.version),
      });
      expect(JSON.stringify(rows[0])).not.toContain("面接で入店に合意");
    } finally {
      await db.end();
    }
    expect(
      (
        await decide(request, applicantId, {
          version: detail.version,
          status: "REJECTED",
          reason: "再確定",
        })
      ).status(),
    ).toBe(409);
  },
);

Then(
  "面接前の応募者は不採用にでき面接なしの採用は拒否される",
  async ({ request }) => {
    const detail = await receive(request);
    applicantId = detail.id;
    const reject = { version: detail.version, status: "REJECTED" };
    expect(
      (await decide(request, applicantId, { ...reject, reason: " " })).status(),
    ).toBe(400);
    expect(
      (
        await decide(request, applicantId, {
          version: detail.version,
          status: "HIRED",
          reason: "面接なしの採用",
        })
      ).status(),
    ).toBe(400);
    expect(
      (
        await decide(request, applicantId, {
          ...reject,
          version: detail.version + 1,
          reason: "旧版",
        })
      ).status(),
    ).toBe(409);
    expect((await current(request)).status).toBe("RECEIVED");
    const rejected = await decide(request, applicantId, {
      ...reject,
      reason: "書類で見送り",
    });
    expect(rejected.status(), await rejected.text()).toBe(200);
    expect((await rejected.json()).status).toBe("REJECTED");
    const history = await (
      await request.get(`${path()}/history`, { headers: headers() })
    ).json();
    expect(history.content[0]).toMatchObject({
      previous_status: "RECEIVED",
      new_status: "REJECTED",
      reason: "書類で見送り",
    });
  },
);

Then(
  "採否確定は確定権限の無い者を拒否し他店舗の応募者を見つけない",
  async ({ request }) => {
    const detail = await receive(request);
    const manager = await createRecruiter(request, [
      "RECRUITMENT_VIEW",
      "RECRUITMENT_MANAGE",
      "STORE_VIEW",
    ]);
    const body = {
      version: detail.version,
      status: "REJECTED",
      reason: "権限検証",
    };
    expect(
      (await decide(request, detail.id, body, manager.token)).status(),
    ).toBe(403);
    const foreign = await foreignApplicant(request, detail.id);
    expect((await decide(request, foreign.id, body)).status()).toBe(404);
    applicantId = detail.id;
    expect((await current(request)).status).toBe("RECEIVED");
  },
);

const RETENTION_KEYS = [
  "applicant_retention_rejected_months",
  "applicant_retention_withdrawn_months",
  "applicant_retention_hired_months",
];
async function putConfig(
  request: APIRequestContext,
  authorization: string,
  key: string,
  value: string,
) {
  return request.put(`/api/system-configs/${key}`, {
    headers: { ...STORE_HEADERS, Authorization: `Bearer ${authorization}` },
    data: { config_value: value },
  });
}

When("管理者が応募者の保持期間を不採用だけ設定する", async ({ request }) => {
  for (const [key, value] of [
    ["applicant_retention_rejected_months", "6"],
    ["applicant_retention_withdrawn_months", ""],
    ["applicant_retention_hired_months", ""],
  ]) {
    const response = await putConfig(request, ownerToken, key, value);
    expect(response.status(), await response.text()).toBe(200);
  }
});

Then(
  "保持期間の設定は負数と非整数と設定権限の無い者を拒否する",
  async ({ request }) => {
    for (const value of ["-1", "1201", "1.5", "abc"]) {
      const response = await putConfig(
        request,
        ownerToken,
        "applicant_retention_hired_months",
        value,
      );
      expect(response.status(), value).toBe(400);
    }
    const denied = await putConfig(
      request,
      token,
      "applicant_retention_hired_months",
      "12",
    );
    expect(denied.status()).toBe(403);
    const configs = await request.get("/api/system-configs", {
      headers: { ...STORE_HEADERS, Authorization: `Bearer ${ownerToken}` },
      params: { category: "RECRUITMENT" },
    });
    expect(configs.status()).toBe(200);
    const values = Object.fromEntries(
      ((await configs.json()) as { config_key: string; config_value: string }[])
        .filter((config) => RETENTION_KEYS.includes(config.config_key))
        .map((config) => [config.config_key, config.config_value ?? ""]),
    );
    expect(values).toEqual({
      applicant_retention_rejected_months: "6",
      applicant_retention_withdrawn_months: "",
      applicant_retention_hired_months: "",
    });
  },
);

Then(
  "保持方針は設定済みの月数を返し未設定を自動匿名化しないと両テーマと狭幅で示す",
  async ({ page, request, $testInfo }) => {
    const policy = await request.get("/api/applicants/policy", {
      headers: headers(),
    });
    expect(policy.status()).toBe(200);
    expect(await policy.json()).toEqual({
      retention_periods: {
        rejected_months: 6,
        withdrawn_months: null,
        hired_months: null,
      },
    });
    applicantId = (await receive(request)).id;
    const value = (label: string) =>
      page
        .locator("section", {
          has: page.getByRole("heading", { name: "情報保持方針" }),
        })
        .getByText(label, { exact: true })
        .locator("xpath=following-sibling::dd");
    for (const [theme, width] of [
      ["light", 1280],
      ["dark", 1280],
      ["light", 390],
    ] as const) {
      await page.evaluate((mode) => {
        localStorage.setItem("theme", mode);
        document.documentElement.classList.toggle("dark", mode === "dark");
      }, theme);
      await page.setViewportSize({ width, height: 960 });
      await page.goto(`${APP_URL}/store/applicants/${applicantId}`);
      await expect(value("不採用")).toHaveText("保持 6 か月");
      await expect(value("辞退")).toHaveText("自動匿名化しない");
      await expect(value("採用")).toHaveText("自動匿名化しない");
      await page
        .getByRole("heading", { name: "情報保持方針", exact: true })
        .scrollIntoViewIfNeeded();
      await page.screenshot({
        path: $testInfo.outputPath(`applicant-retention-${theme}-${width}.png`),
        fullPage: true,
      });
    }
    await page.setViewportSize({ width: 1280, height: 960 });
  },
);

Given(
  "添付も扱える採用担当者でログインしている",
  async ({ page, request, $testInfo }) => {
    $testInfo.setTimeout(120000);
    await loginAsRecruiter(page, request, [
      ...RECRUITER_PERMISSIONS,
      "RECRUITMENT_ATTACHMENT_VIEW",
      "RECRUITMENT_ATTACHMENT_MANAGE",
    ]);
  },
);

let anonymizeOperator = "";
let anonymizeViewer = "";
let anonymizeServiceId = 0;
let incompleteServiceId = 0;
const today = () =>
  new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Tokyo" }).format(
    new Date(),
  );
const anonymization = (serviceUserId: number) => ({
  task_name: "APPLICANT_ANONYMIZATION",
  logical_key: randomUUID(),
  service_user_id: serviceUserId,
  store_id: null,
  period_start: today(),
  period_end: today(),
});

When(
  "添付付きの応募者を面接後に不採用へ確定し不採用の保持期間を0か月にする",
  async ({ page, request }) => {
    let detail = await interviewed(request);
    const image = Buffer.from(
      await page.evaluate(() => {
        const canvas = document.createElement("canvas");
        canvas.width = 8;
        canvas.height = 8;
        return canvas.toDataURL("image/png").split(",")[1];
      }),
      "base64",
    );
    const uploaded = await request.post(`${path()}/attachments`, {
      headers: {
        ...headers(),
        "Content-Type": "image/png",
        "Idempotency-Key": randomUUID(),
      },
      data: image,
    });
    expect(uploaded.status(), await uploaded.text()).toBe(201);
    detail = await current(request);
    const rejected = await decide(request, applicantId, {
      version: detail.version,
      status: "REJECTED",
      reason: "検証の不採用理由",
    });
    expect(rejected.status(), await rejected.text()).toBe(200);
    for (const [key, value] of [
      ["applicant_retention_rejected_months", "0"],
      ["applicant_retention_withdrawn_months", ""],
      ["applicant_retention_hired_months", ""],
    ]) {
      const response = await putConfig(request, ownerToken, key, value);
      expect(response.status(), await response.text()).toBe(200);
    }
  },
);

Then(
  "匿名化の手動実行は匿名化権限の無い運用者と権限の足りないサービスIDを拒否する",
  async ({ request }) => {
    const suffix = randomUUID();
    const password = randomUUID();
    const operatorRole = await createPermissionRole(
      request,
      ownerToken,
      `匿名化運用-${suffix}`,
      ["TASK_MANAGE", "APPLICANT_ANONYMIZE"],
    );
    const viewerRole = await createPermissionRole(
      request,
      ownerToken,
      `匿名化閲覧-${suffix}`,
      ["TASK_MANAGE"],
    );
    for (const [role, prefix] of [
      [operatorRole, "operator"],
      [viewerRole, "viewer"],
    ] as const) {
      const email = `anonymize-${prefix}-${suffix}@example.invalid`;
      await createStaffFixture(request, ownerToken, email, password, [role]);
      const login = await request.post("/api/auth/login", {
        data: { email, password },
      });
      expect(login.status()).toBe(200);
      if (role === operatorRole) anonymizeOperator = (await login.json()).token;
      else anonymizeViewer = (await login.json()).token;
    }
    const completeRole = await createPermissionRole(
      request,
      ownerToken,
      `匿名化処理-${suffix}`,
      ["TASK_EXECUTE", "APPLICANT_ANONYMIZE"],
    );
    const incompleteRole = await createPermissionRole(
      request,
      ownerToken,
      `実行のみ-${suffix}`,
      ["TASK_EXECUTE"],
    );
    for (const roleId of [completeRole, incompleteRole]) {
      const response = await request.post("/api/service-identities", {
        headers: { ...STORE_HEADERS, Authorization: `Bearer ${ownerToken}` },
        data: {
          display_name: `匿名化サービス-${roleId}-${suffix}`,
          role_ids: [roleId],
          store_scope_type: "ALL_STORES",
          store_ids: [],
        },
      });
      expect(response.status(), await response.text()).toBe(201);
      const id = (await response.json()).id;
      if (roleId === completeRole) anonymizeServiceId = id;
      else incompleteServiceId = id;
    }
    const run = (authorization: string, serviceUserId: number) =>
      request.post("/api/task-executions", {
        headers: { ...STORE_HEADERS, Authorization: `Bearer ${authorization}` },
        data: anonymization(serviceUserId),
      });
    expect((await run(anonymizeViewer, anonymizeServiceId)).status()).toBe(403);
    expect((await run(anonymizeOperator, incompleteServiceId)).status()).toBe(
      403,
    );
    expect((await current(request)).anonymized_at).toBeUndefined();
  },
);

When(
  "匿名化権限を持つ運用者がサービスIDで匿名化を手動実行する",
  async ({ request }) => {
    const response = await request.post("/api/task-executions", {
      headers: {
        ...STORE_HEADERS,
        Authorization: `Bearer ${anonymizeOperator}`,
      },
      data: anonymization(anonymizeServiceId),
    });
    expect(response.status(), await response.text()).toBe(201);
    const execution = (await response.json()).execution;
    expect(execution.status).toBe("SUCCEEDED");
    expect(execution.processed_count).toBeGreaterThanOrEqual(1);
  },
);

Then(
  "匿名化後の応募者詳細は個人情報と添付を返さず選考の経緯を残す",
  async ({ request }) => {
    const detail = await current(request);
    expect(detail).toMatchObject({
      id: applicantId,
      name: "匿名化済み応募者",
      status: "REJECTED",
      assignee: "検証担当",
      source_media: "検証媒体",
      interview: {
        interview_at: expect.any(String),
        interviewer: "面接担当者",
        checklist: {},
      },
    });
    expect(detail.anonymized_at).toEqual(expect.any(String));
    for (const field of [
      "phone",
      "email",
      "address",
      "experience",
      "desired_conditions",
      "referrer",
    ])
      expect(detail[field], field).toBeUndefined();
    expect(detail.interview.notes).toBeUndefined();
    expect(JSON.stringify(detail)).not.toContain(applicantName);
    const attachments = await request.get(`${path()}/attachments`, {
      headers: headers(),
    });
    expect(attachments.status()).toBe(200);
    expect((await attachments.json()).content).toEqual([]);
    const history = await request.get(`${path()}/history`, {
      headers: headers(),
    });
    expect(history.status()).toBe(200);
    expect(JSON.stringify(await history.json())).toContain("検証の不採用理由");
    const db = new Client();
    await db.connect();
    try {
      const audit = await db.query(
        "SELECT before_values, after_values, source_type FROM t_audit_events WHERE action='APPLICANT_ANONYMIZED' AND target_id=$1",
        [applicantId],
      );
      expect(audit.rows).toHaveLength(1);
      expect(audit.rows[0].source_type).toBe("TASK_EXECUTION");
      expect(Object.keys(audit.rows[0].after_values).sort()).toEqual([
        "status",
        "version",
      ]);
    } finally {
      await db.end();
    }
  },
);

Then(
  "匿名化済みの応募者は一覧と詳細で分かり両テーマと狭幅で示す",
  async ({ page, $testInfo }) => {
    for (const [theme, width] of [
      ["light", 1280],
      ["dark", 1280],
      ["light", 390],
    ] as const) {
      await page.evaluate((mode) => {
        localStorage.setItem("theme", mode);
        document.documentElement.classList.toggle("dark", mode === "dark");
      }, theme);
      await page.setViewportSize({ width, height: 960 });
      await page.goto(`${APP_URL}/store/applicants/${applicantId}`);
      await expect(
        page.getByRole("heading", { name: "匿名化済み応募者", exact: true }),
      ).toBeVisible();
      await expect(page.getByText("匿名化済み", { exact: true })).toBeVisible();
      await expect(
        page.getByText(/保持期間の満了により .* に匿名化しました/),
      ).toBeVisible();
      await page.screenshot({
        path: $testInfo.outputPath(
          `applicant-anonymized-${theme}-${width}.png`,
        ),
        fullPage: true,
      });
    }
    await page.setViewportSize({ width: 1280, height: 960 });
    await page.goto(`${APP_URL}/store/applicants`);
    const row = page.getByRole("row").filter({
      has: page.locator(`a[href$="/applicants/${applicantId}"]`),
    });
    await page.getByLabel("応募者氏名").fill("匿名化済み応募者");
    await page.getByRole("button", { name: "検索", exact: true }).click();
    await expect(row.getByText("匿名化済み", { exact: true })).toBeVisible();
  },
);

// 保持期間を残すと、後続の匿名化処理が共有店舗の終端応募者を消しうる。既知の未設定へ戻すだけなので番兵は置かない。
After(
  { tags: "@applicant-retention or @applicant-anonymization" },
  async ({ request }) => {
    const login = await request.post("/api/auth/login", {
      data: { email: "admin@kizuna.test", password: ADMIN_PASSWORD },
    });
    expect(login.status()).toBe(200);
    const admin = (await login.json()).token as string;
    for (const key of RETENTION_KEYS) {
      const response = await putConfig(request, admin, key, "");
      expect(response.status(), await response.text()).toBe(200);
    }
  },
);

let stageName = "";
let enrollmentId = "";

When("面接済の応募者を採用に確定する", async ({ request }) => {
  const detail = await interviewed(request);
  const hired = await decide(request, applicantId, {
    version: detail.version,
    status: "HIRED",
    reason: "面接で入店に合意",
  });
  expect(hired.status(), await hired.text()).toBe(200);
  expect(await hired.json()).not.toHaveProperty("enrollment_id");
});

When(
  "採用済みの応募者から画面で在籍を登録する",
  async ({ page, $testInfo }) => {
    stageName = `在籍検証-${randomUUID().slice(0, 8)}`;
    await page.goto(`${APP_URL}/store/applicants/${applicantId}`);
    const section = page.locator("section", {
      has: page.getByRole("heading", { name: "在籍", exact: true }),
    });
    await expect(section.getByText("在籍は未登録です。")).toBeVisible();
    await section.getByRole("link", { name: "在籍登録", exact: true }).click();
    await expect(
      page.getByRole("heading", { name: "在籍登録", exact: true }),
    ).toBeVisible();
    // 応募者の氏名・連絡先は源氏名の初期値に写さない。
    await expect(page.getByLabel("源氏名 *")).toHaveValue("");
    await page.getByLabel("源氏名 *").fill(stageName);
    await page.getByLabel("入店日 *").fill("2026-11-01");
    for (const theme of ["light", "dark"]) {
      await page.evaluate((value) => {
        localStorage.setItem("theme", value);
        document.documentElement.classList.toggle("dark", value === "dark");
      }, theme);
      for (const [width, suffix] of [
        [1280, ""],
        [390, "-narrow"],
      ] as const) {
        await page.setViewportSize({ width, height: 900 });
        await page.screenshot({
          path: $testInfo.outputPath(
            `applicant-enrollment-${theme}${suffix}.png`,
          ),
          fullPage: true,
        });
      }
    }
    await page.evaluate(() => {
      localStorage.setItem("theme", "light");
      document.documentElement.classList.remove("dark");
    });
    await page.setViewportSize({ width: 1280, height: 960 });
    await page.getByRole("button", { name: "保存する", exact: true }).click();
    await expect(page).toHaveURL(
      new RegExp(`/store/applicants/${applicantId}$`),
    );
    await expect(
      page.getByRole("link", { name: "在籍を開く", exact: true }),
    ).toBeVisible();
    await page.setViewportSize({ width: 390, height: 844 });
    await page
      .locator("section", {
        has: page.getByRole("heading", { name: "在籍", exact: true }),
      })
      .screenshot({
        path: $testInfo.outputPath("applicant-enrolled-narrow.png"),
      });
    await page.setViewportSize({ width: 1280, height: 960 });
  },
);

Then(
  "在籍は未公開・招待なしで在籍一覧に出て応募者詳細から開ける",
  async ({ page, request }) => {
    const detail = await current(request);
    expect(detail.status).toBe("HIRED");
    enrollmentId = detail.enrollment_id;
    expect(enrollmentId).toBeTruthy();
    const list = await (
      await request.get("/api/applicants", {
        headers: headers(),
        params: { search: applicantName },
      })
    ).json();
    expect(list.content[0]).toMatchObject({
      id: applicantId,
      enrollment_id: enrollmentId,
    });
    const cast = await request.get(`/api/casts/${enrollmentId}`, {
      headers: headers(),
    });
    expect(cast.status(), await cast.text()).toBe(200);
    expect(await cast.json()).toMatchObject({
      id: enrollmentId,
      name: stageName,
      status: "ENROLLED",
      started_on: "2026-11-01",
      publication_status: "UNPUBLISHED",
      invitation_status: "NOT_INVITED",
    });
    expect(JSON.stringify(await cast.json())).not.toContain("非公開");
    await page.getByRole("link", { name: "在籍を開く", exact: true }).click();
    await expect(page).toHaveURL(
      new RegExp(`/store/casts/${enrollmentId}/edit$`),
    );
    await page.goto(`${APP_URL}/store/casts`);
    await page.getByPlaceholder("名前で検索...").fill(stageName);
    await page.getByRole("button", { name: "検索", exact: true }).click();
    await expect(
      page.getByRole("cell", { name: stageName, exact: true }),
    ).toBeVisible();
  },
);

Then(
  "同じ応募者への二度目の在籍登録と採用前の在籍登録は拒否される",
  async ({ request }) => {
    const detail = await current(request);
    const body = { name: `${stageName}-2`, started_on: "2026-11-01" };
    const again = await enroll(request, applicantId, {
      ...body,
      version: detail.version,
    });
    expect(again.status(), await again.text()).toBe(409);
    const stale = await enroll(request, applicantId, {
      ...body,
      version: detail.version - 1,
    });
    expect(stale.status()).toBe(409);
    expect((await current(request)).enrollment_id).toBe(enrollmentId);
    const open = await receive(request);
    const early = await enroll(request, open.id, {
      ...body,
      version: open.version,
    });
    expect(early.status(), await early.text()).toBe(400);
    const casts = await (
      await request.get("/api/casts", {
        headers: headers(),
        params: { search: `${stageName}-2` },
      })
    ).json();
    expect(casts.content).toHaveLength(0);
  },
);

Then(
  "在籍登録はキャスト管理権限の無い者を拒否し他店舗の応募者を見つけない",
  async ({ request }) => {
    const detail = await interviewed(request);
    const hired = await decide(request, detail.id, {
      version: detail.version,
      status: "HIRED",
      reason: "権限検証",
    });
    expect(hired.status()).toBe(200);
    const version = (await hired.json()).version;
    const recruiter = await createRecruiter(request, RECRUITER_PERMISSIONS);
    const body = { version, name: "権限検証", started_on: "2026-11-01" };
    const stale = await enroll(request, detail.id, {
      ...body,
      version: version - 1,
    });
    expect(stale.status(), await stale.text()).toBe(409);
    expect(
      (await enroll(request, detail.id, body, recruiter.token)).status(),
    ).toBe(403);
    const foreign = await foreignApplicant(request, detail.id);
    expect((await enroll(request, foreign.id, body)).status()).toBe(404);
    expect(await current(request)).not.toHaveProperty("enrollment_id");
  },
);
