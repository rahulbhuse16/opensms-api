package com.opensms.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    private static final String PLAINTEXT_KEY = "os_live_test-credential";

    @Mock
    private ApiKeyAuthenticationService authenticationService;

    @Mock
    private AuthenticationEntryPoint authenticationEntryPoint;

    private ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new ApiKeyAuthenticationFilter(authenticationService, authenticationEntryPoint);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validCredentialPopulatesAuthenticatedTenantPrincipal() throws ServletException, IOException {
        UUID tenantId = UUID.randomUUID();
        UUID apiKeyId = UUID.randomUUID();
        TenantPrincipal principal = new TenantPrincipal(tenantId, apiKeyId);
        when(authenticationService.authenticate(PLAINTEXT_KEY)).thenReturn(principal);
        MockHttpServletRequest request = protectedRequest();
        request.addHeader("X-API-Key", PLAINTEXT_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(authentication.isAuthenticated());
        assertEquals(principal, authentication.getPrincipal());
        assertEquals(tenantId, ((TenantPrincipal) authentication.getPrincipal()).tenantId());
        assertEquals(apiKeyId, ((TenantPrincipal) authentication.getPrincipal()).apiKeyId());
        assertEquals(List.of(new SimpleGrantedAuthority("ROLE_TENANT")), authentication.getAuthorities());
        assertNull(authentication.getCredentials());
        verify(authenticationService).authenticate(PLAINTEXT_KEY);
    }

    @Test
    void missingHeaderIsRejectedAndDoesNotAuthenticate() throws ServletException, IOException {
        MockHttpServletRequest request = protectedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        verifyNoInteractions(authenticationService);
        verify(authenticationEntryPoint).commence(any(), any(), any(BadCredentialsException.class));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void failedAuthenticationClearsExistingSecurityContext() throws ServletException, IOException {
        SecurityContext previousContext = SecurityContextHolder.createEmptyContext();
        previousContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                "previous", null, List.of(new SimpleGrantedAuthority("ROLE_TENANT"))
        ));
        SecurityContextHolder.setContext(previousContext);
        when(authenticationService.authenticate(PLAINTEXT_KEY))
                .thenThrow(new BadCredentialsException("Do not expose this detail."));
        MockHttpServletRequest request = protectedRequest();
        request.addHeader("X-API-Key", PLAINTEXT_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(authenticationEntryPoint).commence(any(), any(), any(BadCredentialsException.class));
    }

    @Test
    void queryParameterIsNotAcceptedAsCredential() throws ServletException, IOException {
        MockHttpServletRequest request = protectedRequest();
        request.setParameter("apiKey", PLAINTEXT_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        verifyNoInteractions(authenticationService);
        verify(authenticationEntryPoint).commence(any(), any(), any(BadCredentialsException.class));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private MockHttpServletRequest protectedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/tenants");
        request.setServletPath("/api/v1/tenants");
        return request;
    }
}



