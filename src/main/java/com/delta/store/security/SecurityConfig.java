package com.delta.store.security;

import com.delta.store.security.oauth2.CustomOAuth2UserService;
import com.delta.store.security.oauth2.CustomOidcUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOidcUserService customOidcUserService;

    public SecurityConfig(
            CustomUserDetailsService userDetailsService,
            CustomOAuth2UserService customOAuth2UserService,
            CustomOidcUserService customOidcUserService
    ) {
        this.userDetailsService = userDetailsService;
        this.customOAuth2UserService = customOAuth2UserService;
        this.customOidcUserService = customOidcUserService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http

            // ================= CSRF =================
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(
                    "/stripe/webhook",
                    "/stripe/webhook/**",
                    "/paypal/webhook",
                    "/api/**"
                )
            )

            // ================= AUTORIZZAZIONI =================
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                .requestMatchers("/api/products", "/api/products/**").hasRole("ADMIN")
                .requestMatchers(
                    "/index.html/**",
                    "/listcard.html/**",
                    "/listtable.html/**",
                    "/",
                    "/login",
                    "/catalogo",
                    "/contatti",
                    "/shop",
                    "/shop/**",
                    "/prodotti",

                    // CHECKOUT
                    "/checkout",
                    "/checkout/**",

                    // STRIPE WEBHOOK
                    "/stripe/**",
                    "/stripe/webhook",
                    "/stripe/webhook/**",

                    // PAYPAL (return/cancel + eventuali webhook)
                    "/paypal/**",

                    // RIENTRI PAGAMENTO GIFT CARD (la pagina /giftcard resta protetta)
                    "/giftcard/stripe/success",
                    "/giftcard/paypal/success",
                    "/giftcard/cancel",

                    // CARRELLO
                    "/cart",
                    "/cart/**",

                    // PASSWORD RESET
                    "/password",
                    "/password/**",

                    // REGISTRAZIONE
                    "/register",
                   
                    "/register/check-email",
                    "/register/check-phone",
                   
                    
                    
                    
                    
                    
                    // LOGIN OAUTH2 GOOGLE
                    "/oauth2/**",
                    "/login/oauth2/**",

                    // API PRODOTTI
                    "/api/products",

                    "/error",
                    "/favicon.ico",
                    "/manifest.json",
                    "/service-worker.js",

                    // STATIC
                    "/css/**",
                    "/js/**",
                    "/assets/**",
                    "/images/**",
                    "/icons/**",
                    "/uploads/**"
                ).permitAll()
                .requestMatchers("/admin", "/admin/**", "/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )

            // ================= LOGIN CLASSICO =================
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/", false)
                .failureUrl("/?login&invalidCredentials")
                .permitAll()
            )

            // ================= LOGIN GOOGLE / OAUTH2 =================
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                    .oidcUserService(customOidcUserService)
                )
                .defaultSuccessUrl("/", false)
                .failureUrl("/login?error")
            )

            // ================= LOGOUT =================
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .permitAll()
            )

            .userDetailsService(userDetailsService);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
