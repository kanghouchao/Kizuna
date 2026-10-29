import { expect } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { randomUUID } from "node:crypto";
import {
  ADMIN_PASSWORD,
  createPermissionRole,
  createStaffFixture,
  getAuditEvent,
  listAuditEvents,
  loginUser,
} from "./store-api";

const { Given, When, Then, After } = createBdd();
let operator = "",
  limited = "",
  auditor = "",
  operatorId = 0,
  secretName = "",
  activated = "",
  discarded = "",
  untouched = "";

const bearer = (token: string, storeId?: string) => ({
  Authorization: `Bearer ${token}`,
  ...(storeId ? { "X-Store-ID": storeId } : {}),
});

Given(
  "店舗登録監査専用の担当と監査者が用意されている",
  async ({ request, $testInfo }) => {
    $testInfo.setTimeout(120000);
    const suffix = randomUUID();
    const password = randomUUID();
    secretName = `店舗監査へ複写しない名称-${suffix}`;
    const owner = await loginUser(request, "admin@kizuna.test", ADMIN_PASSWORD);
    const operatorRole = await createPermissionRole(
      request,
      owner,
      `店舗登録担当-${suffix}`,
      ["STORE_MANAGE", "STORE_PROFILE_MANAGE", "STORE_VIEW"],
    );
    const operatorEmail = `store-operator-${suffix}@example.test`;
    operatorId = await createStaffFixture(
      request,
      owner,
      operatorEmail,
      password,
      [operatorRole],
    );
    operator = await loginUser(request, operatorEmail, password);
    const limitedRole = await createPermissionRole(
      request,
      owner,
      `店舗登録権限なし-${suffix}`,
      ["SHIFT_MANAGE", "STORE_VIEW"],
    );
    const limitedEmail = `store-limited-${suffix}@example.test`;
    await createStaffFixture(request, owner, limitedEmail, password, [
      limitedRole,
    ]);
    limited = await loginUser(request, limitedEmail, password);
    const auditRole = await createPermissionRole(
      request,
      owner,
      `店舗登録監査-${suffix}`,
      ["AUDIT_VIEW"],
    );
    const auditEmail = `store-auditor-${suffix}@example.test`;
    await createStaffFixture(request, owner, auditEmail, password, [auditRole]);
    auditor = await loginUser(request, auditEmail, password);
  },
);

// 略称は一意制約があり、リトライで worker が替わると前回の試行の店舗が残る。重複（409）なら引き直す。
const shortNames = new Map<string, string>();
const SHORT_NAME_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

function randomShortName(): string {
  return Array.from(
    { length: 3 },
    () => SHORT_NAME_ALPHABET[Math.floor(Math.random() * SHORT_NAME_ALPHABET.length)],
  ).join("");
}

async function register(
  request: import("@playwright/test").APIRequestContext,
  name: string,
): Promise<string> {
  for (let attempt = 0; attempt < 5; attempt++) {
    const shortName = randomShortName();
    const created = await request.post("/api/stores", {
      headers: bearer(operator),
      data: { name, short_name: shortName, domain: `audit-${randomUUID()}.kizuna.test` },
    });
    if (created.status() === 409) continue;
    expect(created.status(), await created.text()).toBe(201);
    const id = String((await created.json()).id);
    shortNames.set(id, shortName);
    return id;
  }
  throw new Error("略称の重複が続き、店舗を登録できませんでした");
}

