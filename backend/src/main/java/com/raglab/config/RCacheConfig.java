package com.raglab.config;

import com.raglab.rcache.client.RCacheClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RCacheConfig {

    private static final Logger log = LoggerFactory.getLogger(RCacheConfig.class);

    @Value("${raglab.rcache.host:127.0.0.1}")
    private String host;

    @Value("${raglab.rcache.port:6380}")
    private int port;

    @Value("${raglab.rcache.pool-size:16}")
    private int poolSize;

    @Value("${raglab.rcache.socket-timeout-ms:2000}")
    private int socketTimeoutMs;

    @Value("${raglab.rcache.connect-timeout-ms:2000}")
    private int connectTimeoutMs;

    @Bean(destroyMethod = "close")
    public RCacheClient rCacheClient() {
        log.info("Configuring RCache Java Client for host={}:{} (poolSize={})", host, port, poolSize);
        return new RCacheClient(host, port, poolSize, socketTimeoutMs, connectTimeoutMs, 3, 5000);
    }
}

