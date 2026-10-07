package com.kizuna.audit.recording;

public record AuditActor(Long id, String type, String name) {
  public AuditActor {
    if (id == null
        || id <= 0
        || type == null
        || type.isBlank()
        || type.length() > 20
        || name == null
        || name.isBlank()
        || name.length() > 255) {
      throw new IllegalArgumentException("監査主体の指定が不正です");
    }
  }
}
