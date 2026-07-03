package com.judge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.judge.repository.ApiKeyRepository;
import com.judge.security.ApiKeyFilter;
import com.judge.security.RateLimitFilter;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           ApiKeyRepository apiKeyRepository,
                                           ProxyManager<byte[]> rateLimitProxyManager,
                                           ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new ApiKeyFilter(apiKeyRepository, objectMapper),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new RateLimitFilter(rateLimitProxyManager, objectMapper), ApiKeyFilter.class)
                .build();
    }
}
