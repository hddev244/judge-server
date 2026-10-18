package com.judge.domain;

import com.judge.api.dto.ApiKeyRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyRoleTest {

    private static void prePersist(ApiKey key) throws Exception {
        Method m = ApiKey.class.getDeclaredMethod("prePersist");
        m.setAccessible(true);
        m.invoke(key);
    }

    @Test
    void graderIsNeverAdmin() throws Exception {
        ApiKey key = ApiKey.builder().role(ApiKeyRole.GRADER).isAdmin(true).build();
        prePersist(key);
        assertFalse(key.isAdmin());
        assertTrue(key.isGrader());
    }

    @Test
    void legacyIsAdminBuilderStillProducesAdmin() throws Exception {
        ApiKey key = ApiKey.builder().isAdmin(true).build();
        prePersist(key);
        assertEquals(ApiKeyRole.ADMIN, key.getRole());
        assertTrue(key.isAdmin());
    }

    @Test
    void requestRoleWinsOverLegacyFlag() {
        ApiKeyRequest req = new ApiKeyRequest();
        req.setAdmin(true);
        req.setRole(ApiKeyRole.GRADER);
        assertEquals(ApiKeyRole.GRADER, req.resolvedRole());

        ApiKeyRequest legacy = new ApiKeyRequest();
        legacy.setAdmin(true);
        assertEquals(ApiKeyRole.ADMIN, legacy.resolvedRole());
        assertEquals(ApiKeyRole.CLIENT, new ApiKeyRequest().resolvedRole());
    }
}
