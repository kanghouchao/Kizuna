package com.kizuna.audit.recording;

import java.util.Map;

public record AuditEventResponse(
    AuditEventSummary event, Map<String, String> beforeValues, Map<String, String> afterValues) {}
