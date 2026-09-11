package com.kizuna.user.application;

import org.springframework.modulith.NamedInterface;

@NamedInterface("receptionist-eligibility")
public record ReceptionistCandidate(Long id, String displayName) {}
