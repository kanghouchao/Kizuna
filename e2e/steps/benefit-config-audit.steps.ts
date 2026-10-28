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

const { Given, When, Then } = createBdd();
let operator = "",
  limited = "",
  auditor = "",
  operatorId = 0,
  ruleId = 0,
  secret = "",
  host = "",
  ruleName = "";

const bearer = (token: string) => ({ Authorization: `Bearer ${token}` });
const rulePayload = (points: number, name: string, version?: number) => ({
  name,
  type: "VISIT",
  store_scope_type: "ALL_STORES",
  store_ids: [],
  repeat_policy: "EVERY_TIME",
  points,
  ...(version === undefined ? {} : { version }),
});

Given(
  "設定監査専用の担当と監査者が用意されている",
  async ({ request, $testInfo }) => {
    $testInfo.setTimeout(120000);
    const suffix = randomUUID();
    const password = randomUUID();
    secret = `設定監査へ複写しない秘密-${suffix}`;
    host = `smtp-${suffix}.example.test`;
    ruleName = `設定監査の規則名-${suffix}`;
    const owner = await loginUser(
      request,
      "admin@kizuna.test",
      ADMIN_PASSWORD,
    );
    const operatorRole = await createPermissionRole(
      request,
      owner,
      `設定担当-${suffix}`,
      ["BENEFIT_MANAGE", "SYSTEM_CONFIG_MANAGE"],
    );
    const operatorEmail = `config-operator-${suffix}@example.test`;
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
      `設定権限なし-${suffix}`,
      ["SHIFT_MANAGE"],
    );
    const limitedEmail = `config-limited-${suffix}@example.test`;
    await createStaffFixture(request, owner, limitedEmail, password, [
      limitedRole,
    ]);
    limited = await loginUser(request, limitedEmail, password);
    const auditRole = await createPermissionRole(
      request,
      owner,
      `設定監査-${suffix}`,
      ["AUDIT_VIEW"],
    );
    const auditEmail = `config-auditor-${suffix}@example.test`;
    await createStaffFixture(request, owner, auditEmail, password, [auditRole]);
    auditor = await loginUser(request, auditEmail, password);
  },
);

When(
  "担当が特典ルールを作成・更新・停止しシステム設定も更新して拒否される操作も試す",
  async ({ request }) => {
    const denied = await request.post("/api/benefit-rules", {
      headers: bearer(limited),
      data: rulePayload(500, ruleName),
    });
    expect(denied.status()).toBe(403);
    const created = await request.post("/api/benefit-rules", {
      headers: bearer(operator),
      data: rulePayload(500, ruleName),
    });
    expect(created.status(), await created.text()).toBe(201);
    ruleId = (await created.json()).id;

    const same = await request.put(`/api/benefit-rules/${ruleId}`, {
      headers: bearer(operator),
      data: rulePayload(500, ruleName, 0),
    });
    expect(same.status(), await same.text()).toBe(200);
    const stale = await request.put(`/api/benefit-rules/${ruleId}`, {
      headers: bearer(operator),
      data: rulePayload(900, ruleName, 7),
    });
    expect(stale.status()).toBe(409);
    const updated = await request.put(`/api/benefit-rules/${ruleId}`, {
      headers: bearer(operator),
      data: rulePayload(800, ruleName, 0),
    });
    expect(updated.status(), await updated.text()).toBe(200);
    const stopped = await request.post(
      `/api/benefit-rules/${ruleId}/deactivation`,
      { headers: bearer(operator), data: { version: 1 } },
    );
    expect(stopped.status(), await stopped.text()).toBeLessThan(300);

    const configDenied = await request.put("/api/system-configs/smtp_host", {
      headers: bearer(limited),
      data: { config_value: host },
    });
    expect(configDenied.status()).toBe(403);
    const invalid = await request.put("/api/system-configs/smtp_port", {
      headers: bearer(operator),
      data: { config_value: secret },
    });
    expect(invalid.status()).toBe(400);
    const missing = await request.put("/api/system-configs/no_such_key", {
      headers: bearer(operator),
      data: { config_value: secret },
    });
    expect(missing.status()).toBe(404);
    for (const [key, value] of [
      ["smtp_host", host],
      ["smtp_host", host],
      ["smtp_password", secret],
    ]) {
      const response = await request.put(`/api/system-configs/${key}`, {
        headers: bearer(operator),
        data: { config_value: value },
      });
      expect(response.status(), await response.text()).toBe(200);
    }
  },
);

Then(
  "成功だけが店舗なしの記録で監査され秘密の値は複写されない",
  async ({ request }) => {
    const mine = async (action: string, target: string) =>
      (await listAuditEvents(request, auditor, action)).content.filter(
        (event) => event.target_id === target,
      );
    const dump: unknown[] = [];
    for (const action of [
      "BENEFIT_RULE_CREATED",
      "BENEFIT_RULE_UPDATED",
      "BENEFIT_RULE_DEACTIVATED",
    ]) {
      const events = await mine(action, String(ruleId));
      expect(events, action).toHaveLength(1);
      expect(events[0].store_id).toBeNull();
      expect(events[0].target_type).toBe("BENEFIT_RULE");
      expect(events[0].actor_id).toBe(operatorId);
      dump.push(await getAuditEvent(request, auditor, events[0].id));
    }
    const updated = dump[1] as {
      before_values: Record<string, string>;
      after_values: Record<string, string>;
    };
    expect(updated.before_values.points).toBe("500");
    expect(updated.after_values.points).toBe("800");
    expect(updated.after_values.changed_fields).toBe("points");

    const hostEvents = await mine("SYSTEM_CONFIG_UPDATED", "smtp_host");
    const mineHost = hostEvents.filter((event) => event.actor_id === operatorId);
    expect(mineHost).toHaveLength(1);
    expect(mineHost[0].store_id).toBeNull();
    const hostDetail = await getAuditEvent(request, auditor, mineHost[0].id);
    expect(hostDetail.after_values.config_value).toBe(host);
    dump.push(hostDetail);
    const secretEvents = (
      await mine("SYSTEM_CONFIG_UPDATED", "smtp_password")
    ).filter((event) => event.actor_id === operatorId);
    expect(secretEvents).toHaveLength(1);
    expect(secretEvents[0].store_id).toBeNull();
    const secretDetail = await getAuditEvent(
      request,
      auditor,
      secretEvents[0].id,
    );
    expect(secretDetail.before_values).toEqual({});
    expect(secretDetail.after_values).toEqual({ changed: "true" });
    dump.push(secretDetail);
    expect(JSON.stringify(dump)).not.toContain(secret);
    expect(JSON.stringify(dump)).not.toContain(ruleName);
  },
);
