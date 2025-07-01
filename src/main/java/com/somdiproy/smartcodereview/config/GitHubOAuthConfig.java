package com.somdiproy.smartcodereview.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.SecurityFilterChain;

/**
 * GitHub OAuth configuration for accessing private repositories
 */
@Configuration
@EnableWebSecurity
public class GitHubOAuthConfig {
    
    @Value("${github.oauth.client-id:}")
    private String clientId;
    
    @Value("${github.oauth.client-secret:}")
    private String clientSecret;
    
    @Value("${github.oauth.redirect-uri:http://localhost:8083/login/oauth2/code/github}")
    private String redirectUri;
    
    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(githubClientRegistration());
    }
    
    private ClientRegistration githubClientRegistration() {
        return ClientRegistration.withRegistrationId("github")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .scope("repo", "user:email")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user")
                .userNameAttributeName("login")
                .clientName("GitHub")
                .build();
    }
    
    @Bean
    public SecurityFilterChain gitHubSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/auth/github")
                .defaultSuccessUrl("/auth/github/success", true)
                .failureUrl("/auth/github/error")
            )
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/auth/github/**", "/login/oauth2/**").permitAll()
                .anyRequest().permitAll()
            );
        
        return http.build();
    }
}