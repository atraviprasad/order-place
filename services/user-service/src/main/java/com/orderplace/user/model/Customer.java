package com.orderplace.user.model;

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

/** A customer organisation (e.g. a company or university). Never deleted, only deactivated. */
@Entity
@Table(name = "customer")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_code", nullable = false, updatable = false, length = 30)
    private String customerCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "contact_person", nullable = false, length = 150)
    private String contactPerson;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    @Column(name = "billing_address", nullable = false, columnDefinition = "text")
    private String billingAddress;

    @Column(name = "delivery_address", nullable = false, columnDefinition = "text")
    private String deliveryAddress;

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

    public static Customer create(String customerCode, String name, String contactPerson, String email, String phone,
            String billingAddress, String deliveryAddress) {
        Customer c = new Customer();
        c.customerCode = customerCode;
        c.active = true;
        c.updateDetails(name, contactPerson, email, phone, billingAddress, deliveryAddress);
        return c;
    }

    /** The customer code is deliberately not changeable after creation. */
    public void updateDetails(String name, String contactPerson, String email, String phone, String billingAddress,
            String deliveryAddress) {
        this.name = name;
        this.contactPerson = contactPerson;
        this.email = email;
        this.phone = phone;
        this.billingAddress = billingAddress;
        this.deliveryAddress = deliveryAddress;
    }

    public void activate() { this.active = true; }

    public void deactivate() { this.active = false; }
}
