package backend.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import backend.security.JwtAuthenticationFilter;
import backend.service.CustomUserDetailsService;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final CustomUserDetailsService userService;
    private final List<String> allowedOrigins;

    public SecurityConfig(
            JwtAuthenticationFilter jwtFilter,
            CustomUserDetailsService userService,
            @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.jwtFilter = jwtFilter;
        this.userService = userService;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
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
                        .hasAuthority("ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register")
                        .hasAnyAuthority("ASSISTANT_MEDICAL", "MEDECIN", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/auth/reset-password")
                        .hasAuthority("ADMIN")

                        .requestMatchers(HttpMethod.DELETE, "/api/users/**")
                        .hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/users", "/api/users/**")
                        .hasAnyAuthority("ASSISTANT_MEDICAL", "MEDECIN", "ADMIN")
                        .requestMatchers("/api/users/**")
                        .hasAuthority("ADMIN")

                        .requestMatchers(HttpMethod.GET, "/api/dossiers")
                        .hasAnyAuthority("ASSISTANT_MEDICAL", "MEDECIN", "ADMIN")
                        .requestMatchers("/api/dossiers/**")
                        .hasAnyAuthority("PATIENT", "ASSISTANT_MEDICAL", "MEDECIN", "ADMIN")

                        .requestMatchers("/api/documents/**")
                        .hasAnyAuthority("PATIENT", "ASSISTANT_MEDICAL", "MEDECIN", "ADMIN")

                        .requestMatchers("/api/validations/**")
                        .hasAnyAuthority("ASSISTANT_MEDICAL", "MEDECIN", "ADMIN")
                        .requestMatchers("/api/interactions/**")
                        .hasAnyAuthority("ASSISTANT_MEDICAL", "MEDECIN", "ADMIN")

                        .requestMatchers("/api/chatbot/**")
                        .hasAuthority("PATIENT")

                        .requestMatchers(HttpMethod.POST, "/api/v1/organizations/*/preparation-cases")
                        .hasAnyAuthority("ASSISTANT_MEDICAL", "MEDECIN", "ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/organizations/*/preparation-cases/*/invitations")
                        .hasAnyAuthority("ASSISTANT_MEDICAL", "MEDECIN", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/v1/organizations/*/preparation-cases/*/invitations/*")
                        .hasAnyAuthority("ASSISTANT_MEDICAL", "MEDECIN", "ADMIN")
                        .requestMatchers("/api/v1/organizations/**")
                        .denyAll()

                        .requestMatchers(HttpMethod.POST, "/api/v1/patient-access/exchange")
                        .permitAll()
                        .requestMatchers("/api/v1/patient-access/**")
                        .denyAll()

                        // Historical prototype APIs are intentionally dormant in V0.1.
                        .requestMatchers("/api/parcours/**", "/api/environnement/**")
                        .denyAll()

                        .requestMatchers("/api/admin/**")
                        .hasAuthority("ADMIN")

                        .anyRequest().authenticated())
                .userDetailsService(userService);

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
