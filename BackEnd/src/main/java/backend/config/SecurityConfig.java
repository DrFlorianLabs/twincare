package backend.config;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import backend.security.JwtAuthenticationFilter;
import backend.preparation.adapter.in.web.PatientCsrfTokenRepository;
import backend.preparation.adapter.in.web.PatientAttachmentUploadConcurrencyFilter;
import backend.preparation.adapter.in.web.PatientMutationOriginFilter;
import backend.preparation.adapter.in.web.PatientPreparationPrincipal;
import backend.preparation.adapter.in.web.PatientSecurityResponses;
import backend.preparation.adapter.in.web.PatientSessionAuthenticationFilter;
import backend.preparation.adapter.in.web.ProfessionalPreparationNoStoreFilter;
import backend.service.CustomUserDetailsService;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final CustomUserDetailsService userService;
    private final List<String> allowedOrigins;
    private final List<String> patientAllowedOrigins;

    public SecurityConfig(
            JwtAuthenticationFilter jwtFilter,
            CustomUserDetailsService userService,
            @Value("${app.cors.allowed-origins}") String allowedOrigins,
            @Value("${app.preparation.patient-cors.allowed-origins}") String patientAllowedOrigins) {
        this.jwtFilter = jwtFilter;
        this.userService = userService;
        this.allowedOrigins = parseOrigins(allowedOrigins);
        this.patientAllowedOrigins = parseOrigins(patientAllowedOrigins);
        if (this.allowedOrigins.isEmpty() || this.patientAllowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("CORS origins must not be empty");
        }
    }

    private List<String> parseOrigins(String origins) {
        return Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain patientFilterChain(
            HttpSecurity http,
            PatientSessionAuthenticationFilter patientSessionFilter,
            PatientMutationOriginFilter originFilter,
            PatientAttachmentUploadConcurrencyFilter uploadConcurrencyFilter,
            PatientCsrfTokenRepository csrfTokenRepository,
            PatientSecurityResponses securityResponses) throws Exception {
        var csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName("_csrf");

        http
                .securityMatcher("/api/v1/patient-access/**")
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        .requireCsrfProtectionMatcher(SecurityConfig::requiresPatientCsrf))
                .cors(cors -> cors.configurationSource(patientCorsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                securityResponses.unauthorized(response))
                        .accessDeniedHandler((request, response, exception) ->
                                securityResponses.forbidden(response)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/patient-access/exchange")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/patient-access/session")
                        .hasAuthority(PatientPreparationPrincipal.AUTHORITY)
                        .requestMatchers(HttpMethod.GET, "/api/v1/patient-access/reason")
                        .hasAuthority(PatientPreparationPrincipal.AUTHORITY)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/patient-access/reason")
                        .hasAuthority(PatientPreparationPrincipal.AUTHORITY)
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/patient-access/attachment")
                        .hasAuthority(PatientPreparationPrincipal.AUTHORITY)
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/patient-access/attachment")
                        .hasAuthority(PatientPreparationPrincipal.AUTHORITY)
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/patient-access/attachment")
                        .hasAuthority(PatientPreparationPrincipal.AUTHORITY)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/patient-access/session")
                        .hasAuthority(PatientPreparationPrincipal.AUTHORITY)
                        .anyRequest().denyAll());

        http.addFilterBefore(patientSessionFilter, CsrfFilter.class);
        http.addFilterAfter(originFilter, PatientSessionAuthenticationFilter.class);
        http.addFilterAfter(uploadConcurrencyFilter, PatientMutationOriginFilter.class);
        return http.build();
    }

    private static boolean requiresPatientCsrf(jakarta.servlet.http.HttpServletRequest request) {
        if (Set.of("GET", "HEAD", "TRACE", "OPTIONS").contains(request.getMethod())) {
            return false;
        }
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof PatientPreparationPrincipal;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain staffFilterChain(
            HttpSecurity http,
            ProfessionalPreparationNoStoreFilter professionalPreparationNoStoreFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                response.sendError(HttpStatus.UNAUTHORIZED.value()))
                        .accessDeniedHandler((request, response, exception) ->
                                response.sendError(HttpStatus.FORBIDDEN.value())))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                        .denyAll()

                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/**")
                        .denyAll()

                        .requestMatchers(HttpMethod.GET, "/api/v1/professional-context")
                        .hasAnyAuthority("ASSISTANT_MEDICAL", "MEDECIN")
                        .requestMatchers("/api/v1/professional-context")
                        .denyAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/organizations/*/preparation-cases")
                        .hasAnyAuthority("ASSISTANT_MEDICAL", "MEDECIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/organizations/*/preparation-cases")
                        .hasAnyAuthority("ASSISTANT_MEDICAL", "MEDECIN")
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/organizations/*/preparation-cases/*/preparation-view")
                        .hasAnyAuthority("ASSISTANT_MEDICAL", "MEDECIN")
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/organizations/*/preparation-cases/*/invitations")
                        .hasAnyAuthority("ASSISTANT_MEDICAL", "MEDECIN")
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/v1/organizations/*/preparation-cases/*/invitations/*")
                        .hasAnyAuthority("ASSISTANT_MEDICAL", "MEDECIN")
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/organizations/*/preparation-cases/*/tasks")
                        .hasAnyAuthority("ASSISTANT_MEDICAL", "MEDECIN")
                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/organizations/*/preparation-cases/*/tasks/*/status")
                        .hasAnyAuthority("ASSISTANT_MEDICAL", "MEDECIN")
                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/organizations/*/preparation-cases/*/ready-confirmation")
                        .hasAuthority("MEDECIN")
                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/organizations/*/preparation-cases/*/closure")
                        .hasAuthority("MEDECIN")
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/organizations/*/preparation-cases/*/attachment")
                        .hasAnyAuthority("ASSISTANT_MEDICAL", "MEDECIN")
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/organizations/*/preparation-cases/*/audit-events")
                        .hasAuthority("MEDECIN")
                        .requestMatchers("/api/v1/organizations/**")
                        .denyAll()
                        .requestMatchers("/api/v1/**", "/api/**")
                        .denyAll()
                        .anyRequest().denyAll())
                .userDetailsService(userService);

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(professionalPreparationNoStoreFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    FilterRegistrationBean<PatientSessionAuthenticationFilter> disablePatientSessionFilterAutoRegistration(
            PatientSessionAuthenticationFilter filter) {
        FilterRegistrationBean<PatientSessionAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> disableJwtFilterAutoRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<PatientMutationOriginFilter> disablePatientOriginFilterAutoRegistration(
            PatientMutationOriginFilter filter) {
        FilterRegistrationBean<PatientMutationOriginFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<PatientAttachmentUploadConcurrencyFilter> disablePatientUploadFilterAutoRegistration(
            PatientAttachmentUploadConcurrencyFilter filter) {
        FilterRegistrationBean<PatientAttachmentUploadConcurrencyFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<ProfessionalPreparationNoStoreFilter> disableProfessionalPreparationNoStoreAutoRegistration(
            ProfessionalPreparationNoStoreFilter filter) {
        FilterRegistrationBean<ProfessionalPreparationNoStoreFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private CorsConfigurationSource patientCorsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(patientAllowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Content-Type", "Accept", PatientCsrfTokenRepository.HEADER_NAME));
        config.setExposedHeaders(List.of(PatientCsrfTokenRepository.HEADER_NAME));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/patient-access/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
