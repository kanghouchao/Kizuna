package com.kizuna.user.api.dto;

/** 店長に任命できる既存アカウントの 1 件。母集団は有効なアカウントに限るので状態は返さない。 */
public record StoreManagerCandidateResponse(Long id, String email, String displayName) {}
