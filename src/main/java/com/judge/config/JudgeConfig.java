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
    private Map<String, LanguageConfig> languages = new LinkedHashMap<>();

    @Data
    public static class LanguageConfig {
        private String image;
        private String sourceFile;
        private String compileCmd = "";
        private String runCmd;
    }
}