When(
  "担当が店舗を作成・改名・稼働化・削除し同値の更新と拒否される操作も試す",
  async ({ request }) => {
    const denied = await request.post("/api/stores", {
      headers: bearer(limited),
      data: { name: secretName, short_name: "x", domain: `denied-${randomUUID()}.kizuna.test` },
    });
    expect(denied.status()).toBe(403);

    activated = await register(request, secretName);
    discarded = await register(request, secretName);
    untouched = await register(request, secretName);

    const renamed = await request.put(`/api/stores/${activated}`, {
      headers: bearer(operator),
      data: { name: `${secretName}-改名`, short_name: shortNames.get(activated) },
    });
    expect(renamed.status()).toBe(204);
    const same = await request.put(`/api/stores/${activated}`, {
      headers: bearer(operator),
      data: { name: `${secretName}-改名`, short_name: shortNames.get(activated) },
    });
    expect(same.status()).toBe(204);

    const activation = await request.post("/api/config/activation", {
      headers: bearer(operator, activated),
    });
    expect(activation.status(), await activation.text()).toBe(204);
    const again = await request.post("/api/config/activation", {
      headers: bearer(operator, activated),
    });
    expect(again.status()).toBe(400);

    const refused = await request.delete(`/api/stores/${activated}`, {
      headers: bearer(operator),
    });
    expect(refused.status()).toBe(400);
    const forbidden = await request.delete(`/api/stores/${discarded}`, {
      headers: bearer(limited),
    });
    expect(forbidden.status()).toBe(403);

    const removed = await request.delete(`/api/stores/${discarded}`, {
      headers: bearer(operator),
    });
    expect(removed.status()).toBe(204);
  },
);

Then(
  "成功した操作だけが店舗 ID と状態の前後で監査され値は複写されない",
  async ({ request }) => {
    const mine = async (action: string) =>
      (await listAuditEvents(request, auditor, action)).content.filter((e) =>
        [activated, discarded, untouched].includes(e.target_id),
      );
    const detail = async (id: number) => getAuditEvent(request, auditor, id);

    const created = await mine("STORE_CREATED");
    expect(created.map((e) => e.target_id).sort()).toEqual(
      [activated, discarded, untouched].sort(),
    );
    for (const event of created) {
      expect(event.target_type).toBe("STORE");
      expect(event.actor_id).toBe(operatorId);
      expect(event.store_id).toBe(Number(event.target_id));
      const { before_values: before, after_values: after } = await detail(
        event.id,
      );
      expect(before).toEqual({});
      expect(after.exists).toBe("true");
      expect(after.status).toBe("PREPARING");
      expect(after.version).toBe("0");
      expect(after.changed_fields).toBe("name,short_name,domain");
    }

    const updated = await mine("STORE_UPDATED");
    expect(updated).toHaveLength(1);
    expect(updated[0].target_id).toBe(activated);
    expect(updated[0].store_id).toBe(Number(activated));
    const update = await detail(updated[0].id);
    expect(update.after_values.changed_fields).toBe("name");
    expect(Number(update.after_values.version)).toBe(
      Number(update.before_values.version) + 1,
    );

    const activations = await mine("STORE_ACTIVATED");
    expect(activations).toHaveLength(1);
    expect(activations[0].target_id).toBe(activated);
    expect(activations[0].store_id).toBe(Number(activated));
    const activation = await detail(activations[0].id);
    expect(activation.before_values.status).toBe("PREPARING");
    expect(activation.after_values.status).toBe("ACTIVE");

    const deletions = await mine("STORE_DELETED");
    expect(deletions).toHaveLength(1);
    expect(deletions[0].target_id).toBe(discarded);
    expect(deletions[0].store_id).toBe(Number(discarded));
    const deletion = await detail(deletions[0].id);
    expect(deletion.before_values.exists).toBe("true");
    expect(deletion.before_values.status).toBe("PREPARING");
    expect(deletion.after_values.exists).toBe("false");
    expect(deletion.after_values.cascade_deleted).toContain("t_store_profiles=1");

    const everything = JSON.stringify(
      await Promise.all(
        [...created, ...updated, ...activations, ...deletions].map((e) =>
          detail(e.id),
        ),
      ),
    );
    expect(everything).not.toContain(secretName);
  },
);

// 稼働化した店舗は仕様上削除できないため残る。それ以外の作成物はここで戻す。
After({ tags: "@store-registry-audit" }, async ({ request }) => {
  if (!operator || !untouched) return;
  const cleanup = await request.delete(`/api/stores/${untouched}`, {
    headers: bearer(operator),
  });
  expect(cleanup.status()).toBe(204);
  untouched = "";
});
