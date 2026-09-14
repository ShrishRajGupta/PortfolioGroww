package com.example.demo.repository;

import com.example.demo.entity.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * NOTE: this class mocks the repository it is named after, so it only exercises the entity.
 * Real @DataJpaTest coverage arrives with the Testcontainers work.
 */
class UserAccountRepositoryTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSaveUserAccount() {
        UserAccount userAccount = new UserAccount();
        userAccount.setName("John Doe");
        userAccount.setEmail("john.doe@example.com");
        // created_at is set by @CreationTimestamp on persist; a mock does not persist.
        userAccount.setCreatedAt(LocalDateTime.now());

        when(userAccountRepository.save(userAccount)).thenReturn(userAccount);

        UserAccount savedAccount = userAccountRepository.save(userAccount);

        assertEquals("John Doe", savedAccount.getName());
        assertEquals("john.doe@example.com", savedAccount.getEmail());
        assertNotNull(savedAccount.getCreatedAt());
    }

    @Test
    void testFindById() {
        UserAccount mockUserAccount = new UserAccount();
        mockUserAccount.setId(1L);
        mockUserAccount.setName("Jane Doe");
        mockUserAccount.setEmail("jane.doe@example.com");

        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(mockUserAccount));

        Optional<UserAccount> result = userAccountRepository.findById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
        assertEquals("Jane Doe", result.get().getName());
        assertEquals("jane.doe@example.com", result.get().getEmail());
    }

    @Test
    void testFindByEmail() {
        UserAccount mockUserAccount = new UserAccount();
        mockUserAccount.setId(1L);
        mockUserAccount.setEmail("test@example.com");
        mockUserAccount.setName("Test User");

        when(userAccountRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUserAccount));

        Optional<UserAccount> result = userAccountRepository.findByEmail("test@example.com");

        assertTrue(result.isPresent());
        assertEquals("test@example.com", result.get().getEmail());
        assertEquals("Test User", result.get().getName());
    }

    @Test
    void testDeleteById() {
        doNothing().when(userAccountRepository).deleteById(1L);

        userAccountRepository.deleteById(1L);

        verify(userAccountRepository, times(1)).deleteById(1L);
    }
}
