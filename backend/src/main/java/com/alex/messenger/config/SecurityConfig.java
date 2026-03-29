package com.alex.messenger.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final InternalApiTokenFilter internalApiTokenFilter;
    private final BotApiTokenFilter botApiTokenFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login",
                                "/api/auth/request-code",
                                "/api/auth/verify-code",
                                "/api/auth/refresh",
                                "/api/auth/2fa/verify",
                                "/api/auth/qr/bind",
                                "/api/auth/qr/poll"
                        ).permitAll()
                        .requestMatchers("/ws/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/attachments/*/download",
                                "/api/attachments/*/preview",
                                "/api/attachments/*/thumbnail"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/photos/download").permitAll()
                        .requestMatchers("/api/internal/**").hasRole("INTERNAL_SYSTEM")
                        .requestMatchers("/api/bot-api/**").hasRole("BOT_API")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(internalApiTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(botApiTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOriginPattern("*");
        configuration.addAllowedHeader("*");
        configuration.addAllowedMethod("*");
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
