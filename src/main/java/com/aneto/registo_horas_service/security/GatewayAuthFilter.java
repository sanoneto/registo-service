package com.aneto.registo_horas_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

@Component
public class GatewayAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuthFilter.class);
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLES_HEADER = "X-User-Roles";

    /**
     * Ignora rotas do Actuator e Documentação para não barrar o Health Check do Coolify.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/actuator") ||
                path.startsWith("/swagger-ui") ||
                path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String userId = request.getHeader(USER_ID_HEADER);
        String rolesString = request.getHeader(USER_ROLES_HEADER);

        try {
            if (userId != null && !userId.isBlank() && rolesString != null && !rolesString.isBlank()) {

                Collection<SimpleGrantedAuthority> authorities = Arrays.stream(rolesString.split(","))
                        .map(String::trim)
                        .filter(role -> !role.isEmpty())
                        .map(role -> {
                            String trimmedRole = role.toUpperCase();
                            return trimmedRole.startsWith("ROLE_")
                                    ? new SimpleGrantedAuthority(trimmedRole)
                                    : new SimpleGrantedAuthority("ROLE_" + trimmedRole);
                        })
                        .collect(Collectors.toList());

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        authorities
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Autenticação configurada para o user: {}", userId);
            } else {
                // Log apenas para rotas que deveriam ter autenticação via Gateway
                log.warn("Acesso sem headers de Gateway em rota protegida: {}", request.getRequestURI());
            }

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("Erro crítico no GatewayAuthFilter para URI {}: {}", request.getRequestURI(), e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Erro na autenticação interna.");
        }
    }
}