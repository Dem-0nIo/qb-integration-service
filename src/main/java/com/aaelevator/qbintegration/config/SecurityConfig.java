package com.aaelevator.qbintegration.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        var admin = User.builder()
                .username("admin")
                .password(encoder.encode("admin123"))
                .roles("ADMIN")
                .build();

        var accounting = User.builder()
                .username("accounting")
                .password(encoder.encode("accounting123"))
                .roles("ACCOUNTING")
                .build();

        var client = User.builder()
                .username("client")
                .password(encoder.encode("client123"))
                .roles("CLIENT")
                .build();

        return new InMemoryUserDetailsManager(admin, accounting, client);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/services/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/accounting/**").hasRole("ACCOUNTING")
                        .requestMatchers("/api/client/**").hasRole("CLIENT")
                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> {})
                .csrf(csrf -> csrf.disable());

        http.addFilterBefore(jwtAuthenticationFilter,
                org.springframework.security.web.authentication.www.BasicAuthenticationFilter.class);

        return http.build();
    }
}