import { expect } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { randomUUID } from "node:crypto";
import {
  ADMIN_PASSWORD,
  STORE1_ID,
  STORE_HEADERS,
  acceptCastInvitation,
  acceptExistingCastInvitation,
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
  personEmail = "",
  password = "";
let first = { enrollment: "", token: "" };
let second = { enrollment: "", token: "" };

Given(
  "招待監査専用の担当と監査者が用意されている",
  async ({ request, $testInfo }) => {
    $testInfo.setTimeout(120000);
    const suffix = randomUUID();
    password = randomUUID();
    personEmail = `invited-${suffix}@example.test`;
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
      `招待担当-${suffix}`,
      ["CAST_MANAGE", "CAST_INVITE", "STORE_VIEW"],
    );
    const operatorEmail = `invite-operator-${suffix}@example.test`;
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
      `在籍のみ-${suffix}`,
      ["CAST_MANAGE", "STORE_VIEW"],
    );
    const limitedEmail = `invite-limited-${suffix}@example.test`;
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
      `招待監査-${suffix}`,
      ["AUDIT_VIEW"],
    );
    const auditEmail = `invite-auditor-${suffix}@example.test`;
    await createStaffFixture(request, owner, auditEmail, password, [auditRole]);
    auditor = await loginUser(request, auditEmail, password);
  },
);

When(
  "担当が招待を発行し新規受諾と既存受諾を行い拒否操作も試す",
  async ({ request }) => {
    first.enrollment = await createUnpublishedCast(
      request,
      operator,
      "招待監査-1",
    );
    second.enrollment = await createUnpublishedCast(
      request,
      operator,
      "招待監査-2",
      otherStore,
    );
    const denied = await request.post(
      `/api/casts/${first.enrollment}/invitation`,
      { headers: { ...STORE_HEADERS, Authorization: `Bearer ${limited}` } },
    );
    expect(denied.status()).toBe(403);
    const hidden = await request.post(
      `/api/casts/${second.enrollment}/invitation`,
      { headers: { ...STORE_HEADERS, Authorization: `Bearer ${operator}` } },
    );
    expect(hidden.status()).toBe(404);

    first.token = await issueCastInvitation(request, operator, first.enrollment);
    const unknown = await request.post("/api/cast-invitations/acceptance", {
      data: {
        token: randomUUID(),
        email: personEmail,
        password,
        display_name: "招待された本人",
      },
    });
    expect(unknown.status()).toBe(404);
    await acceptCastInvitation(
      request,
      first.token,
      personEmail,
      password,
      "招待された本人",
    );
    const replay = await request.post("/api/cast-invitations/acceptance", {
      data: {
        token: first.token,
        email: `again-${personEmail}`,
        password,
        display_name: "招待された本人",
      },
    });
    expect(replay.status()).toBe(400);

    second.token = await issueCastInvitation(
      request,
      operator,
      second.enrollment,
      otherStore,
    );
    const person = await loginUser(request, personEmail, password);
    await acceptExistingCastInvitation(request, person, second.token);
  },
);

Then(
  "招待の成功操作だけが安全な前後値と参照で監査される",
  async ({ request }) => {
    const find = async (action: string, enrollment: string) => {
      const events = (await listAuditEvents(request, auditor, action)).content
        .filter((event) => event.source_id === enrollment);
      expect(events).toHaveLength(1);
      return { event: events[0], detail: await getAuditEvent(request, auditor, events[0].id) };
    };
    const secrets = [first.token, second.token, personEmail, password, "招待された本人"];

    for (const [item, storeId] of [
      [first, STORE1_ID],
      [second, otherStore],
    ] as const) {
      const issued = await find("CAST_INVITATION_ISSUED", item.enrollment);
      expect(issued.event.actor_id).toBe(operatorId);
      expect(issued.event.actor_type).toBe("STAFF");
      expect(issued.event.store_id).toBe(Number(storeId));
      expect(issued.event.target_type).toBe("CAST_INVITATION");
      expect(issued.event.source_type).toBe("CAST_ENROLLMENT");
      expect(Object.keys(issued.detail.before_values)).toEqual([]);
      expect(Object.keys(issued.detail.after_values).sort()).toEqual([
        "expires_at",
        "status",
        "version",
      ]);
      expect(issued.detail.after_values.status).toBe("PENDING");
      expect(issued.detail.after_values.version).toBe("0");

      const accepted = await find("CAST_INVITATION_ACCEPTED", item.enrollment);
      expect(accepted.event.actor_type).toBe("CAST");
      expect(accepted.event.store_id).toBe(Number(storeId));
      expect(accepted.event.target_id).toBe(issued.event.target_id);
      const { before_values: before, after_values: after } = accepted.detail;
      expect(before).toEqual({ status: "PENDING", version: "0" });
      expect(after.status).toBe("ACCEPTED");
      expect(after.cast_enrollment_id).toBe(item.enrollment);
      expect(after.user_id).toBe(String(accepted.event.actor_id));
      expect(after.store_id).toBe(storeId);
      if (item === first) {
        expect(after.acceptance).toBe("NEW_USER");
        expect(Object.keys(after)).not.toContain("store_membership_added");
      } else {
        expect(after.acceptance).toBe("EXISTING_USER");
        expect(after.store_membership_added).toBe("true");
      }
      for (const detail of [issued.detail, accepted.detail]) {
        for (const secret of secrets) {
          expect(
            JSON.stringify([detail.before_values, detail.after_values]),
          ).not.toContain(secret);
        }
      }
    }
    const actors = (
      await Promise.all(
        [first, second].map((item) =>
          find("CAST_INVITATION_ACCEPTED", item.enrollment),
        ),
      )
    ).map((row) => row.event.actor_id);
    expect(actors[0]).toBe(actors[1]);
  },
);
