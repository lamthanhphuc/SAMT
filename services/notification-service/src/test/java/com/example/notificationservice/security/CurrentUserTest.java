package com.example.notificationservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserTest {

    @Test
    void exposesUserDetailsContract() {
        CurrentUser currentUser = new CurrentUser(
                42L,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        assertThat(currentUser.getUserId()).isEqualTo(42L);
        assertThat(currentUser.getUsername()).isEqualTo("42");
        assertThat(currentUser.getPassword()).isNull();
        assertThat(currentUser.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
        assertThat(currentUser.isAccountNonExpired()).isTrue();
        assertThat(currentUser.isAccountNonLocked()).isTrue();
        assertThat(currentUser.isCredentialsNonExpired()).isTrue();
        assertThat(currentUser.isEnabled()).isTrue();
    }
}
