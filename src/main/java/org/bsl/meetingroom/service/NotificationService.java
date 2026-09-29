package org.bsl.meetingroom.service;

import org.bsl.meetingroom.common.socket.AppSocketPublisher;
import org.bsl.meetingroom.dto.Dtos.NotificationResponse;
import org.bsl.meetingroom.exception.AppException;
import org.bsl.meetingroom.model.*;
import org.bsl.meetingroom.repository.NotificationRepository;
import org.bsl.meetingroom.repository.UserRepository;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final UserRepository users;
    private final Clock clock;
    private final AppSocketPublisher socket;

    public NotificationService(NotificationRepository notifications, UserRepository users, Clock clock, AppSocketPublisher socket){
        this.notifications=notifications; this.users=users; this.clock=clock; this.socket=socket;
    }

    public List<NotificationResponse> list(User user){
        return notifications.findTop50ByRecipientUserIdOrderByCreatedAtDesc(user.getId()).stream().map(this::map).toList();
    }

    public long unreadCount(User user){ return notifications.countByRecipientUserIdAndReadFalse(user.getId()); }

    public NotificationResponse markRead(String id, User user){
        AppNotification n=notifications.findById(id).orElseThrow(()->AppException.notFound("Không tìm thấy thông báo"));
        if(!Objects.equals(n.getRecipientUserId(),user.getId())) throw AppException.forbidden("Bạn không có quyền cập nhật thông báo này");
        if(!n.isRead()){
            n.setRead(true); n.setReadAt(LocalDateTime.now(clock)); n=notifications.save(n);
            socket.publish("NOTIFICATION_CHANGED");
        }
        return map(n);
    }

    public void markAllRead(User user){
        List<AppNotification> unread=notifications.findByRecipientUserIdAndReadFalse(user.getId());
        if(unread.isEmpty()) return;
        LocalDateTime now=LocalDateTime.now(clock);
        unread.forEach(n->{n.setRead(true);n.setReadAt(now);});
        notifications.saveAll(unread);
        socket.publish("NOTIFICATION_CHANGED");
    }

    public void notifyUser(String userId, NotificationType type, String title, String message, String entityType, String entityId, String actionPath){
        if(userId==null || userId.isBlank()) return;
        boolean created=users.findById(userId).filter(User::isEnabled).map(u->{save(u.getId(),type,title,message,entityType,entityId,actionPath);return true;}).orElse(false);
        if(created) socket.publish("NOTIFICATION_CHANGED");
    }

    public void notifyAdminsExcept(String excludeUserId, NotificationType type, String title, String message, String entityType, String entityId, String actionPath){
        List<User> recipients=users.findByRoleAndEnabledTrue(Role.ADMIN).stream().filter(u->!Objects.equals(u.getId(),excludeUserId)).toList();
        recipients.forEach(u->save(u.getId(),type,title,message,entityType,entityId,actionPath));
        if(!recipients.isEmpty()) socket.publish("NOTIFICATION_CHANGED");
    }

    public void notifyBookingManagersExcept(String excludeUserId, NotificationType type, String title, String message, String entityType, String entityId, String actionPath){
        List<User> recipients=users.findByRoleInAndEnabledTrue(List.of(Role.ROOM_MANAGER,Role.ADMIN)).stream()
                .filter(u->!Objects.equals(u.getId(),excludeUserId)).toList();
        recipients.forEach(u->save(u.getId(),type,title,message,entityType,entityId,actionPath));
        if(!recipients.isEmpty()) socket.publish("NOTIFICATION_CHANGED");
    }

    public void notifyAllEnabledExcept(String excludeUserId, NotificationType type, String title, String message, String entityType, String entityId, String actionPath){
        List<User> recipients=users.findByEnabledTrue().stream().filter(u->!Objects.equals(u.getId(),excludeUserId)).toList();
        recipients.forEach(u->save(u.getId(),type,title,message,entityType,entityId,actionPath));
        if(!recipients.isEmpty()) socket.publish("NOTIFICATION_CHANGED");
    }

    public void deleteForUser(String userId){ notifications.deleteByRecipientUserId(userId); }

    private void save(String recipientUserId, NotificationType type, String title, String message, String entityType, String entityId, String actionPath){
        AppNotification n=new AppNotification();
        n.setRecipientUserId(recipientUserId); n.setType(type); n.setTitle(title); n.setMessage(message);
        n.setEntityType(entityType); n.setEntityId(entityId); n.setActionPath(actionPath); n.setRead(false);
        notifications.save(n);
    }

    private NotificationResponse map(AppNotification n){
        return new NotificationResponse(n.getId(),n.getType(),n.getTitle(),n.getMessage(),n.getEntityType(),n.getEntityId(),n.getActionPath(),n.isRead(),n.getCreatedAt(),n.getReadAt());
    }
}
