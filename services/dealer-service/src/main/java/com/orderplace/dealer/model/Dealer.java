package com.orderplace.dealer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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

/** Dealer master data. Never deleted, only deactivated. No setters: change state through the methods below. */
@Entity
@Table(name = "dealer")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Dealer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "dealer_code", nullable = false, updatable = false, length = 30)
    private String dealerCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "contact_person", nullable = false, length = 150)
    private String contactPerson;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    @Column(name = "address", nullable = false, columnDefinition = "text")
    private String address;

    @Column(name = "tax_registration_no", length = 50)
    private String taxRegistrationNo;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    /** Null until first saved; Hibernate then manages it (optimistic locking). */
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

    public static Dealer create(String dealerCode, String name, String contactPerson, String email, String phone,
                                String address, String taxRegistrationNo) {
        Dealer d = new Dealer();
        d.dealerCode = dealerCode;
        d.active = true;
        d.updateDetails(name, contactPerson, email, phone, address, taxRegistrationNo);
        return d;
    }

    /** The dealer code is deliberately not changeable after creation. */
    public void updateDetails(String name, String contactPerson, String email, String phone, String address,
                              String taxRegistrationNo) {
        this.name = name;
        this.contactPerson = contactPerson;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.taxRegistrationNo = taxRegistrationNo;
    }

    public void activate() { this.active = true; }

    public void deactivate() { this.active = false; }
}
