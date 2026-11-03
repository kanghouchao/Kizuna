package com.kizuna.cast.application;

import com.kizuna.cast.domain.CastProfilePatch;
import java.util.Map;

/** キャストの更新入力。customFields の null は「カスタムフィールドを変更しない」。 */
public record CastUpdateCommand(CastProfilePatch profile, Map<String, String> customFields) {}
