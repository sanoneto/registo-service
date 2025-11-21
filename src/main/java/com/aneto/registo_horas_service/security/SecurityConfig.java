package com.aneto.registo_horas_service.security;


import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // ESSENCIAL para que @PreAuthorize funcione
@RequiredArgsConstructor
public class SecurityConfig {

    private final GatewayAuthFilter gatewayAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // 1. Desabilita CSRF
                .csrf(AbstractHttpConfigurer::disable)

                // 2. Define sessão como stateless (sem cookies)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 3. Permite todas as requisições (a autorização fina é feita pelo @PreAuthorize)
                // O filtro customizado (Passo 4) irá autenticar o usuário para o @PreAuthorize funcionar
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().permitAll()
                )

                // 4. Adiciona o filtro customizado para ler headers antes de qualquer outra autenticação
                .addFilterBefore(gatewayAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}