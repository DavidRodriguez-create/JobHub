package com.davidcreate.jobhub.auth.adapter.out.token;

import com.davidcreate.jobhub.auth.application.port.out.TokenGenerator;
import com.davidcreate.jobhub.auth.domain.entity.User;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Set;

@ApplicationScoped
public class SmallryeJwtTokenGenerator implements TokenGenerator {

    @ConfigProperty(name = "auth.token.lifespan-seconds", defaultValue = "86400")
    long lifespanSeconds;

    @Override
    public String generate(User user) {
        return Jwt.subject(user.getId().toString())
                .upn(user.getEmail())
                .groups(Set.of("user"))
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
