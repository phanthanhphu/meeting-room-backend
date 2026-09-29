package org.bsl.meetingroom.controller;

import org.bsl.meetingroom.dto.Dtos.DashboardResponse;
import org.bsl.meetingroom.service.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboard; private final AuthService auth;
    public DashboardController(DashboardService dashboard,AuthService auth){this.dashboard=dashboard;this.auth=auth;}
    @GetMapping("/summary") public DashboardResponse summary(){return dashboard.summary(auth.current());}
}
