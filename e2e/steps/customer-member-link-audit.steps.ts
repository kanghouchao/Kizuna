import { expect } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import { randomUUID } from "node:crypto";
import {
  ADMIN_PASSWORD,
  STORE1_ID,
  STORE_HEADERS,
  activateEmergencyElevation,
  revokeEmergencyElevation,
  createCustomer,
  createPermissionRole,
  createPlatformStaffFixture,
  createStoreStaffFixture,
  currentCustomerMemberLink,
  customerMemberLinkHistory,
  getAuditEvent,
  getAuthorizedStores,
  linkMemberToCustomer,
  listAuditEvents,
  listShifts,
  loginAsStoreAdmin,
  loginPlatformUser,
  registerMember,
  releaseCustomerMemberLink,
} from "./store-api";

const { Given, When, Then, After } = createBdd();
let limitedStaff = "";
let staff = "",
  auditor = "",
  elevated = "",
  secret = "",
  otherStore = "";
let auditorId = 0,
  elevationId = 0;
let cases: {
  customer: string;
  first: string;
  second: string;
  isElevated: boolean;
  codes: string[];
}[] = [];
const headers = (token: string) => ({
  ...STORE_HEADERS,
  Authorization: `Bearer ${token}`,
});
const keys = [
  "customer_id",
  "member_id",
  "status",
  "reason",
  "version",
  "linked_by",
  "linked_at",
  "released_by",
  "released_at",
];

Given(
  "関連監査専用の担当と昇格セッションが用意されている",
  async ({ request, $testInfo }) => {
    $testInfo.setTimeout(120000);
    cases = [];
    elevationId = 0;
    const suffix = randomUUID();
    secret = `関連監査へ複写しない私的入力-${suffix}`;
    staff = await loginAsStoreAdmin(request);
    const owner = await loginPlatformUser(
      request,
      "admin@kizuna.test",
      ADMIN_PASSWORD,
    );
    const password = randomUUID();
    const role = await createPermissionRole(
      request,
      owner,
      `関連監査-${suffix}`,
      ["AUDIT_VIEW", "EMERGENCY_ELEVATE", "PLATFORM_MENU_VIEW"],
    );
    const email = `link-auditor-${suffix}@example.test`;
    auditorId = await createPlatformStaffFixture(
      request,
      owner,
      email,
      password,
      [role],
    );
    auditor = await loginPlatformUser(request, email, password);
    const limitedRole = await createPermissionRole(
      request,
      owner,
      `関連参照-${suffix}`,
      ["SHIFT_MANAGE", "STORE_VIEW", "STORE_MENU_VIEW"],
    );
    const limitedEmail = `link-reader-${suffix}@example.test`;
    await createStoreStaffFixture(
      request,
      staff,
      limitedEmail,
      password,
      [limitedRole],
      [Number(STORE1_ID)],
    );
    limitedStaff = await loginPlatformUser(request, limitedEmail, password);
    const limitedStores = await getAuthorizedStores(request, limitedStaff);
    expect(
      limitedStores.map((store: { id: number }) => String(store.id)),
    ).toContain(STORE1_ID);

    const day = new Date().toISOString().slice(0, 10);
    await listShifts(request, limitedStaff, day, day);

    const session = await activateEmergencyElevation(
      request,
      auditor,
      STORE1_ID,
      "関連監査の検証",
      password,
    );
    elevated = session.token;
    elevationId = session.id;
    const stores = await getAuthorizedStores(request, staff);
    otherStore = String(
      stores.find((store: { id: number }) => String(store.id) !== STORE1_ID)!
        .id,
    );
  },
);

