package org.bsl.meetingroom.controller;

import jakarta.validation.Valid;
import org.bsl.meetingroom.dto.Dtos.*;
import org.bsl.meetingroom.service.*;
import org.bsl.meetingroom.security.WebSocketTicketService;
import org.bsl.meetingroom.security.LoginAttemptService;
import org.bsl.meetingroom.exception.AppException;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final WebSocketTicketService wsTickets;
    private final LoginAttemptService attempts;
    public AuthController(AuthService auth, WebSocketTicketService wsTickets,LoginAttemptService attempts){this.auth=auth;this.wsTickets=wsTickets;this.attempts=attempts;}
    @PostMapping("/login") public LoginResponse login(@Valid @RequestBody LoginRequest req,HttpServletRequest request){
        String key=attempts.key(request.getRemoteAddr(),req.resolveIdentifier());
        attempts.check(key);
        try {
            LoginResponse result=auth.login(req);
            attempts.succeeded(key);
            return result;
        } catch(AppException ex) {
            if(ex.getStatus()==HttpStatus.UNAUTHORIZED || "SYSTEM_INVALID_CREDENTIALS".equals(ex.getCode())) attempts.failed(key);
            throw ex;
        }
    }
    @GetMapping("/me") public UserResponse me(){return ResponseMapper.user(auth.current());}
    @PostMapping("/ws-ticket") public ResponseEntity<Map<String,String>> wsTicket(){
        // This endpoint requires a valid JWT through SecurityConfig; ticket is one-use, 30 seconds.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("ticket",wsTickets.issue()));
    }
}
