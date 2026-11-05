package com.everload.everload.service;

import com.everload.everload.model.Role;
import com.everload.everload.model.User;
import com.everload.everload.model.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(showSql = false)
class UserPlayerPreferencePersistenceTest {
    @Autowired
    private TestEntityManager entityManager;

    @Test
    void preferenceSurvivesReloadAndCanBeChangedBack() {
        User user = newUser("player-pref");
        user.setFloatingPlayerStyle("island");
        Long id = entityManager.persistAndGetId(user, Long.class);
        entityManager.flush();
        entityManager.clear();
        User loaded = entityManager.find(User.class, id);
        assertEquals("island", loaded.getFloatingPlayerStyle());
        loaded.setFloatingPlayerStyle("bar");
        entityManager.flush();
        entityManager.clear();
        assertEquals("bar", entityManager.find(User.class, id).getFloatingPlayerStyle());
    }

    @Test
    void accountsWithoutAPreferenceKeepTheirExistingProfileData() {
        User user = newUser("existing-pref");
        Long id = entityManager.persistAndGetId(user, Long.class);
        entityManager.flush();
        entityManager.clear();
        User loaded = entityManager.find(User.class, id);
        assertNull(loaded.getFloatingPlayerStyle());
        assertTrue(loaded.getShowLastSeen());
        assertEquals("existing-pref@example.test", loaded.getEmail());
    }

    private User newUser(String username) {
        return User.builder().username(username).email(username + "@example.test")
                .password("test-only-hash").role(Role.BASIC_USER).status(UserStatus.ACTIVE).build();
    }
}
