package com.judge.api;

import com.judge.config.JudgeConfig;
import com.judge.domain.ApiKey;
import com.judge.security.ApiKeyContext;
import com.judge.webhook.WebhookSender;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lets an upstream platform check compatibility before routing work here: protocol version,
 * capabilities, runtime images (so replicas can be compared) and the caller's own role.
 */
@RestController
public class MetaController {

    /** Bump when a breaking change is made to the grader protocol. */
    public static final int API_VERSION = 2;

    static final List<String> CAPABILITIES = List.of(
            "GRADER_ROLE", "PACKAGE_EXPORT", "PACKAGE_UPSERT", "WEBHOOK_SECRET");

    private final JudgeConfig judgeConfig;
    private final WebhookSender webhookSender;

    public MetaController(JudgeConfig judgeConfig, WebhookSender webhookSender) {
        this.judgeConfig = judgeConfig;
        this.webhookSender = webhookSender;
    }

    @GetMapping("/api/v1/meta")
    public ResponseEntity<Map<String, Object>> meta() {
        ApiKey caller = ApiKeyContext.get();
        Map<String, String> images = new LinkedHashMap<>();
        judgeConfig.getLanguages().forEach((lang, cfg) -> images.put(lang, cfg.getImage()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("apiVersion", API_VERSION);
        body.put("buildVersion", buildVersion());
        body.put("capabilities", CAPABILITIES);
        body.put("languages", List.copyOf(images.keySet()));
        body.put("images", images);
        body.put("workers", judgeConfig.getWorkers());
        body.put("webhookSecretConfigured", webhookSender.hasDedicatedSecret());
        body.put("callerRole", caller != null ? caller.getRole() : null);
        return ResponseEntity.ok(body);
    }

    private static String buildVersion() {
        String v = MetaController.class.getPackage().getImplementationVersion();
        return v != null ? v : "dev";
    }
}
