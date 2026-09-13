package com.example.demo.repository;

import com.example.demo.entity.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class UserAccountRepositoryJpaTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private UserAccountRepository users;

    @Test
    void save_setsCreatedAt_andFindByEmailRoundTrips() {
        UserAccount saved = users.saveAndFlush(UserAccount.builder().name("Alice").email("alice@example.com").build());
        em.clear();

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt(), "@CreationTimestamp set on insert");
        Optional<UserAccount> found = users.findByEmail("alice@example.com");
        assertTrue(found.isPresent());
        assertEquals("Alice", found.get().getName());
        assertTrue(users.findByEmail("nobody@example.com").isEmpty());
    }

    @Test
    void email_isUniqueAtTheDatabase() {
        users.saveAndFlush(UserAccount.builder().name("A").email("dup@example.com").build());

        assertThrows(DataIntegrityViolationException.class,
                () -> users.saveAndFlush(UserAccount.builder().name("B").email("dup@example.com").build()));
    }

    @Test
    void email_isRequired() {
        assertThrows(DataIntegrityViolationException.class,
                () -> users.saveAndFlush(UserAccount.builder().name("NoMail").build()));
    }
}
