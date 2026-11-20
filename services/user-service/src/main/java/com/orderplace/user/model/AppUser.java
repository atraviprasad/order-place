package com.orderplace.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Profile of a Customer or Dealer user. Credentials and sessions live in Keycloak.
 * The user may log in only while this flag AND the organisation are active.
 */
@Entity
@Table(name = "app_user")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "keycloak_user_id", nullable = false, updatable = false)
    private UUID keycloakUserId;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "phone", length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, updatable = false, length = 20)
    private UserRole role;

    /** customer.id for CUSTOMER users, dealer id (Dealer Service) for DEALER users. */
    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    public static AppUser create(UUID keycloakUserId, String email, String firstName, String lastName, String phone,
            UserRole role, UUID orgId) {
        AppUser u = new AppUser();
        u.keycloakUserId = keycloakUserId;
        u.email = email;
        u.role = role;
        u.orgId = orgId;
        u.active = true;
        u.updateDetails(firstName, lastName, phone);
        return u;
    }

    public void updateDetails(String firstName, String lastName, String phone) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
    }

    public void changeOrganisation(UUID orgId) { this.orgId = orgId; }

    public void activate() { this.active = true; }

    public void deactivate() { this.active = false; }
}
