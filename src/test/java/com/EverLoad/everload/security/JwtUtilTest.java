package com.EverLoad.everload.security;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private static final String SECRET = "TestOnlySecretKey_MustBeAtLeast32CharsLong!!";

    private JwtUtil jwtUtil;
    private UserDetails user;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", 86_400_000L);
        user = User.withUsername("ApiUser").password("irrelevant").authorities("ROLE_BASIC_USER").build();
    }

    @Test
    void generateToken_thenExtractUsername_roundtrips() {
        String token = jwtUtil.generateToken(user);

        assertNotNull(token);
        assertEquals("ApiUser", jwtUtil.extractUsername(token));
    }

    @Test
    void generateToken_includesUniqueJti() {
        String token1 = jwtUtil.generateToken(user);
        String token2 = jwtUtil.generateToken(user);

        assertNotNull(jwtUtil.extractJti(token1));
        assertNotNull(jwtUtil.extractJti(token2));
        assertNotEquals(jwtUtil.extractJti(token1), jwtUtil.extractJti(token2));
    }

    @Test
    void isTokenValid_trueForMatchingUserAndUnexpiredToken() {
        String token = jwtUtil.generateToken(user);

        assertTrue(jwtUtil.isTokenValid(token, user));
    }

    @Test
    void isTokenValid_falseForDifferentUser() {
        String token = jwtUtil.generateToken(user);
        UserDetails otherUser = User.withUsername("OtherUser").password("x").authorities("ROLE_BASIC_USER").build();

        assertFalse(jwtUtil.isTokenValid(token, otherUser));
    }

    @Test
    void extractExpiration_isInTheFuture() {
        String token = jwtUtil.generateToken(user);

        assertTrue(jwtUtil.extractExpiration(token).after(new Date()));
    }

    @Test
    void expiredToken_throwsExpiredJwtExceptionOnParse() {
        ReflectionTestUtils.setField(jwtUtil, "expiration", -10_000L);
        String expiredToken = jwtUtil.generateToken(user);

        // The filter relies on this throwing so it can fail closed (treat as unauthenticated).
        assertThrows(ExpiredJwtException.class, () -> jwtUtil.extractUsername(expiredToken));
    }
}
