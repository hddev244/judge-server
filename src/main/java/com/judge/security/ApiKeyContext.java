package com.judge.security;

import com.judge.domain.ApiKey;

public class ApiKeyContext {
    private static final ThreadLocal<ApiKey> holder = new ThreadLocal<>();

    public static void set(ApiKey key) { holder.set(key); }
    public static ApiKey get() { return holder.get(); }
    public static void clear() { holder.remove(); }
}
