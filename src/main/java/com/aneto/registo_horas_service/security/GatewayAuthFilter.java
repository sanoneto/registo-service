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
     * 1. SOLUÇÃO PARA OS LOGS: Ignora rotas do Actuator.
     * O Spring Security não executará o doFilterInternal para estas rotas.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/actuator");
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
                            // Garante o prefixo ROLE_ para compatibilidade com @PreAuthorize("hasRole('...')")
                            String normalizedRole = trimmedRole.startsWith("ROLE_")
                                    ? trimmedRole
                                    : "ROLE_" + trimmedRole;
                            return new SimpleGrantedAuthority(normalizedRole);
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
                // Agora este log só aparecerá para rotas reais de negócio que falharam
                log.warn("Tentativa de acesso sem headers em rota protegida: {}", request.getRequestURI());
            }

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("Erro crítico no GatewayAuthFilter: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Erro na autenticação interna.");
        }
    }
}