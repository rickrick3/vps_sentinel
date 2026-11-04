package com.expertiz.sentinel.core.identity.internal.security;

/** Access token signé et sa durée de validité, renvoyée au client ({@code expiresIn}). */
public record IssuedAccessToken(String value, long expiresInSeconds) {}
