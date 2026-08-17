package com.swp391.bloodcare.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {


    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


    @Bean

    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Swagger & public
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/swagger-resources/**", "/webjars/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()

                        // Public GET cụ thể
                        .requestMatchers(HttpMethod.GET, "/api/blog/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/event/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/achievements/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/images/**").permitAll()


                        //Public GET cần đăng nhập
                        .requestMatchers(HttpMethod.GET, "/api/healthcheck/get-by-registration/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/after-donation/get-by-healthcheck/**").authenticated()
                        .requestMatchers(HttpMethod.PUT,"/api/accounts/change-password/").authenticated()


                        // Role-based
                        .requestMatchers("/api/role/**").hasRole("ADMIN")
                        .requestMatchers("/api/achievements/**").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers("/api/blood-bags/**").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers("/api/event/**").hasAnyRole( "ADMIN")
                        .requestMatchers("/api/healthcheck/**").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers("/api/after-donation/**").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers("/api/blog/**").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers("/api/auth/set-role/**").hasRole("ADMIN")
                        .requestMatchers("/api/gmail/**").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers("/api/accounts/**").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers("/api/blood-requests/{requestId}/approve").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers("/api/blood-requests/{requestId}/reject").hasAnyRole("ADMIN", "STAFF")


                        // Tất cả còn lại yêu cầu login
                        .anyRequest().authenticated()
                )



                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }



    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:*",
                "https://swp391-su25-1.onrender.com"
        ));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE","PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(
                "Content-Type", "Authorization", "X-Requested-With", "Origin", "Accept"
        ));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }




}
