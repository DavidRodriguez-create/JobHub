package com.davidcreate.jobhub.auth.adapter.out.security;

import com.davidcreate.jobhub.auth.application.port.out.PasswordHasher;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class BcryptPasswordHasher implements PasswordHasher {

    @ConfigProperty(name = "auth.bcrypt.cost", defaultValue = "12")
    int cost;

    @Override
    public String hash(String rawPassword) {
        return BcryptUtil.bcryptHash(rawPassword, cost);
    }

    @Override
    public boolean matches(String rawPassword, String hashedPassword) {
        return BcryptUtil.matches(rawPassword, hashedPassword);
    }
}
