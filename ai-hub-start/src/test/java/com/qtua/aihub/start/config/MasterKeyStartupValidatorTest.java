package com.qtua.aihub.start.config;

import com.qtua.aihub.common.config.GatewayProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MasterKeyStartupValidatorTest {

    @Test
    void production_without_master_key_fails_fast() {
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        MasterKeyStartupValidator validator =
                new MasterKeyStartupValidator(properties(""), environment);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("master-key");
    }

    @Test
    void production_with_master_key_passes() {
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        MasterKeyStartupValidator validator =
                new MasterKeyStartupValidator(properties("sk-master"), environment);

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void local_without_master_key_passes() {
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        MasterKeyStartupValidator validator =
                new MasterKeyStartupValidator(properties(""), environment);

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    private GatewayProperties properties(String masterKey) {
        GatewayProperties properties = new GatewayProperties();
        properties.setMasterKey(masterKey);
        return properties;
    }
}
