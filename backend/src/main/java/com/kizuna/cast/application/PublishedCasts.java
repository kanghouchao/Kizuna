package com.kizuna.cast.application;

import com.kizuna.cast.domain.CastFieldDefinition;
import com.kizuna.cast.domain.CastProfile;
import java.util.List;

/** 公開中のプロフィールと、その店舗の公開カスタムフィールド定義（表示順）。 */
public record PublishedCasts(
    List<CastProfile> profiles, List<CastFieldDefinition> publicDefinitions) {}
