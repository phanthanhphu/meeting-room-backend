package org.bsl.meetingroom.controller;

import jakarta.validation.Valid;
import org.bsl.meetingroom.dto.Dtos.*;
import org.bsl.meetingroom.service.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth){this.auth=auth;}
    @PostMapping("/login") public LoginResponse login(@Valid @RequestBody LoginRequest req){return auth.login(req);}
    @GetMapping("/me") public UserResponse me(){return ResponseMapper.user(auth.current());}
}
