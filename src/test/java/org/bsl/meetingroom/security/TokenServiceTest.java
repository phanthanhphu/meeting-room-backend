package org.bsl.meetingroom.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsl.meetingroom.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {
    @Test
    void startsWithoutSecretInLocalEnvironmentAndSignsTokens() {
        TokenService service=new TokenService(new ObjectMapper(),"",480,new MockEnvironment());
        User user=new User();
        user.setId("test-id");
        user.setUsername("local-user");
        String token=service.generate(user);
        assertNotNull(service.parse(token));
        // Key is unique to the running instance, not committed in configuration.
        TokenService otherInstance=new TokenService(new ObjectMapper(),"",480,new MockEnvironment());
        assertNull(otherInstance.parse(token));
    }

    @Test
    void productionRejectsMissingSecret() {
        MockEnvironment environment=new MockEnvironment();
        environment.setActiveProfiles("prod");
        assertThrows(IllegalStateException.class,
                () -> new TokenService(new ObjectMapper(),"",480,environment));
    }

    @Test
    void productionAcceptsConfiguredStrongSecret() {
        MockEnvironment environment=new MockEnvironment();
        environment.setActiveProfiles("production");
        String key="0123456789abcdef0123456789abcdef";
        TokenService service=new TokenService(new ObjectMapper(),key,480,environment);
        User user=new User();
        user.setUsername("production-user");
        assertNotNull(service.parse(service.generate(user)));
    }

    @Test
    void productionRejectsShortSecret() {
        MockEnvironment environment=new MockEnvironment();
        environment.setActiveProfiles("prod");
        assertThrows(IllegalStateException.class,
                () -> new TokenService(new ObjectMapper(),"short",480,environment));
    }
}
