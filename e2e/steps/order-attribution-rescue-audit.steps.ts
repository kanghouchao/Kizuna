import { expect } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import {
  adjustCustomerPoints,
  createCast,
  createCourse,
  createCustomer,
  createPermissionRole,
  createStaffFixture,
  getAuditEvent,
  getOrder,
  getOrderReceptionists,
  linkMemberToCustomer,
  listAuditEvents,
  loginAsStoreAdmin,
  loginUser,
  registerMember,
  submitConfirmedStoreRequest,
  STORE1_ID,
  STORE_HEADERS,
} from "./store-api";

const { Given, When, Then } = createBdd();
let staff = "";
let auditor = "";
let orderId = "";
let attributionId = 0;
let corrections = 0;
let secrets: string[] = [];
const bearer = (token: string) => ({ Authorization: `Bearer ${token}` });
const storeHeaders = (token: string) => ({ ...STORE_HEADERS, ...bearer(token) });
const today = () =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Tokyo" }).format(
    new Date(),
  );

Given(
  "帰属救済監査専用の担当と監査者が用意されている",
  async ({ request, $testInfo }) => {
    $testInfo.setTimeout(120000);
    secrets = [];
    corrections = 0;
    const suffix = Date.now().toString();
    const password = "Rescue-" + suffix + "-fixture";
    staff = await loginAsStoreAdmin(request);
    const owner = await loginUser(request, "admin@kizuna.test", "pass1234");
    const role = await createPermissionRole(request, owner, "救済監査-" + suffix, [
      "AUDIT_VIEW",
    ]);
    const email = "rescue-auditor-" + suffix + "@example.test";
    await createStaffFixture(request, owner, email, password, [role]);
    auditor = await loginUser(request, email, password);
    secrets.push(password);
  },
);

When(
  "会員に帰属した完了受注で救済4入口を順に操作する",
  async ({ request }) => {
    const suffix = Date.now().toString();
    const reason = "救済監査へ転記しない理由-" + suffix;
    secrets.push(reason);
    const castId = await createCast(request, staff, "救済対象-" + suffix);
    const courseId = await createCourse(request, staff, "救済コース-" + suffix);
    const receptionistId = (await getOrderReceptionists(request, staff))[0].id;
    const memberCode = await registerMember(
      request,
      "rescue-member-" + suffix + "@example.test",
      crypto.randomUUID(),
      "救済会員-" + suffix,
    );
    const customerId = await createCustomer(request, staff, "救済顧客-" + suffix);
    await linkMemberToCustomer(request, staff, customerId, memberCode);
    await adjustCustomerPoints(request, staff, customerId, 1000, "原資", STORE1_ID);
    const created = await (
      await submitConfirmedStoreRequest(
        request,
        staff,
        "/api/orders/preview",
        "/api/orders",
        {
          cast_id: castId,
          course_id: courseId,
          receptionist_id: receptionistId,
          customer_selection: { mode: "EXISTING", customer_id: customerId },
          business_date: today(),
          pax: 1,
        },
      )
    ).json();
    orderId = created.id;
    const order = await getOrder(request, staff, STORE1_ID, orderId);
    const completed = await submitConfirmedStoreRequest(
      request,
      staff,
      `/api/orders/${orderId}/completion-preview`,
      `/api/orders/${orderId}/completion`,
      { expected_version: order.version, fee_lines: [] },
    );
    expect(completed.status()).toBe(200);

    const current = await request.get(`/api/orders/${orderId}/attribution`, {
      headers: storeHeaders(staff),
    });
    expect(current.status()).toBe(200);
    attributionId = (await current.json()).id;
    expect(attributionId).toBeGreaterThan(0);

    const invalidation = await request.post(
      `/api/orders/${orderId}/attribution/invalidation`,
      {
        headers: storeHeaders(staff),
        data: { attribution_id: attributionId, reason },
      },
    );
    expect(invalidation.status(), await invalidation.text()).toBe(200);

    const progress = await request.get(
      `/api/orders/${orderId}/attribution/correction`,
      { headers: storeHeaders(staff), params: { attribution_id: attributionId } },
    );
    expect(progress.status(), await progress.text()).toBe(200);
    const granted = (await progress.json()).granted_points as number;
    if (granted > 0) {
      const key = crypto.randomUUID();
      const body = { attribution_id: attributionId, points: 1, reason, idempotency_key: key };
      const first = await request.post(
        `/api/orders/${orderId}/attribution/correction`,
        { headers: storeHeaders(staff), data: body },
      );
      expect(first.status(), await first.text()).toBe(200);
      const replay = await request.post(
        `/api/orders/${orderId}/attribution/correction`,
        { headers: storeHeaders(staff), data: body },
      );
      expect(replay.status(), await replay.text()).toBe(200);
      corrections = 1;
    }

    const denied = await request.post(`/api/orders/${orderId}/receipt-token`, {
      headers: storeHeaders(auditor),
    });
    expect(denied.status()).toBe(403);
    const reissued = await request.post(`/api/orders/${orderId}/receipt-token`, {
      headers: storeHeaders(staff),
    });
    expect(reissued.status(), await reissued.text()).toBe(201);
    secrets.push((await reissued.json()).receipt_token);

    const latest = await getOrder(request, staff, STORE1_ID, orderId);
    const rollback = await request.post(`/api/orders/${orderId}/point-rollback`, {
      headers: storeHeaders(staff),
      data: { expected_total_fee: latest.total_fee, expected_offset_amount: 0, reason },
    });
    expect(rollback.status(), await rollback.text()).toBe(201);
    const again = await request.post(`/api/orders/${orderId}/point-rollback`, {
      headers: storeHeaders(staff),
      data: { expected_total_fee: latest.total_fee, expected_offset_amount: 0, reason },
    });
    expect(again.status()).toBe(409);
  },
);

