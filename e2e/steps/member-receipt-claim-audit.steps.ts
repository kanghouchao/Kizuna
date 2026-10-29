import { expect } from "@playwright/test";
import { createBdd } from "playwright-bdd";
import {
  createCast,
  createCourse,
  createCustomer,
  createPermissionRole,
  createStaffFixture,
  getAuditEvent,
  getOrder,
  getOrderReceptionists,
  listAuditEvents,
  loginAsStoreAdmin,
  loginUser,
  registerMember,
  submitConfirmedStoreRequest,
  STORE1_ID,
  STORE_HEADERS,
} from "./store-api";

const { Given, When, Then } = createBdd();
let auditor = "";
let orderId = "";
let memberEmail = "";
let memberName = "";
let secrets: string[] = [];
const bearer = (token: string) => ({ Authorization: `Bearer ${token}` });
const today = () =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Tokyo" }).format(
    new Date(),
  );

Given(
  "伝票申領監査専用の監査者が用意されている",
  async ({ request, $testInfo }) => {
    $testInfo.setTimeout(120000);
    secrets = [];
    const suffix = Date.now().toString();
    const password = "Claim-" + suffix + "-fixture";
    const owner = await loginUser(request, "admin@kizuna.test", "pass1234");
    const role = await createPermissionRole(request, owner, "申領監査-" + suffix, [
      "AUDIT_VIEW",
    ]);
    const email = "claim-auditor-" + suffix + "@example.test";
    await createStaffFixture(request, owner, email, password, [role]);
    auditor = await loginUser(request, email, password);
    secrets.push(password);
  },
);

When(
  "完了した受注の伝票を新規登録した会員が申領する",
  async ({ request }) => {
    const suffix = Date.now().toString();
    const staff = await loginAsStoreAdmin(request);
    const storeHeaders = { ...STORE_HEADERS, ...bearer(staff) };
    const castId = await createCast(request, staff, "申領対象-" + suffix);
    const courseId = await createCourse(request, staff, "申領コース-" + suffix);
    const receptionistId = (await getOrderReceptionists(request, staff))[0].id;
    const customerId = await createCustomer(request, staff, "申領顧客-" + suffix);
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
    const receiptToken = (await completed.json()).receipt_token as string;
    expect(receiptToken).toBeTruthy();
    secrets.push(receiptToken);

    memberEmail = "claim-member-" + suffix + "@example.test";
    memberName = "申領会員-" + suffix;
    const password = crypto.randomUUID();
    await registerMember(request, memberEmail, password, memberName);
    secrets.push(password);
    const member = await loginUser(request, memberEmail, password);

    const claimed = await request.post("/api/me/receipts", {
      headers: bearer(member),
      data: { token: receiptToken },
    });
    expect(claimed.status(), await claimed.text()).toBe(201);
    const again = await request.post("/api/me/receipts", {
      headers: bearer(member),
      data: { token: receiptToken },
    });
    expect(again.status()).toBe(404);
  },
);

Then(
  "申領の成功だけが会員本人の主体と帰属記録を伴い秘密なしで監査に残る",
  async ({ request }) => {
    const listing = await listAuditEvents(request, auditor, "RECEIPT_CLAIMED");
    const events = listing.content.filter(
      (e: { target_id: string }) => e.target_id === orderId,
    );
    // 二度目の申領は拒否されるため、成功監査は 1 件だけ。
    expect(events).toHaveLength(1);
    expect(events[0].actor_type).toBe("MEMBER");
    expect(events[0].store_id).toBe(Number(STORE1_ID));
    const detail = await getAuditEvent(request, auditor, events[0].id);
    expect(detail.event.source_type).toBe("ORDER_ATTRIBUTION");
    expect(Number(detail.event.source_id)).toBeGreaterThan(0);
    expect(detail.before_values.exists).toBe("false");
    expect(detail.after_values.exists).toBe("true");
    expect(detail.after_values.status).toBe("ACTIVE");
    expect(detail.after_values.rank_changed).toMatch(/^(true|false)$/);
    const serialized = JSON.stringify(detail);
    for (const secret of [...secrets, memberEmail, memberName]) {
      expect(serialized).not.toContain(secret);
    }
  },
);
