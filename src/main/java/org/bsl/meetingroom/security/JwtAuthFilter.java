package org.bsl.meetingroom.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.bsl.meetingroom.repository.UserRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final TokenService tokenService;
    private final UserRepository users;
    public JwtAuthFilter(TokenService tokenService,UserRepository users){this.tokenService=tokenService;this.users=users;}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException{
        String auth=req.getHeader("Authorization");
        if(auth!=null && auth.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication()==null){
            TokenService.TokenClaims c=tokenService.parse(auth.substring(7));
            if(c!=null){
                users.findByUsernameIgnoreCase(c.username()).filter(u->u.isEnabled() && u.getAuthVersion()==c.authVersion()).ifPresent(u->{
                    var a=new UsernamePasswordAuthenticationToken(u.getUsername(),null,List.of(new SimpleGrantedAuthority("ROLE_"+u.getRole().name())));
                    SecurityContextHolder.getContext().setAuthentication(a);
                });
            }
        }
        chain.doFilter(req,res);
    }
}
