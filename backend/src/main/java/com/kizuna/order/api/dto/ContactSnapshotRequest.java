package com.kizuna.order.api.dto;

import jakarta.validation.constraints.Size;

public record ContactSnapshotRequest(
    @Size(max = 255) String name,
    @Size(max = 50) String phoneNumber,
    @Size(max = 254) String email,
    @Size(max = 255) String lineId) {}
