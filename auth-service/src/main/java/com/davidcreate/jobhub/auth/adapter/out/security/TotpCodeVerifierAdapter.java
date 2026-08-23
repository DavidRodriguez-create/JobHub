package com.davidcreate.jobhub.auth.adapter.out.security;

import com.davidcreate.jobhub.auth.application.port.out.TotpCodeVerifier;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class TotpCodeVerifierAdapter implements TotpCodeVerifier {

    @ConfigProperty(name = "auth.totp.issuer", defaultValue = "JobHub")
    String issuer;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeVerifier codeVerifier =
            new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());

    @Override
    public String generateSecret() {
        return secretGenerator.generate();
    }

    @Override
    public String buildOtpAuthUri(String base32Secret, String accountEmail) {
        QrData data = new QrData.Builder()
                .label(accountEmail)
                .secret(base32Secret)
                .issuer(issuer)
                .build();
        return data.getUri();
    }

    @Override
    public boolean verify(String base32Secret, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return codeVerifier.isValidCode(base32Secret, code);
    }
}
