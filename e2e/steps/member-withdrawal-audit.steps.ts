import { expect } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { randomUUID } from "node:crypto";
import {
  ADMIN_PASSWORD,
  adjustCustomerPoints,
  createCustomer,
  createPermissionRole,
  createStaffFixture,
  getAuditEvent,
  linkMemberToCustomer,
  listAuditEvents,
  loginAsStoreAdmin,
  loginUser,
  memberIdOf,
  registerMember,
} from "./store-api";

const { Given, When, Then } = createBdd();

const POINTS = 120;
let auditor = "";
let customers: string[] = [];
let secret = "";
let selfMember = { id: "", email: "", password: "" };
let staffMember = { id: "", email: "" };

async function registerLinkedMember(
  request: Parameters<typeof registerMember>[0],
  token: string,
  email: string,
  password: string,
) {
  const code = await registerMember(request, email, password, `退会監査会員-${randomUUID()}`);
  const customer = await createCustomer(request, token, secret);
  await linkMemberToCustomer(request, token, customer, code);
  await adjustCustomerPoints(request, token, customer, POINTS, "退会監査の付与");
  return customer;
}

Given("退会監査専用の監査者が用意されている", async ({ request }) => {
  const suffix = randomUUID();
  secret = `退会監査へ複写しない私的入力-${suffix}`;
  const owner = await loginUser(request, "admin@kizuna.test", ADMIN_PASSWORD);
  const role = await createPermissionRole(
    request,
    owner,
    `退会監査-${suffix}`,
    ["AUDIT_VIEW"],
  );
  const email = `withdrawal-auditor-${suffix}@example.test`;
  const password = randomUUID();
  await createStaffFixture(request, owner, email, password, [role]);
  auditor = await loginUser(request, email, password);
});

When(
  "会員を新規登録してポイントと関連を用意し、本人退会と職員代行退会を行う",
  async ({ request }) => {
    const staff = await loginAsStoreAdmin(request);
    const owner = await loginUser(request, "admin@kizuna.test", ADMIN_PASSWORD);
    const suffix = randomUUID();
    selfMember = {
      id: "",
      email: `withdrawal-self-${suffix}@example.test`,
      password: randomUUID(),
    };
    staffMember = { id: "", email: `withdrawal-staff-${suffix}@example.test` };
    customers = [
      await registerLinkedMember(
        request,
        staff,
        selfMember.email,
        selfMember.password,
      ),
      await registerLinkedMember(request, staff, staffMember.email, randomUUID()),
    ];
    selfMember.id = await memberIdOf(request, owner, selfMember.email);
    staffMember.id = await memberIdOf(request, owner, staffMember.email);

    const memberToken = await loginUser(
      request,
      selfMember.email,
      selfMember.password,
    );
    const self = await request.post("/api/me/member/withdrawal", {
      headers: { Authorization: `Bearer ${memberToken}` },
      data: { reason: secret },
    });
    expect(self.status(), await self.text()).toBe(204);

    const denied = await request.post(
      `/api/members/${staffMember.id}/withdrawal`,
      {
        headers: { Authorization: `Bearer ${staff}` },
        data: { reason: secret },
      },
    );
    expect(denied.status(), "MEMBER_WITHDRAW を持たない店長は 403").toBe(403);

    const byStaff = await request.post(
      `/api/members/${staffMember.id}/withdrawal`,
      {
        headers: { Authorization: `Bearer ${owner}` },
        data: { reason: secret },
      },
    );
    expect(byStaff.status(), await byStaff.text()).toBe(204);

    const again = await request.post(
      `/api/members/${staffMember.id}/withdrawal`,
      {
        headers: { Authorization: `Bearer ${owner}` },
        data: { reason: secret },
      },
    );
    expect(again.status()).toBe(409);
    const unknown = await request.post("/api/members/999999999/withdrawal", {
      headers: { Authorization: `Bearer ${owner}` },
      data: { reason: secret },
    });
    expect(unknown.status()).toBe(404);
  },
);

Then(
  "退会ごとに1行だけが主体と消去仕訳を伴い理由の原文なしで監査に残る",
  async ({ request }) => {
    const events = (await listAuditEvents(request, auditor, "MEMBER_WITHDRAWN"))
      .content;
    for (const [member, actorType] of [
      [selfMember, "MEMBER"],
      [staffMember, "STAFF"],
    ] as const) {
      const matches = events.filter((event) => event.target_id === member.id);
      expect(matches).toHaveLength(1);
      const event = matches[0];
      expect(event.target_type).toBe("MEMBER");
      expect(event.actor_type).toBe(actorType);
      expect(event.store_id ?? null).toBeNull();
      expect(event.result).toBe("SUCCEEDED");
      expect(event.source_type).toBe("POINT_LEDGER_ENTRY");
      expect(event.source_id).toMatch(/^\d+$/);
      const detail = await getAuditEvent(request, auditor, event.id);
      expect(detail.before_values.status).toBe("ACTIVE");
      expect(detail.before_values.user_enabled).toBe("true");
      expect(detail.after_values.status).toBe("WITHDRAWN");
      expect(detail.after_values.user_enabled).toBe("false");
      expect(detail.after_values.cleared_points).toBe(String(POINTS));
      expect(detail.after_values.withdrawn_applications).toBe("0");
      expect(Number(detail.after_values.version)).toBeGreaterThan(
        Number(detail.before_values.version),
      );
      for (const value of [secret, member.email])
        expect(JSON.stringify(detail)).not.toContain(value);
    }
    const released = (
      await listAuditEvents(request, auditor, "CUSTOMER_MEMBER_LINK_RELEASED")
    ).content;
    for (const customer of customers)
      expect(
        released.filter((event) => event.source_id === customer),
        "関連解除の既存監査は退会ごとに1行のまま",
      ).toHaveLength(1);
  },
);
