package com.kizuna.user.api.dto;

import java.util.List;

/** 能力束の応答（授与 UI の選択肢）。capabilities は能力 enum 名の昇順。 */
public record CapabilityBundleResponse(Long id, String name, List<String> capabilities) {}
