import { expect } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { randomUUID } from "node:crypto";
import {
  ADMIN_PASSWORD,
  STORE1_ID,
  STORE_HEADERS,
  acceptCastInvitation,
  createPermissionRole,
  createStaffFixture,
  createStoreStaffFixture,
  createUnpublishedCast,
  getAuditEvent,
  getAuthorizedStores,
  issueCastInvitation,
  listAuditEvents,
  loginAsStoreAdmin,
  loginUser,
} from "./store-api";

const { Given, When, Then } = createBdd();
let operator = "",
  limited = "",
  auditor = "",
  operatorId = 0,
  otherStore = "",
  secret = "",
  password = "";
let draft = "",
  linked = "",
  foreign = "";

const headers = (token: string, storeId = STORE1_ID) => ({
  ...STORE_HEADERS,
  "X-Store-ID": storeId,
  Authorization: `Bearer ${token}`,
});

Given(
  "削除監査専用の担当と監査者が用意されている",
  async ({ request, $testInfo }) => {
    $testInfo.setTimeout(120000);
    const suffix = randomUUID();
    password = randomUUID();
    secret = `削除監査へ複写しない秘密-${suffix}`;
    const manager = await loginAsStoreAdmin(request);
    const stores = await getAuthorizedStores(request, manager);
    const other = stores.find((store) => String(store.id) !== STORE1_ID);
    expect(other).toBeDefined();
    otherStore = String(other!.id);
    const owner = await loginUser(
      request,
      "admin@kizuna.test",
      ADMIN_PASSWORD,
    );
    const operatorRole = await createPermissionRole(
      request,
      owner,
      `削除担当-${suffix}`,
      ["CAST_MANAGE", "CAST_INVITE", "STORE_VIEW"],
    );
    const operatorEmail = `delete-operator-${suffix}@example.test`;
    operatorId = await createStoreStaffFixture(
      request,
      manager,
      operatorEmail,
      password,
      [operatorRole],
      stores.map((store) => store.id),
    );
    operator = await loginUser(request, operatorEmail, password);
    const limitedRole = await createPermissionRole(
      request,
      owner,
      `勤務のみ-${suffix}`,
      ["SHIFT_MANAGE", "STORE_VIEW"],
    );
    const limitedEmail = `delete-limited-${suffix}@example.test`;
    await createStoreStaffFixture(
      request,
      manager,
      limitedEmail,
      password,
      [limitedRole],
      [Number(STORE1_ID)],
    );
    limited = await loginUser(request, limitedEmail, password);
    const auditRole = await createPermissionRole(
      request,
      owner,
      `削除監査-${suffix}`,
      ["AUDIT_VIEW"],
    );
    const auditEmail = `delete-auditor-${suffix}@example.test`;
    await createStaffFixture(request, owner, auditEmail, password, [auditRole]);
    auditor = await loginUser(request, auditEmail, password);
  },
);

When("担当が草稿を削除し拒否される削除も試す", async ({ request }) => {
  draft = await createUnpublishedCast(request, operator, secret);
  await issueCastInvitation(request, operator, draft);
  linked = await createUnpublishedCast(request, operator, secret);
  const token = await issueCastInvitation(request, operator, linked);
  await acceptCastInvitation(
    request,
    token,
    `delete-person-${randomUUID()}@example.test`,
    password,
    "削除監査の本人",
  );
  foreign = await createUnpublishedCast(request, operator, secret, otherStore);

  const denied = await request.delete(`/api/casts/${draft}`, {
    headers: headers(limited),
  });
  expect(denied.status()).toBe(403);
  const hidden = await request.delete(`/api/casts/${foreign}`, {
    headers: headers(operator),
  });
  expect(hidden.status()).toBe(404);
  const refused = await request.delete(`/api/casts/${linked}`, {
    headers: headers(operator),
  });
  expect(refused.status()).toBe(409);

  const removed = await request.delete(`/api/casts/${draft}`, {
    headers: headers(operator),
  });
  expect(removed.status()).toBe(204);
  const gone = await request.get(`/api/casts/${draft}`, {
    headers: headers(operator),
  });
  expect(gone.status()).toBe(404);
});

Then(
  "削除の成功だけが削除前の状態と連鎖件数で監査される",
  async ({ request }) => {
    const events = (
      await listAuditEvents(request, auditor, "CAST_ENROLLMENT_DELETED")
    ).content.filter((event) =>
      [draft, linked, foreign].includes(event.target_id),
    );
    expect(events).toHaveLength(1);
    const event = events[0];
    expect(event.target_id).toBe(draft);
    expect(event.target_type).toBe("CAST_ENROLLMENT");
    expect(event.actor_id).toBe(operatorId);
    expect(event.actor_type).toBe("STAFF");
    expect(event.store_id).toBe(Number(STORE1_ID));
    const { before_values: before, after_values: after } = await getAuditEvent(
      request,
      auditor,
      event.id,
    );
    expect(before.exists).toBe("true");
    expect(before.status).toBe("ENROLLED");
    expect(before.publication_status).toBe("UNPUBLISHED");
    expect(Object.keys(after).sort()).toEqual([
      "deleted_invitations",
      "deleted_profiles",
      "deleted_snapshots",
      "deleted_status_histories",
      "exists",
    ]);
    expect(after.exists).toBe("false");
    expect(after.deleted_profiles).toBe("1");
    expect(after.deleted_invitations).toBe("1");
    expect(JSON.stringify([before, after])).not.toContain(secret);
  },
);
