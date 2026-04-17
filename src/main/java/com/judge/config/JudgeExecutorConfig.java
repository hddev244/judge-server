package com.judge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class JudgeExecutorConfig {

    private final JudgeConfig judgeConfig;

    public JudgeExecutorConfig(JudgeConfig judgeConfig) {
        this.judgeConfig = judgeConfig;
    }

    @Bean("judgeExecutor")
    public Executor judgeExecutor() {
        int workers = judgeConfig.getWorkers();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(workers);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("judge-worker-");
        executor.initialize();
        return executor;
    }
}
