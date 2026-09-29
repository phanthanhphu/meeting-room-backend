package org.bsl.meetingroom.dto;

import jakarta.validation.constraints.*;
import org.bsl.meetingroom.model.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class Dtos {
    private Dtos() {}

    public record LoginRequest(String loginType, String identifier, String username, @NotBlank String password) {
        public String resolveIdentifier() {
            if (identifier != null && !identifier.isBlank()) return identifier.trim();
            return username == null ? "" : username.trim();
        }
        public LoginType resolveLoginType() { return LoginType.from(loginType); }
    }
    public record DomainUserResponse(String username, String userPrincipalName, String email, String displayName) {}
    public record LoginResponse(String token, UserResponse user, String authenticationType, DomainUserResponse domainUser) {}
    public record UserResponse(String id, String username, String email, String fullName, String department,
                               String domainAccount, AccountSource accountSource, Role role, boolean enabled) {}
    public record CreateUserRequest(
            @NotBlank @Size(min=3,max=60) String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min=2,max=120) String fullName,
            @NotBlank @Size(min=1,max=120) String department,
            @NotBlank @Size(min=12,max=100) @Pattern(regexp="^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%&*!?])\\S+$", message="Password must include uppercase, lowercase, number and special character (@#$%&*!?)") String password,
            @NotNull Role role,
            Boolean enabled) {}
    public record SetEnabledRequest(boolean enabled) {}
    public record UpdateUserRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min=2,max=120) String fullName,
            @Size(max=120) String department,
            @NotNull Role role,
            boolean enabled) {}
    public record ResetPasswordResponse(String temporaryPassword) {}

    public record RoomRequest(
            @NotBlank @Size(max=40) String code,
            @NotBlank @Size(max=120) String name,
            @NotBlank @Size(max=160) String location,
            @Min(1) @Max(500) int capacity,
            @Size(max=1000) String description,
            @Size(max=1000) String amenities,
            @NotNull RoomStatus status) {}
    public record RoomResponse(String id,String code,String name,String location,int capacity,String description,String amenities,RoomStatus status) {}

    public record BookingRequest(
            @NotBlank String roomId,
            @NotBlank @Size(max=180) String title,
            @Size(max=1000) String purpose,
            @Min(1) @Max(500) int attendeeCount,
            @NotNull LocalDateTime startAt,
            @NotNull LocalDateTime endAt) {}
    public record RejectRequest(@NotBlank @Size(max=1000) String reason) {}
    public record BookingResponse(
            String id, RoomResponse room, UserResponse createdBy, String bookedByAccount, String bookedByEmail,
            String title, String purpose, int attendeeCount,
            LocalDateTime startAt, LocalDateTime endAt, BookingStatus status, String adminNote,
            UserResponse approvedBy, LocalDateTime approvedAt, LocalDateTime rejectedAt,
            LocalDateTime cancelledAt, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record HistoryResponse(String id, BookingAction action, UserResponse actor, String note, LocalDateTime createdAt) {}
    public record NotificationResponse(String id, NotificationType type, String title, String message, String entityType, String entityId, String actionPath, boolean read, LocalDateTime createdAt, LocalDateTime readAt) {}
    public record UnreadNotificationCountResponse(long count) {}
    public record CalendarQuery(LocalDate from, LocalDate to) {}
    public record DashboardResponse(long rooms,long pending,long approvedToday,long myUpcoming,long users) {}
    public record ApiError(String code,String message,LocalDateTime timestamp,List<String> details) {}
}
