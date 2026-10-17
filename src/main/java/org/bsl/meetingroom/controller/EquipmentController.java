package org.bsl.meetingroom.controller;

import jakarta.validation.Valid;
import org.bsl.meetingroom.dto.Dtos.*;
import org.bsl.meetingroom.service.EquipmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {
    private final EquipmentService service;
    public EquipmentController(EquipmentService service){this.service=service;}
    @GetMapping public List<EquipmentResponse> list(){return service.list();}
    @GetMapping("/{id}") public EquipmentResponse get(@PathVariable String id){return service.get(id);}
    @PostMapping @PreAuthorize("hasAnyRole('ROOM_MANAGER','ADMIN')") public EquipmentResponse create(@Valid @RequestBody EquipmentRequest req){return service.create(req);}
    @PutMapping("/{id}") @PreAuthorize("hasAnyRole('ROOM_MANAGER','ADMIN')") public EquipmentResponse update(@PathVariable String id,@Valid @RequestBody EquipmentRequest req){return service.update(id,req);}
    @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('ROOM_MANAGER','ADMIN')") public void delete(@PathVariable String id){service.delete(id);}
}