When(
  "通常と昇格の担当で関連建立と変更と解除を操作する",
  async ({ request }) => {
    for (const [token, isElevated] of [
      [staff, false],
      [elevated, true],
    ] as const) {
      const customer = await createCustomer(request, token, secret);
      const codes: string[] = [];
      for (let i = 0; i < 2; i++)
        codes.push(
          await registerMember(
            request,
            `link-${randomUUID()}@example.test`,
            randomUUID(),
            secret,
          ),
        );
      const path = `/api/store/customers/${customer}/member-link`;
      await linkMemberToCustomer(request, token, customer, codes[0]);
      const first = (await currentCustomerMemberLink(request, token, customer))
        .id;
      const change = {
        member_code: codes[1],
        expected_link_id: first,
        operation_reason: secret,
      };
      expect(
        (
          await request.post(path, {
            headers: headers(limitedStaff),
            data: change,
          })
        ).status(),
      ).toBe(403);
      expect(
        (
          await request.post(`${path}/releases`, {
            headers: headers(limitedStaff),
            data: { expected_link_id: first, operation_reason: secret },
          })
        ).status(),
      ).toBe(403);
      expect(
        (
          await request.post(path, {
            headers: headers(token),
            data: { ...change, member_code: codes[0] },
          })
        ).status(),
      ).toBe(409);
      expect(
        (
          await request.post(path, {
            headers: headers(token),
            data: { ...change, expected_link_id: "stale" },
          })
        ).status(),
      ).toBe(409);
      expect(
        (
          await request.post(path, {
            headers: headers(token),
            data: { ...change, operation_reason: "" },
          })
        ).status(),
      ).toBe(400);
      expect(
        (
          await request.post(path, {
            headers: { ...headers(token), "X-Store-ID": otherStore },
            data: change,
          })
        ).status(),
      ).toBe(isElevated ? 403 : 404);
      await linkMemberToCustomer(request, token, customer, codes[1], {
        expected_link_id: first,
        operation_reason: secret,
      });
      const second = (await currentCustomerMemberLink(request, token, customer))
        .id;
      expect(
        (
          await request.post(`${path}/releases`, {
            headers: headers(token),
            data: { expected_link_id: first, operation_reason: secret },
          })
        ).status(),
      ).toBe(409);
      expect(
        (
          await request.post(`${path}/releases`, {
            headers: { ...headers(token), "X-Store-ID": otherStore },
            data: { expected_link_id: second, operation_reason: secret },
          })
        ).status(),
      ).toBe(isElevated ? 403 : 404);
      await releaseCustomerMemberLink(request, token, customer, second, secret);
      expect(
        (
          await request.post(`${path}/releases`, {
            headers: headers(token),
            data: { expected_link_id: second, operation_reason: secret },
          })
        ).status(),
      ).toBe(409);
      const history = await customerMemberLinkHistory(request, token, customer);
      expect(history.content).toHaveLength(2);
      expect(
        history.content.map((row: { id: string }) => row.id).sort(),
      ).toEqual([first, second].sort());
      for (const row of history.content) {
        expect(row.status).toBe("RELEASED");
        expect(row.release_reason).toBe(secret);
      }
      cases.push({ customer, first, second, isElevated, codes });
    }
  },
);

Then(
  "関連の成功した区間だけが安全な前後値と主体で監査される",
  async ({ request }) => {
    for (const action of [
      "CUSTOMER_MEMBER_LINK_CREATED",
      "CUSTOMER_MEMBER_LINK_RELEASED",
    ]) {
      const events = (await listAuditEvents(request, auditor, action)).content;
      for (const item of cases) {
        const matches = events.filter(
          (event) => event.source_id === item.customer,
        );
        expect(matches).toHaveLength(2);
        expect(matches.map((event) => event.target_id).sort()).toEqual(
          [item.first, item.second].sort(),
        );
        for (const event of matches) {
          expect(event.target_type).toBe("CUSTOMER_MEMBER_LINK");
          expect(event.source_type).toBe("CUSTOMER");
          expect(event.actor_type).toBe("STAFF");
          expect(event.actor_id === auditorId).toBe(item.isElevated);
          expect(event.store_id).toBe(Number(STORE1_ID));
          expect(event.result).toBe("SUCCEEDED");
          const detail = await getAuditEvent(request, auditor, event.id);
          const after = detail.after_values;
          expect(Object.keys(after).sort()).toEqual(
            [
              ...keys,
              ...(item.isElevated ? ["emergency_elevation_id"] : []),
            ].sort(),
          );
          expect(after.customer_id).toBe(item.customer);
          expect(after.reason).toBe("MEMBER_CODE");
          expect(after.member_id).toMatch(/^\d+$/);
          expect(after.linked_by).toBe(String(event.actor_id));
          expect(after.linked_at).toBeTruthy();
          expect(after.emergency_elevation_id).toBe(
            item.isElevated ? String(elevationId) : undefined,
          );
          if (action.endsWith("CREATED")) {
            expect(detail.before_values).toEqual({});
            expect(after.status).toBe("ACTIVE");
            expect(after.version).toBe("0");
            expect(after.released_by).toBe("");
            expect(after.released_at).toBe("");
          } else {
            expect(Object.keys(detail.before_values).sort()).toEqual(
              [...keys].sort(),
            );
            expect(detail.before_values.status).toBe("ACTIVE");
            expect(detail.before_values.version).toBe("0");
            expect(after.status).toBe("RELEASED");
            expect(after.version).toBe("1");
            expect(after.released_by).toBe(String(event.actor_id));
            expect(after.released_at).toBeTruthy();
            for (const key of [
              "customer_id",
              "member_id",
              "reason",
              "linked_by",
              "linked_at",
            ])
              expect(after[key]).toBe(detail.before_values[key]);
          }
          for (const value of [secret, ...item.codes, elevated])
            expect(JSON.stringify(detail)).not.toContain(value);
        }
      }
    }
  },
);

After({ tags: "@link-audit" }, async ({ request }) => {
  if (elevationId) {
    await revokeEmergencyElevation(request, auditor, elevationId);
    elevationId = 0;
  }
});
