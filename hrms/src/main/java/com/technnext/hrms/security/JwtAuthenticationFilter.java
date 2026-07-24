package com.technnext.hrms.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per request: reads the "Authorization: Bearer <jwt>" header,
 * validates it, loads the full user, and populates the SecurityContext.
 *
 * FIX 1: Wrapped user-loading in tAry/catch so a deleted user whose token is
 *         still valid doesn't cause an unhandled UsernameNotFoundException that
 *         results in a 500 instead of a 401.
 * FIX 2: Verify the loaded user is still enabled (isActive) before setting the
 *         SecurityContext, so deactivated accounts can't use old tokens.
 * FIX 3: Clear SecurityContext on any JWT processing error to avoid a stale
 *         authentication being left in place.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            try {
                if (jwtService.isValid(token)
                        && SecurityContextHolder.getContext().getAuthentication() == null) {

                    String email = jwtService.extractEmail(token);

                    // FIX 1: catch UsernameNotFoundException -> treat as unauthenticated (401),
                    // not an unhandled exception (500)
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                    // FIX 2: reject tokens belonging to deactivated accounts
                    if (!userDetails.isEnabled()) {
                        // Do not set authentication; the request will fail with 401
                        filterChain.doFilter(request, response);
                        return;
                    }

                    var auth = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (UsernameNotFoundException ex) {
                // FIX 1: user deleted after token issued — treat as unauthenticated
                SecurityContextHolder.clearContext();
            } catch (Exception ex) {
                // FIX 3: any other JWT processing error -> clear context, proceed unauthenticated
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}