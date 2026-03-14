package com.example.gateway.mtls;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enables mTLS for gateway -> downstream proxy traffic.
 */
@Configuration
@EnableConfigurationProperties(GatewayMtlsProperties.class)
public class GatewayMtlsHttpClientConfig {

    @Bean
    @ConditionalOnProperty(prefix = "gateway.mtls", name = "enabled", havingValue = "true")
    HttpClientCustomizer mtlsHttpClientCustomizer(SslBundles sslBundles, GatewayMtlsProperties props) {
        SslBundle bundle = sslBundles.getBundle(props.getClientBundle());
        SslContext sslContext;
        try {
            sslContext = SslContextBuilder.forClient()
                    .keyManager(bundle.getManagers().getKeyManagerFactory())
                    .trustManager(bundle.getManagers().getTrustManagerFactory())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to build mTLS SslContext from bundle: " + props.getClientBundle(), e);
        }

        return httpClient -> httpClient.secure(ssl -> ssl.sslContext(sslContext));
    }
}
