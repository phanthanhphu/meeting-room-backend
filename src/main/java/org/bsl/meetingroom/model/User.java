package org.bsl.meetingroom.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection="users")
public class User {
    @Id private String id;
    @Indexed(unique=true) private String username;
    @Indexed(unique=true) private String email;
    private String fullName;
    private String department;
    private String passwordHash;
    /** Last successfully authenticated Windows/AD sAMAccountName. */
    private String domainAccount;
    private AccountSource accountSource;
    private Role role = Role.USER;
    private boolean enabled = true;
    private long authVersion = 0L;
    @CreatedDate private LocalDateTime createdAt;

    public String getId(){return id;} public void setId(String id){this.id=id;}
    public String getUsername(){return username;} public void setUsername(String v){username=v;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;}
    public String getFullName(){return fullName;} public void setFullName(String v){fullName=v;}
    public String getDepartment(){return department;} public void setDepartment(String v){department=v;}
    public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String v){passwordHash=v;}
    public String getDomainAccount(){return domainAccount;} public void setDomainAccount(String v){domainAccount=v;}
    public AccountSource getAccountSource(){return accountSource;} public void setAccountSource(AccountSource v){accountSource=v;}
    public Role getRole(){return role;} public void setRole(Role v){role=v;}
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public long getAuthVersion(){return authVersion;} public void setAuthVersion(long v){authVersion=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