Then(
  "成功した救済だけが主体と実versionを伴い秘密なしで監査に残る",
  async ({ request }) => {
    const expectations = [
      { action: "ORDER_ATTRIBUTION_INVALIDATED", target: String(attributionId), count: 1 },
      { action: "ORDER_RECEIPT_TOKEN_REISSUED", target: orderId, count: 1 },
      { action: "ORDER_POINTS_ROLLED_BACK", target: orderId, count: 1 },
    ];
    for (const item of expectations) {
      const listing = await listAuditEvents(request, auditor, item.action);
      const events = listing.content.filter(
        (e: { target_id: string }) => e.target_id === item.target,
      );
      expect(events, item.action).toHaveLength(item.count);
      expect(events[0].actor_type).toBe("STAFF");
      expect(events[0].store_id).toBe(Number(STORE1_ID));
      const detail = await getAuditEvent(request, auditor, events[0].id);
      const serialized = JSON.stringify(detail);
      for (const secret of secrets) expect(serialized).not.toContain(secret);
      if (item.action === "ORDER_ATTRIBUTION_INVALIDATED") {
        expect(detail.before_values.status).toBe("ACTIVE");
        expect(detail.after_values.status).toBe("INVALIDATED");
        expect(Number(detail.after_values.version)).toBeGreaterThan(
          Number(detail.before_values.version),
        );
      }
      if (item.action === "ORDER_RECEIPT_TOKEN_REISSUED") {
        expect(detail.after_values.token_status).toBe("ISSUED");
      }
      if (item.action === "ORDER_POINTS_ROLLED_BACK") {
        expect(detail.after_values.rolled_back).toBe("true");
      }
    }
    const corrected = (
      await listAuditEvents(request, auditor, "ORDER_ATTRIBUTION_CORRECTED")
    ).content.filter(
      (e: { target_id: string }) => e.target_id === String(attributionId),
    );
    // 再送は台帳が収束させるため、訂正の監査は初回の 1 件だけ。
    expect(corrected).toHaveLength(corrections);
  },
);
