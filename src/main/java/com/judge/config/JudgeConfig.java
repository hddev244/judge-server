package com.judge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "judge")
public class JudgeConfig {

    private int workers = 4;
    private String workBase = "/tmp/judge";
    private String queueKey = "judge:queue";
    private String testcaseBasePath = "/data/problems";
    private long outputLimitBytes = 1_048_576;
    private long compileTimeoutMs = 30_000;
    private double sandboxCpus = 1.0;
    private Map<String, LanguageConfig> languages = new LinkedHashMap<>();

    @Data
    public static class LanguageConfig {
        private String image;
        private String sourceFile;
        private String compileCmd = "";
        private String runCmd;
        /** Time limit is multiplied by this before judging (Java/Python are slower than C++). */
        private double timeMultiplier = 1.0;
        /** Extra KB added to the container memory cap so the runtime can start; verdict still uses the raw limit. */
        private long memoryBonusKb = 0;
    }
}
