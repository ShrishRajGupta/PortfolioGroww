package com.example.demo.repository;

import com.example.demo.entity.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserAccountRepositoryTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSaveUserAccount() {
        // Arrange
        UserAccount userAccount = new UserAccount();
        userAccount.setName("John Doe");
        userAccount.setEmail("john.doe@example.com");

        when(userAccountRepository.save(userAccount)).thenReturn(userAccount);

        // Act
        UserAccount savedAccount = userAccountRepository.save(userAccount);

        // Assert
        assertEquals("John Doe", savedAccount.getName());
        assertEquals("john.doe@example.com", savedAccount.getEmail());
        assertNotNull(savedAccount.getCreatedAt());
    }

    @Test
    void testFindById() {
        // Arrange
        Long userId = 1L;
        UserAccount mockUserAccount = new UserAccount();
        mockUserAccount.setId(userId);
        mockUserAccount.setName("Jane Doe");
        mockUserAccount.setEmail("jane.doe@example.com");

        when(userAccountRepository.findById(userId)).thenReturn(Optional.of(mockUserAccount));

        // Act
        Optional<UserAccount> result = userAccountRepository.findById(userId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(userId, result.get().getId());
        assertEquals("Jane Doe", result.get().getName());
        assertEquals("jane.doe@example.com", result.get().getEmail());
    }



    @Test
    void testFindByEmail() {
        // Arrange
        String testEmail = "test@example.com";
        UserAccount mockUserAccount = new UserAccount();
        mockUserAccount.setId(1L);
        mockUserAccount.setEmail(testEmail);
        mockUserAccount.setName("Test User");

        when(userAccountRepository.findByEmail(testEmail)).thenReturn(Optional.of(mockUserAccount));

        // Act
        Optional<UserAccount> result = userAccountRepository.findByEmail(testEmail);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testEmail, result.get().getEmail());
        assertEquals("Test User", result.get().getName());
    }

    @Test
    void testDeleteById() {
        // Arrange
        Long userId = 1L;
        doNothing().when(userAccountRepository).deleteById(userId);

        // Act
        userAccountRepository.deleteById(userId);

        // Assert
        verify(userAccountRepository, times(1)).deleteById(userId);
    }

}
