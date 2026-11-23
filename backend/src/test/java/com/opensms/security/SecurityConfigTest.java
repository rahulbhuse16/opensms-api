package com.opensms.security;

import com.opensms.controller.HealthController;
import com.opensms.tenant.controller.TenantController;
import com.opensms.tenant.service.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({HealthController.class, TenantController.class})
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyAuthenticationService authenticationService;

    @MockitoBean
    private TenantService tenantService;

    @Test
    void healthEndpointIsPublicWithoutApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application").value("OpenSMS"));

        verifyNoInteractions(authenticationService);
    }

    @Test
    void healthEndpointRemainsPublicWithInvalidApiKeyHeader() throws Exception {
        mockMvc.perform(get("/api/v1/health").header("X-API-Key", "invalid-secret"))
                .andExpect(status().isOk());

        verifyNoInteractions(authenticationService);
    }

    @Test
    void otherApiRoutesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/tenants"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("A valid API key is required."))
                .andExpect(jsonPath("$.path").value("/api/v1/tenants"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void invalidCredentialReturnsGeneric401WithoutInternalDetails() throws Exception {
        when(authenticationService.authenticate("invalid-secret"))
                .thenThrow(new BadCredentialsException("internal credential detail"));

        mockMvc.perform(get("/api/v1/tenants")
                        .header("X-API-Key", "invalid-secret")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("A valid API key is required."))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("internal credential detail"))))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("invalid-secret"))))
                .andExpect(jsonPath("$.path").value("/api/v1/tenants"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void queryParameterIsNotAcceptedAsApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/tenants").queryParam("apiKey", "valid-looking-secret"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authenticationService);
    }
}
