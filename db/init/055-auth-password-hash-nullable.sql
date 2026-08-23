-- 055-auth-password-hash-nullable.sql
-- Story #459 (ADR 0027): social-only accounts have no password. Drop the NOT NULL
-- so a NULL password_hash is valid; the JPA entity is flipped to match
-- (UserEntity.passwordHash -> @Column(nullable = true)). Every password-path read
-- of getPasswordHash() is guarded against null (LoginService, ChangePasswordService).

ALTER TABLE auth.user ALTER COLUMN password_hash DROP NOT NULL;
