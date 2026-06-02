package com.davidcreate.jobhub.auth.application.port.out;

import com.davidcreate.jobhub.auth.domain.entity.User;

public interface TokenGenerator {

    String generate(User user);

    long lifespanSeconds();
}
