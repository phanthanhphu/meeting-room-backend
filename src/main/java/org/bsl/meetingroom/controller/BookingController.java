package org.bsl.meetingroom.controller;

import jakarta.validation.Valid;
import org.bsl.meetingroom.dto.Dtos.*;
import org.bsl.meetingroom.service.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final BookingService service; private final AuthService auth;
    public BookingController(BookingService service,AuthService auth){this.service=service;this.auth=auth;}
    @PostMapping @PreAuthorize("hasAnyRole('USER','ROOM_MANAGER','ADMIN')") public BookingResponse create(@Valid @RequestBody BookingRequest req){return service.create(req,auth.current());}
    @GetMapping("/mine") public List<BookingResponse> mine(){return service.mine(auth.current());}
    @GetMapping("/{id}") public BookingResponse get(@PathVariable String id){return service.get(id,auth.current());}
    @PutMapping("/{id}") @PreAuthorize("hasAnyRole('USER','ROOM_MANAGER','ADMIN')") public BookingResponse update(@PathVariable String id,@Valid @RequestBody BookingRequest req){return service.update(id,req,auth.current());}
    @PatchMapping("/{id}/cancel") @PreAuthorize("hasAnyRole('USER','ROOM_MANAGER','ADMIN')") public BookingResponse cancel(@PathVariable String id){return service.cancel(id,auth.current());}
    @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('USER','ROOM_MANAGER','ADMIN')") public void delete(@PathVariable String id){service.delete(id,auth.current());}
    @GetMapping("/{id}/history") public List<HistoryResponse> history(@PathVariable String id){return service.history(id,auth.current());}
    @GetMapping("/calendar/range") public List<BookingResponse> calendar(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){return service.calendar(from,to,auth.current());}
}
