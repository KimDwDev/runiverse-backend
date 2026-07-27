package com.runiverse.running_service.infrastructure.security;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class Argon2PasswordHashAdapterTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private Argon2PasswordHashAdapter argon2PasswordHashAdapter;

    @Test
    @DisplayName("원본 비밀번호를 해시하여 반환한다")
    void hashPassword() {
        // given
        String rawPassword = "password!";
        String encodedPassword = "encoded-password";

        when(passwordEncoder.encode(rawPassword))
                .thenReturn(encodedPassword);

        // when
        String result = argon2PasswordHashAdapter.hash(rawPassword);

        // then
        assertThat(result).isEqualTo(encodedPassword);
        verify(passwordEncoder).encode(rawPassword);
    }

}
