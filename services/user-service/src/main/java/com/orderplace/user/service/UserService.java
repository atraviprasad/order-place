package com.orderplace.user.service;

import com.orderplace.common.security.TenantContext;
import com.orderplace.common.web.ApiException;
import com.orderplace.common.web.PagedResponse;
import com.orderplace.user.api.dto.CreateUserRequest;
import com.orderplace.user.api.dto.MeResponse;
import com.orderplace.user.api.dto.UpdateUserRequest;
import com.orderplace.user.api.dto.UserCreatedResponse;
import com.orderplace.user.api.dto.UserResponse;
import com.orderplace.user.client.DealerClient;
import com.orderplace.user.client.DealerInfo;
import com.orderplace.user.model.AppUser;
import com.orderplace.user.model.Customer;
import com.orderplace.user.model.UserRole;
import com.orderplace.user.keycloak.KeycloakAdminClient;
import com.orderplace.user.mapper.UserMapper;
import com.orderplace.user.repository.AppUserRepository;
import com.orderplace.user.repository.CustomerRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rule of thumb for every change that touches Keycloak and the database:
 * do the SAFER step last. Deactivate: Keycloak first (a failure leaves the user locked out).
 * Activate: database first, Keycloak second, and a Keycloak failure rolls the database back.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final String DUPLICATE = "DUPLICATE_RESOURCE";

    private final AppUserRepository users;
    private final CustomerRepository customers;
    private final KeycloakAdminClient keycloak;
    private final DealerClient dealers;

    public UserService(AppUserRepository users, CustomerRepository customers, KeycloakAdminClient keycloak,
            DealerClient dealers) {
        this.users = users;
        this.customers = customers;
        this.keycloak = keycloak;
        this.dealers = dealers;
    }

    @Transactional
    public UserCreatedResponse create(CreateUserRequest r) {
        String email = r.email().strip().toLowerCase(Locale.ROOT);
        String firstName = r.firstName().strip();
        String lastName = r.lastName().strip();

        String orgName = checkOrganisationForNewUser(r.role(), r.orgId());
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict(DUPLICATE, "A user with this email already exists");
        }

        UUID keycloakId = keycloak.createUser(email, firstName, lastName, r.role(), r.orgId());
        AppUser saved;
        try {
            keycloak.assignRole(keycloakId, r.role());
            saved = users.saveAndFlush(AppUser.create(keycloakId, email, firstName, lastName,
                    blankToNull(r.phone()), r.role(), r.orgId()));
        } catch (DataIntegrityViolationException e) {
            keycloak.deleteUserQuietly(keycloakId);
            throw ApiException.conflict(DUPLICATE, "A user with this email already exists");
        } catch (RuntimeException e) {
            keycloak.deleteUserQuietly(keycloakId);
            throw e;
        }

        // The account exists at this point. A failed email must not undo it: the Admin can resend.
        boolean emailSent = true;
        try {
            keycloak.sendUpdatePasswordEmail(keycloakId);
        } catch (RuntimeException e) {
            emailSent = false;
            log.warn("Set-password email could not be sent for Keycloak user {}", keycloakId, e);
        }
        return new UserCreatedResponse(UserMapper.toResponse(saved, orgName), emailSent);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest r) {
        AppUser user = find(id);
        if (r.version() != null && !r.version().equals(user.getVersion())) {
            throw ApiException.conflict("VERSION_CONFLICT", "This user was changed by someone else. Reload and try again.");
        }
        String firstName = r.firstName().strip();
        String lastName = r.lastName().strip();
        UUID newOrgId = r.orgId() != null ? r.orgId() : user.getOrgId();
        boolean orgChanged = !newOrgId.equals(user.getOrgId());
        boolean nameChanged = !firstName.equals(user.getFirstName()) || !lastName.equals(user.getLastName());
        if (orgChanged) {
            checkOrganisationForNewUser(user.getRole(), newOrgId);
        }

        user.updateDetails(firstName, lastName, blankToNull(r.phone()));
        if (orgChanged) {
            user.changeOrganisation(newOrgId);
        }
        AppUser saved = users.saveAndFlush(user);
        if (orgChanged || nameChanged) {
            // database first: if Keycloak fails, this transaction rolls back and nothing has changed
            keycloak.updateProfile(user.getKeycloakUserId(), firstName, lastName, user.getRole(), newOrgId);
        }
        return UserMapper.toResponse(saved, orgName(saved));
    }

    @Transactional
    public UserResponse deactivate(UUID id) {
        AppUser user = find(id);
        if (!user.isActive()) {
            return UserMapper.toResponse(user, orgName(user));
        }
        keycloak.setEnabled(user.getKeycloakUserId(), false);   // locks the user out and ends sessions first
        user.deactivate();
        return UserMapper.toResponse(users.saveAndFlush(user), orgName(user));
    }

    @Transactional
    public UserResponse activate(UUID id) {
        AppUser user = find(id);
        if (user.isActive()) {
            return UserMapper.toResponse(user, orgName(user));
        }
        if (user.getRole() == UserRole.CUSTOMER) {
            Customer customer = customers.findById(user.getOrgId()).orElse(null);
            if (customer == null || !customer.isActive()) {
                throw ApiException.unprocessable("ORGANISATION_INACTIVE",
                        "Activate the customer first: its users cannot log in while it is inactive");
            }
        }
        user.activate();
        AppUser saved = users.saveAndFlush(user);
        keycloak.setEnabled(user.getKeycloakUserId(), true);    // failure rolls the database change back
        return UserMapper.toResponse(saved, orgName(saved));
    }

    public void resetPassword(UUID id) {
        AppUser user = find(id);
        if (!user.isActive()) {
            throw ApiException.unprocessable("USER_INACTIVE", "Activate the user before resetting the password");
        }
        keycloak.sendUpdatePasswordEmail(user.getKeycloakUserId());
    }

    public UserResponse get(UUID id) {
        AppUser user = find(id);
        return UserMapper.toResponse(user, orgName(user));
    }

    public PagedResponse<UserResponse> list(String q, UserRole role, Boolean active, Pageable pageable) {
        String pattern = "%" + (q == null ? "" : q.strip().toLowerCase(Locale.ROOT)) + "%";
        Page<AppUser> page;
        if (role == null && active == null) {
            page = users.search(pattern, pageable);
        } else if (active == null) {
            page = users.searchByRole(pattern, role, pageable);
        } else if (role == null) {
            page = users.searchByActive(pattern, active, pageable);
        } else {
            page = users.searchByRoleAndActive(pattern, role, active, pageable);
        }
        Map<UUID, String> names = customerNames(page.getContent());
        List<UserResponse> content = page.getContent().stream()
                .map(u -> UserMapper.toResponse(u, u.getRole() == UserRole.CUSTOMER ? names.get(u.getOrgId()) : null))
                .toList();
        return new PagedResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** Identity of the caller from the JWT. Works for Admins too, who have no row in app_user. */
    public MeResponse me(Jwt jwt) {
        TenantContext ctx = TenantContext.from(jwt);
        String orgName = null;
        if (ctx.isCustomer() && ctx.orgId() != null) {
            orgName = customers.findById(ctx.orgId()).map(Customer::getName).orElse(null);
        }
        return new MeResponse(jwt.getSubject(), jwt.getClaimAsString("preferred_username"), jwt.getClaimAsString("email"),
                jwt.getClaimAsString("given_name"), jwt.getClaimAsString("family_name"), ctx.role(), ctx.orgType(),
                ctx.orgId(), orgName);
    }

    // ---------------------------------------------------------------- helpers

    /** The organisation must exist and be active. Returns the customer name (null for dealers). */
    private String checkOrganisationForNewUser(UserRole role, UUID orgId) {
        if (role == UserRole.CUSTOMER) {
            Customer customer = customers.findById(orgId)
                    .orElseThrow(() -> ApiException.unprocessable("ORGANISATION_NOT_FOUND", "Customer not found"));
            if (!customer.isActive()) {
                throw ApiException.unprocessable("ORGANISATION_INACTIVE", "The customer is inactive");
            }
            return customer.getName();
        }
        DealerInfo dealer = dealers.get(orgId);
        if (!dealer.active()) {
            throw ApiException.unprocessable("ORGANISATION_INACTIVE", "The dealer is inactive");
        }
        return null;
    }

    private String orgName(AppUser user) {
        if (user.getRole() != UserRole.CUSTOMER) {
            return null;
        }
        return customers.findById(user.getOrgId()).map(Customer::getName).orElse(null);
    }

    private Map<UUID, String> customerNames(Collection<AppUser> list) {
        List<UUID> ids = list.stream().filter(u -> u.getRole() == UserRole.CUSTOMER)
                .map(AppUser::getOrgId).distinct().collect(Collectors.toList());
        Map<UUID, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            customers.findAllById(ids).forEach(c -> names.put(c.getId(), c.getName()));
        }
        return names;
    }

    private AppUser find(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
