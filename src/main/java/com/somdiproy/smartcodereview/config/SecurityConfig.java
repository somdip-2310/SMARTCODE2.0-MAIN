package com.somdiproy.smartcodereview.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
	    http
	        .csrf(csrf -> csrf.disable())
	        .authorizeHttpRequests(authz -> authz
	                .requestMatchers("/", "/auth/**", "/css/**", "/js/**", "/actuator/health").permitAll()
	                .requestMatchers("/login/oauth2/**", "/oauth2/**").permitAll()
	                .anyRequest().permitAll()  // For development, allow all
	            )
	        .oauth2Login(oauth2 -> oauth2
	                .loginPage("/auth/github/login")
	                .defaultSuccessUrl("/auth/github/success", true)
	                .failureUrl("/auth/github/error")
	                .permitAll()
	            );
	    return http.build();
	}
}