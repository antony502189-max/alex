package com.alex.messenger.config;

import com.alex.messenger.bot.BotAccountEntity;
import com.alex.messenger.bot.DeveloperBotService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class BotApiTokenFilter extends OncePerRequestFilter {

    private static final String BOT_API_PATH_PREFIX = "/api/bot-api/";
    private static final String BOT_TOKEN_HEADER = "X-Bot-Token";

    private final DeveloperBotService developerBotService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith(BOT_API_PATH_PREFIX) && !"/api/bot-api".equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String presentedToken = extractToken(request);
        BotAccountEntity botAccount = developerBotService.authenticateApiToken(presentedToken).orElse(null);
        if (botAccount == null) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"invalid_bot_token\"}");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                botAccount.getBotUserId().toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_BOT_API"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        String headerToken = request.getHeader(BOT_TOKEN_HEADER);
        return headerToken != null ? headerToken.trim() : null;
    }
}
