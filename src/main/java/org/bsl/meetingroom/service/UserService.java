package org.bsl.meetingroom.service;

import org.bsl.meetingroom.dto.Dtos.*;
import org.bsl.meetingroom.common.socket.AppSocketPublisher;
import org.bsl.meetingroom.exception.AppException;
import org.bsl.meetingroom.model.*;
import org.bsl.meetingroom.repository.BookingRepository;
import org.bsl.meetingroom.repository.UserRepository;
import org.bsl.meetingroom.security.PasswordPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.util.StringUtils;

@Service
public class UserService {
    private final UserRepository repo;
    private final BookingRepository bookings;
    private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();
    private final AppSocketPublisher socket;
    private final NotificationService notifications;

    public UserService(UserRepository repo,BookingRepository bookings,PasswordEncoder encoder,AppSocketPublisher socket,NotificationService notifications){
        this.repo=repo;this.bookings=bookings;this.encoder=encoder;this.socket=socket;this.notifications=notifications;
    }

    public List<UserResponse> list(){return repo.findAll().stream().map(ResponseMapper::user).toList();}

    /**
     * Resolve a successfully authenticated Active Directory identity to the local
     * Meeting Room user record. A first-time Domain user is provisioned automatically
     * as USER with a blank department. Existing SYSTEM accounts are linked rather than
     * duplicated, so a pre-created ADMIN keeps the ADMIN role when logging in via AD.
     */
    public User resolveOrProvisionDomainUser(ActiveDirectoryService.DirectoryUser directoryUser, String canonicalEmail) {
        String sam = normalize(directoryUser.getUsername());
        String email = normalize(canonicalEmail);
        String adEmail = normalize(directoryUser.getEmail());

        Optional<User> match = repo.findByEmailIgnoreCase(email);
        if (match.isEmpty() && StringUtils.hasText(adEmail) && !adEmail.equals(email)) {
            match = repo.findByEmailIgnoreCase(adEmail);
        }
        if (match.isEmpty() && StringUtils.hasText(sam)) {
            match = repo.findByDomainAccountIgnoreCase(sam);
        }
        if (match.isEmpty() && StringUtils.hasText(sam)) {
            match = repo.findByUsernameIgnoreCase(sam);
        }

        if (match.isPresent()) {
            User existing = match.get();
            boolean changed = false;

            if (existing.getAccountSource() == null) {
                existing.setAccountSource(StringUtils.hasText(existing.getPasswordHash()) ? AccountSource.SYSTEM : AccountSource.DOMAIN);
                changed = true;
            }
            if (!Objects.equals(normalize(existing.getDomainAccount()), sam)) {
                existing.setDomainAccount(sam);
                changed = true;
            }
            if (!StringUtils.hasText(existing.getFullName()) && StringUtils.hasText(directoryUser.getDisplayName())) {
                existing.setFullName(directoryUser.getDisplayName().trim());
                changed = true;
            }
            // Auto-provisioned Domain records use the canonical corporate email.
            // Manually-created SYSTEM accounts keep the email entered by Admin.
            if (existing.getAccountSource() == AccountSource.DOMAIN && !email.equals(normalize(existing.getEmail()))) {
                Optional<User> emailOwner = repo.findByEmailIgnoreCase(email);
                if (emailOwner.isEmpty() || Objects.equals(emailOwner.get().getId(), existing.getId())) {
                    existing.setEmail(email);
                    changed = true;
                }
            }
            return changed ? repo.save(existing) : existing;
        }

        User created = new User();
        created.setUsername(sam);
        created.setEmail(email);
        created.setFullName(StringUtils.hasText(directoryUser.getDisplayName()) ? directoryUser.getDisplayName().trim() : sam);
        created.setDepartment("");
        created.setPasswordHash(null); // Domain passwords are never persisted.
        created.setDomainAccount(sam);
        created.setAccountSource(AccountSource.DOMAIN);
        created.setRole(Role.USER);
        created.setEnabled(true);
        created = repo.save(created);

        notifications.notifyAdminsExcept(created.getId(), NotificationType.USER_CREATED, "Domain account added",
                created.getFullName()+" signed in with Domain for the first time and was added automatically.",
                "USER", created.getId(), "/users");
        socket.publish("USER_CHANGED");
        return created;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public UserResponse create(CreateUserRequest req,User actor){
        String username=req.username().trim();
        String email=req.email().trim();
        if(repo.existsByUsernameIgnoreCase(username)) throw AppException.conflict("USERNAME_EXISTS","Username đã tồn tại");
        if(repo.existsByEmailIgnoreCase(email)) throw AppException.conflict("EMAIL_EXISTS","Email đã tồn tại");
        User u=new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setFullName(req.fullName().trim());
        u.setDepartment(req.department().trim());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setAccountSource(AccountSource.SYSTEM);
        u.setRole(req.role());
        u.setEnabled(req.enabled()==null || req.enabled());
        UserResponse saved=ResponseMapper.user(repo.save(u));
        notifications.notifyUser(saved.id(),NotificationType.USER_CREATED,"Account created",
                "Your Meeting Room account was created by "+actor.getFullName()+".","USER",saved.id(),"/dashboard");
        notifications.notifyAdminsExcept(actor.getId(),NotificationType.USER_CREATED,"User created",
                actor.getFullName()+" created "+saved.fullName()+".","USER",saved.id(),"/users");
        socket.publish("USER_CHANGED");
        return saved;
    }

    public UserResponse update(String id,UpdateUserRequest req,User actor){
        User u=find(id);
        AccountSource source = u.getAccountSource()==null ? AccountSource.SYSTEM : u.getAccountSource();
        if (source == AccountSource.SYSTEM) {
            repo.findByEmailIgnoreCase(req.email()).ifPresent(existing->{
                if(!Objects.equals(existing.getId(),id)) throw AppException.conflict("EMAIL_EXISTS","Email đã tồn tại");
            });
        }
        if(Objects.equals(u.getId(),actor.getId()) && (!req.enabled() || req.role()!=Role.ADMIN))
            throw AppException.badRequest("SELF_ROLE_CHANGE","Admin không thể tự khóa hoặc hạ quyền tài khoản của mình");
        // DOMAIN email/domainAccount are directory identity fields and are not editable locally.
        if (source == AccountSource.SYSTEM) u.setEmail(req.email().trim());
        u.setFullName(req.fullName().trim());
        u.setDepartment(req.department()==null?"":req.department().trim());
        boolean authChanged=u.getRole()!=req.role() || u.isEnabled()!=req.enabled();
        u.setRole(req.role());
        u.setEnabled(req.enabled());
        if(authChanged) u.setAuthVersion(u.getAuthVersion()+1);
        UserResponse saved=ResponseMapper.user(repo.save(u));
        if(!Objects.equals(actor.getId(),u.getId())) notifications.notifyUser(u.getId(),NotificationType.USER_UPDATED,"Account updated",
                "Your account information was updated by "+actor.getFullName()+".","USER",u.getId(),"/dashboard");
        notifications.notifyAdminsExcept(actor.getId(),NotificationType.USER_UPDATED,"User updated",
                actor.getFullName()+" updated "+u.getFullName()+".","USER",u.getId(),"/users");
        socket.publish("USER_CHANGED");
        return saved;
    }

    public UserResponse setEnabled(String id,boolean enabled,User actor){
        User u=find(id);
        if(Objects.equals(u.getId(),actor.getId()) && !enabled)
            throw AppException.badRequest("SELF_DISABLE","Admin không thể tự khóa tài khoản của mình");
        if(u.isEnabled()!=enabled) u.setAuthVersion(u.getAuthVersion()+1);
        u.setEnabled(enabled);
        UserResponse saved=ResponseMapper.user(repo.save(u));
        if(enabled && !Objects.equals(actor.getId(),u.getId())) notifications.notifyUser(u.getId(),NotificationType.USER_STATUS_CHANGED,"Account enabled",
                "Your Meeting Room account was enabled by "+actor.getFullName()+".","USER",u.getId(),"/dashboard");
        notifications.notifyAdminsExcept(actor.getId(),NotificationType.USER_STATUS_CHANGED,"User status changed",
                actor.getFullName()+" "+(enabled?"enabled ":"disabled ")+u.getFullName()+".","USER",u.getId(),"/users");
        socket.publish("USER_CHANGED");
        return saved;
    }

    public ResetPasswordResponse resetPassword(String id,User actor){
        User u=find(id);
        if (u.getAccountSource() == AccountSource.DOMAIN)
            throw AppException.badRequest("DOMAIN_PASSWORD_MANAGED_BY_AD", "Domain account password is managed by Active Directory and cannot be reset here.");
        String temp=PasswordPolicy.generate(random);
        u.setPasswordHash(encoder.encode(temp));
        u.setAuthVersion(u.getAuthVersion()+1);
        repo.save(u);
        notifications.notifyUser(u.getId(),NotificationType.USER_PASSWORD_RESET,"Password reset",
                "Your password was reset by "+actor.getFullName()+". Sign in using the temporary password provided by Admin.","USER",u.getId(),"/dashboard");
        notifications.notifyAdminsExcept(actor.getId(),NotificationType.USER_PASSWORD_RESET,"Password reset",
                actor.getFullName()+" reset password for "+u.getFullName()+".","USER",u.getId(),"/users");
        socket.publish("USER_CHANGED");
        return new ResetPasswordResponse(temp);
    }

    public void delete(String id,User actor){
        User u=find(id);
        if(Objects.equals(u.getId(),actor.getId()))
            throw AppException.badRequest("SELF_DELETE","Admin không thể xóa tài khoản của mình");
        if(bookings.existsByCreatedById(id))
            throw AppException.conflict("USER_HAS_BOOKINGS","User đã có lịch sử booking; hãy Disable thay vì Delete");
        String fullName=u.getFullName();
        notifications.deleteForUser(id);
        repo.delete(u);
        notifications.notifyAdminsExcept(actor.getId(),NotificationType.USER_DELETED,"User deleted",
                actor.getFullName()+" deleted "+fullName+".","USER",id,"/users");
        socket.publish("USER_CHANGED");
    }

    private User find(String id){
        return repo.findById(id).orElseThrow(()->AppException.notFound("Không tìm thấy user"));
    }
}
