package org.bsl.meetingroom.controller;

import jakarta.validation.Valid;
import org.bsl.meetingroom.dto.Dtos.*;
import org.bsl.meetingroom.service.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final UserService service;
    private final AuthService auth;
    public AdminUserController(UserService service,AuthService auth){this.service=service;this.auth=auth;}

    @GetMapping public List<UserResponse> list(){return service.list();}
    @PostMapping public UserResponse create(@Valid @RequestBody CreateUserRequest req){return service.create(req,auth.current());}
    @PutMapping("/{id}") public UserResponse update(@PathVariable String id,@Valid @RequestBody UpdateUserRequest req){return service.update(id,req,auth.current());}
    @PatchMapping("/{id}/enabled") public UserResponse enabled(@PathVariable String id,@RequestBody SetEnabledRequest req){return service.setEnabled(id,req.enabled(),auth.current());}
    @PostMapping("/{id}/reset-password") public ResetPasswordResponse resetPassword(@PathVariable String id){return service.resetPassword(id,auth.current());}
    @DeleteMapping("/{id}") public void delete(@PathVariable String id){service.delete(id,auth.current());}
}
