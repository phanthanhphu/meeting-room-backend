package org.bsl.meetingroom.controller;

import jakarta.validation.Valid;
import org.bsl.meetingroom.dto.Dtos.*;
import org.bsl.meetingroom.service.RoomService;
import org.bsl.meetingroom.service.AuthService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomService service; private final AuthService auth;
    public RoomController(RoomService service,AuthService auth){this.service=service;this.auth=auth;}
    @GetMapping public List<RoomResponse> list(){return service.list();}
    @GetMapping("/{id}") public RoomResponse get(@PathVariable String id){return service.get(id);}
    @PostMapping @PreAuthorize("hasAnyRole('ROOM_MANAGER','ADMIN')") public RoomResponse create(@Valid @RequestBody RoomRequest req){return service.create(req,auth.current());}
    @PutMapping("/{id}") @PreAuthorize("hasAnyRole('ROOM_MANAGER','ADMIN')") public RoomResponse update(@PathVariable String id,@Valid @RequestBody RoomRequest req){return service.update(id,req,auth.current());}
    @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('ROOM_MANAGER','ADMIN')") public void delete(@PathVariable String id){service.delete(id,auth.current());}
}
