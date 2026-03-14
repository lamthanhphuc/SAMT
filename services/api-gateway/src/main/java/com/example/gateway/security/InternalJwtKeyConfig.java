package com.example.gateway.security;

import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
@EnableConfigurationProperties(InternalJwtProperties.class)
public class InternalJwtKeyConfig {

    @Bean
    public InternalJwtKeyMaterial internalJwtKeyMaterial(InternalJwtProperties props) {
        RSAPrivateKey privateKey = PemKeyLoader.loadRsaPrivateKeyPkcs8(Path.of(props.getPrivateKeyPemPath()));
        RSAPublicKey publicKey = PemKeyLoader.deriveRsaPublicKey(privateKey);

        RSAKey jwk = new RSAKey.Builder(publicKey)
                .keyUse(KeyUse.SIGNATURE)
                .keyID(props.getKeyId())
                .build();

        return new InternalJwtKeyMaterial(privateKey, publicKey, jwk);
    }
}
