package com.example.gateway.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PemKeyLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsPkcs8PrivateKeyAndDerivesMatchingPublicKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        byte[] privateDer = keyPair.getPrivate().getEncoded();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(privateDer)
            + "\n-----END PRIVATE KEY-----\n";

        Path pemFile = tempDir.resolve("private.pem");
        Files.writeString(pemFile, pem, StandardCharsets.UTF_8);

        RSAPrivateKey loadedPrivateKey = PemKeyLoader.loadRsaPrivateKeyPkcs8(pemFile);
        RSAPublicKey derivedPublicKey = PemKeyLoader.deriveRsaPublicKey(loadedPrivateKey);

        assertThat(loadedPrivateKey.getModulus()).isEqualTo(((RSAPrivateKey) keyPair.getPrivate()).getModulus());
        assertThat(derivedPublicKey.getModulus()).isEqualTo(((RSAPublicKey) keyPair.getPublic()).getModulus());
        assertThat(derivedPublicKey.getPublicExponent()).isEqualTo(((RSAPublicKey) keyPair.getPublic()).getPublicExponent());
    }

    @Test
    void throwsClearErrorWhenPemFileDoesNotExist() {
        Path missingPath = tempDir.resolve("missing.pem");

        assertThatThrownBy(() -> PemKeyLoader.loadRsaPrivateKeyPkcs8(missingPath))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unable to read PEM file");
    }

    @Test
    void throwsClearErrorWhenPemContentIsInvalid() throws Exception {
        Path pemFile = tempDir.resolve("invalid.pem");
        Files.writeString(pemFile, "-----BEGIN PRIVATE KEY-----\nnot_base64!!\n-----END PRIVATE KEY-----", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> PemKeyLoader.loadRsaPrivateKeyPkcs8(pemFile))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unable to parse RSA private key");
    }

    @Test
    void throwsWhenPrivateKeyCannotProvideCrtInformation() {
        RSAPrivateKey nonCrtPrivateKey = new RSAPrivateKey() {
            @Override
            public String getAlgorithm() {
                return "RSA";
            }

            @Override
            public String getFormat() {
                return "PKCS#8";
            }

            @Override
            public byte[] getEncoded() {
                return new byte[0];
            }

            @Override
            public BigInteger getPrivateExponent() {
                return BigInteger.ONE;
            }

            @Override
            public BigInteger getModulus() {
                return BigInteger.TEN;
            }
        };

        assertThatThrownBy(() -> PemKeyLoader.deriveRsaPublicKey(nonCrtPrivateKey))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unable to derive RSA public key")
            .hasRootCauseMessage("Private key is not RSAPrivateCrtKey; provide a matching public key instead");
    }
}
