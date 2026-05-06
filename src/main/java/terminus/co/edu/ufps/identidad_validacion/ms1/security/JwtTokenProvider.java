package terminus.co.edu.ufps.identidad_validacion.ms1.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${codecup.jwt.private-key-pem:}")
    private String privateKeyPem;

    @Value("${codecup.jwt.public-key-pem:}")
    private String publicKeyPem;

    @Value("${codecup.jwt.key-id}")
    private String keyId;

    @Value("${codecup.jwt.issuer}")
    private String issuer;

    @Value("${codecup.jwt.ttl-seconds}")
    private long ttlSeconds;

    private RSAKey rsaJwk;

    @PostConstruct
    public void init() throws Exception {
        if (privateKeyPem == null || privateKeyPem.isBlank()) {
            var gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            var pair = gen.generateKeyPair();
            this.rsaJwk = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(keyId)
                    .build();
            log.warn("JWT keys missing, generated ephemeral keys.");
            log.warn("JWT_PRIVATE_KEY_PEM=\n{}", toPem("PRIVATE KEY", pair.getPrivate().getEncoded()));
            log.warn("JWT_PUBLIC_KEY_PEM=\n{}", toPem("PUBLIC KEY", pair.getPublic().getEncoded()));
            return;
        }

        var pubBytes = Base64.getDecoder().decode(stripPem(publicKeyPem));
        var privBytes = Base64.getDecoder().decode(stripPem(privateKeyPem));
        var pub = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(pubBytes));
        var priv = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(privBytes));
        this.rsaJwk = new RSAKey.Builder(pub).privateKey(priv).keyID(keyId).build();
    }

    public String generarToken(String userId, String cedula, String email, String nombre, List<String> roles) {
        try {
            var now = Instant.now();
            var claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(userId)
                    .claim("cedula", cedula)
                    .claim("email", email)
                    .claim("nombre", nombre)
                    .claim("roles", roles)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
                    .build();
            var jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build(),
                    claims);
            jwt.sign(new RSASSASigner(rsaJwk.toPrivateKey()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Token signing failed", e);
        }
    }

    public String getJwksJson() {
        return new com.nimbusds.jose.jwk.JWKSet(rsaJwk.toPublicJWK()).toString();
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    private static String stripPem(String pem) {
        return pem.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
    }

    private static String toPem(String type, byte[] bytes) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(bytes)
                + "\n-----END " + type + "-----\n";
    }
}

