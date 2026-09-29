package org.bsl.meetingroom.controller;

import jakarta.validation.Valid;
import org.bsl.meetingroom.dto.Dtos.*;
import org.bsl.meetingroom.model.BookingStatus;
import org.bsl.meetingroom.service.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/bookings")
@PreAuthorize("hasAnyRole('VIEWER','ROOM_MANAGER','ADMIN')")
public class AdminBookingController {
    private final BookingService service;
    private final AuthService auth;
    public AdminBookingController(BookingService service,AuthService auth){this.service=service;this.auth=auth;}

    @GetMapping public List<BookingResponse> all(){return service.all();}
    @GetMapping("/pending") public List<BookingResponse> pending(){return service.pending();}
    @GetMapping("/report") public List<BookingResponse> report(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) String roomId,
            @RequestParam(required=false) String userId,
            @RequestParam(required=false) BookingStatus status){
        return service.report(from,to,roomId,userId,status);
    }
    @PatchMapping("/{id}/approve") @PreAuthorize("hasAnyRole('ROOM_MANAGER','ADMIN')") public BookingResponse approve(@PathVariable String id){return service.approve(id,auth.current());}
    @PatchMapping("/{id}/reject") @PreAuthorize("hasAnyRole('ROOM_MANAGER','ADMIN')") public BookingResponse reject(@PathVariable String id,@Valid @RequestBody RejectRequest req){return service.reject(id,req.reason(),auth.current());}
    @PatchMapping("/{id}/cancel") @PreAuthorize("hasAnyRole('ROOM_MANAGER','ADMIN')") public BookingResponse cancel(@PathVariable String id){return service.cancel(id,auth.current());}
    @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('ROOM_MANAGER','ADMIN')") public void delete(@PathVariable String id){service.delete(id,auth.current());}
}
