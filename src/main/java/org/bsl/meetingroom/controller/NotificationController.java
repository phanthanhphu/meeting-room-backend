package org.bsl.meetingroom.controller;

import org.bsl.meetingroom.dto.Dtos.*;
import org.bsl.meetingroom.service.AuthService;
import org.bsl.meetingroom.service.NotificationService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;
    private final AuthService auth;
    public NotificationController(NotificationService service,AuthService auth){this.service=service;this.auth=auth;}

    @GetMapping public List<NotificationResponse> list(){return service.list(auth.current());}
    @GetMapping("/unread-count") public UnreadNotificationCountResponse unreadCount(){return new UnreadNotificationCountResponse(service.unreadCount(auth.current()));}
    @PatchMapping("/{id}/read") public NotificationResponse read(@PathVariable String id){return service.markRead(id,auth.current());}
    @PatchMapping("/read-all") public void readAll(){service.markAllRead(auth.current());}
}
