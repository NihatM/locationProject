package com.example.locationproject.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {
    static final String[] WHITE_LIST = new String[]{
            "/",
            "/static/**",
            "/api/v1/auth/**",
            "/css/**",
            "/js/**",
            "images/**",
            "/images/*.jpg",
            "/images/*.png",
            "/images/*.gif",
            "images/icons/*.svg",
            "/v3/api-docs/**",
            "/api/v1/markers/all",
            "/api/maps-key",
            "http://www.w3.org/2000/svg",
            "/about",
            "/contact",
            "/api/v1/markers/contact/new",
            "/test",
            "/api/v1/thanks/sections"

    };
    static final String[] SWAGGER_WHITELIST = new String[]{
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable()).headers(headers -> headers.cacheControl(cache -> cache.disable()))
                .cors(cors -> cors
                        .configurationSource(corsConfigurationSource())
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(WHITE_LIST).permitAll()
                        .requestMatchers("/dashboard/**", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html").authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/dashboard", true)
///swagger-ui/index.html
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("https://qerbiazerbaycanim.com"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }


}
