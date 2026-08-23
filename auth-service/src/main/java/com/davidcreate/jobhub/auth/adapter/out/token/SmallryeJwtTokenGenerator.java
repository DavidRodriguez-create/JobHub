package com.davidcreate.jobhub.auth.adapter.out.token;

import com.davidcreate.jobhub.auth.application.port.out.TokenGenerator;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.valueobject.AdminAllowlist;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class SmallryeJwtTokenGenerator implements TokenGenerator {

    @ConfigProperty(name = "auth.token.lifespan-seconds", defaultValue = "86400")
    public long lifespanSeconds;

    /**
     * Comma-separated admin allowlist (auth.admin.emails / AUTH_ADMIN_EMAILS).
     * Injected as Optional because SmallRye Config treats an empty-string config
     * value as absent for non-Optional String properties. Defaults to "" (no admins).
     * Public + non-final so unit tests can set it directly without CDI.
     */
    public String adminEmailsConfig = "";

    @Inject
    void setAdminEmailsConfig(@ConfigProperty(name = "auth.admin.emails") Optional<String> value) {
        this.adminEmailsConfig = value.orElse("");
    }

    @Override
    public String generate(User user) {
        List<String> groups = AdminAllowlist.groupsFor(adminEmailsConfig, user.getEmail());
        return Jwt.subject(user.getId().toString())
                .upn(user.getEmail())
                .groups(Set.copyOf(groups))
                .claim("email_verified", user.isEmailVerified())
                .claim("first_name", user.getFirstName())
                .claim("last_name", user.getLastName())
                .expiresIn(Duration.ofSeconds(lifespanSeconds))
                .sign();
    }

    @Override
    public long lifespanSeconds() {
        return lifespanSeconds;
    }
}
