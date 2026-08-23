package com.davidcreate.jobhub.auth.application.port.out;

public interface SecretEncryptor {

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
