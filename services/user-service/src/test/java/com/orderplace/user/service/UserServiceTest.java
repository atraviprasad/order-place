package com.orderplace.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orderplace.common.web.ApiException;
import com.orderplace.user.api.dto.CreateUserRequest;
import com.orderplace.user.api.dto.UpdateUserRequest;
import com.orderplace.user.api.dto.UserCreatedResponse;
import com.orderplace.user.client.DealerClient;
import com.orderplace.user.client.DealerInfo;
import com.orderplace.user.model.AppUser;
import com.orderplace.user.model.Customer;
import com.orderplace.user.model.UserRole;
import com.orderplace.user.keycloak.KeycloakAdminClient;
import com.orderplace.user.repository.AppUserRepository;
import com.orderplace.user.repository.CustomerRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock AppUserRepository users;
    @Mock CustomerRepository customers;
    @Mock KeycloakAdminClient keycloak;
    @Mock DealerClient dealers;
    @InjectMocks UserService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID keycloakId = UUID.randomUUID();

    private static Customer customer(boolean active) {
        Customer c = Customer.create("CUS-001", "Demo Customer", "Rahul", "c@x.com", "+91", "Bill", "Deliver");
        if (!active) c.deactivate();
        return c;
    }

    private CreateUserRequest customerUserRequest() {
        return new CreateUserRequest(" Rahul@X.com ", " Rahul ", "Sharma", "  ", UserRole.CUSTOMER, orgId);
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", "down");
    }

    private static void assertCode(Throwable t, String code) {
        assertThat(t).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    void create_customerUser_createsKeycloakAccountThenProfile() {
        when(customers.findById(orgId)).thenReturn(Optional.of(customer(true)));
        when(keycloak.createUser("rahul@x.com", "Rahul", "Sharma", UserRole.CUSTOMER, orgId)).thenReturn(keycloakId);
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        UserCreatedResponse response = service.create(customerUserRequest());

        assertThat(response.setPasswordEmailSent()).isTrue();
        assertThat(response.user().email()).isEqualTo("rahul@x.com");
        assertThat(response.user().orgName()).isEqualTo("Demo Customer");
        assertThat(response.user().phone()).isNull();
        verify(keycloak).assignRole(keycloakId, UserRole.CUSTOMER);
        verify(keycloak).sendUpdatePasswordEmail(keycloakId);
    }

    @Test
    void create_dealerUser_checksDealerWithDealerService() {
        when(dealers.get(orgId)).thenReturn(new DealerInfo(orgId, "Dealer One", true));
        when(keycloak.createUser("d@x.com", "Dee", "Ler", UserRole.DEALER, orgId)).thenReturn(keycloakId);
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        UserCreatedResponse response = service.create(new CreateUserRequest("d@x.com", "Dee", "Ler", null, UserRole.DEALER, orgId));

        assertThat(response.user().role()).isEqualTo(UserRole.DEALER);
        assertThat(response.user().orgName()).isNull();
    }

    @Test
    void create_inactiveCustomer_isRejectedBeforeTouchingKeycloak() {
        when(customers.findById(orgId)).thenReturn(Optional.of(customer(false)));

        assertThatThrownBy(() -> service.create(customerUserRequest())).satisfies(t -> assertCode(t, "ORGANISATION_INACTIVE"));
        verifyNoInteractions(keycloak);
    }

    @Test
    void create_inactiveDealer_isRejected() {
        when(dealers.get(orgId)).thenReturn(new DealerInfo(orgId, "Dealer One", false));

        assertThatThrownBy(() -> service.create(new CreateUserRequest("d@x.com", "Dee", "Ler", null, UserRole.DEALER, orgId)))
                .satisfies(t -> assertCode(t, "ORGANISATION_INACTIVE"));
        verifyNoInteractions(keycloak);
    }

    @Test
    void create_duplicateEmail_isRejectedBeforeTouchingKeycloak() {
        when(customers.findById(orgId)).thenReturn(Optional.of(customer(true)));
        when(users.existsByEmailIgnoreCase("rahul@x.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(customerUserRequest())).satisfies(t -> assertCode(t, "DUPLICATE_RESOURCE"));
        verifyNoInteractions(keycloak);
    }

    @Test
    void create_roleAssignmentFails_removesTheKeycloakAccount() {
        when(customers.findById(orgId)).thenReturn(Optional.of(customer(true)));
        when(keycloak.createUser("rahul@x.com", "Rahul", "Sharma", UserRole.CUSTOMER, orgId)).thenReturn(keycloakId);
        doThrow(unavailable()).when(keycloak).assignRole(keycloakId, UserRole.CUSTOMER);

        assertThatThrownBy(() -> service.create(customerUserRequest())).satisfies(t -> assertCode(t, "DEPENDENCY_UNAVAILABLE"));
        verify(keycloak).deleteUserQuietly(keycloakId);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void create_databaseRejectsProfile_removesTheKeycloakAccount() {
        when(customers.findById(orgId)).thenReturn(Optional.of(customer(true)));
        when(keycloak.createUser("rahul@x.com", "Rahul", "Sharma", UserRole.CUSTOMER, orgId)).thenReturn(keycloakId);
        when(users.saveAndFlush(any(AppUser.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.create(customerUserRequest())).satisfies(t -> assertCode(t, "DUPLICATE_RESOURCE"));
        verify(keycloak).deleteUserQuietly(keycloakId);
    }

    @Test
    void create_emailFails_userIsStillCreatedAndFlagIsFalse() {
        when(customers.findById(orgId)).thenReturn(Optional.of(customer(true)));
        when(keycloak.createUser("rahul@x.com", "Rahul", "Sharma", UserRole.CUSTOMER, orgId)).thenReturn(keycloakId);
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(unavailable()).when(keycloak).sendUpdatePasswordEmail(keycloakId);

        UserCreatedResponse response = service.create(customerUserRequest());

        assertThat(response.setPasswordEmailSent()).isFalse();
        assertThat(response.user().email()).isEqualTo("rahul@x.com");
        verify(keycloak, never()).deleteUserQuietly(any());
    }

    @Test
    void update_staleVersion_isRejected() {
        UUID id = UUID.randomUUID();
        AppUser user = AppUser.create(keycloakId, "a@x.com", "A", "B", null, UserRole.CUSTOMER, orgId);
        org.springframework.test.util.ReflectionTestUtils.setField(user, "version", 3L);
        when(users.findById(id)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.update(id, new UpdateUserRequest("A", "B", null, null, 2L)))
                .satisfies(t -> assertCode(t, "VERSION_CONFLICT"));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void update_onlyPhoneChanged_doesNotCallKeycloak() {
        UUID id = UUID.randomUUID();
        AppUser user = AppUser.create(keycloakId, "a@x.com", "A", "B", null, UserRole.CUSTOMER, orgId);
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(id, new UpdateUserRequest("A", "B", "+91-123", null, null));

        verifyNoInteractions(keycloak);
        assertThat(user.getPhone()).isEqualTo("+91-123");
    }

    @Test
    void update_moveToAnotherCustomer_updatesKeycloakAttributes() {
        UUID id = UUID.randomUUID();
        UUID newOrg = UUID.randomUUID();
        AppUser user = AppUser.create(keycloakId, "a@x.com", "A", "B", null, UserRole.CUSTOMER, orgId);
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(customers.findById(newOrg)).thenReturn(Optional.of(customer(true)));
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(id, new UpdateUserRequest("A", "B", null, newOrg, null));

        assertThat(user.getOrgId()).isEqualTo(newOrg);
        verify(keycloak).updateProfile(keycloakId, "A", "B", UserRole.CUSTOMER, newOrg);
    }

    @Test
    void deactivate_disablesKeycloakBeforeSavingTheDatabase() {
        UUID id = UUID.randomUUID();
        AppUser user = AppUser.create(keycloakId, "a@x.com", "A", "B", null, UserRole.CUSTOMER, orgId);
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deactivate(id);

        InOrder order = inOrder(keycloak, users);
        order.verify(keycloak).setEnabled(keycloakId, false);
        order.verify(users).saveAndFlush(user);
        assertThat(user.isActive()).isFalse();
    }

    @Test
    void deactivate_keycloakFails_userStaysActiveInDatabase() {
        UUID id = UUID.randomUUID();
        AppUser user = AppUser.create(keycloakId, "a@x.com", "A", "B", null, UserRole.CUSTOMER, orgId);
        when(users.findById(id)).thenReturn(Optional.of(user));
        doThrow(unavailable()).when(keycloak).setEnabled(keycloakId, false);

        assertThatThrownBy(() -> service.deactivate(id)).satisfies(t -> assertCode(t, "DEPENDENCY_UNAVAILABLE"));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void activate_whenCustomerIsInactive_isRejected() {
        UUID id = UUID.randomUUID();
        AppUser user = AppUser.create(keycloakId, "a@x.com", "A", "B", null, UserRole.CUSTOMER, orgId);
        user.deactivate();
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(customers.findById(orgId)).thenReturn(Optional.of(customer(false)));

        assertThatThrownBy(() -> service.activate(id)).satisfies(t -> assertCode(t, "ORGANISATION_INACTIVE"));
        verifyNoInteractions(keycloak);
    }

    @Test
    void activate_enablesKeycloakAfterTheDatabaseChange() {
        UUID id = UUID.randomUUID();
        AppUser user = AppUser.create(keycloakId, "a@x.com", "A", "B", null, UserRole.CUSTOMER, orgId);
        user.deactivate();
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(customers.findById(orgId)).thenReturn(Optional.of(customer(true)));
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        service.activate(id);

        InOrder order = inOrder(users, keycloak);
        order.verify(users).saveAndFlush(user);
        order.verify(keycloak).setEnabled(keycloakId, true);
        assertThat(user.isActive()).isTrue();
    }

    @Test
    void resetPassword_forInactiveUser_isRejected() {
        UUID id = UUID.randomUUID();
        AppUser user = AppUser.create(keycloakId, "a@x.com", "A", "B", null, UserRole.CUSTOMER, orgId);
        user.deactivate();
        when(users.findById(id)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.resetPassword(id)).satisfies(t -> assertCode(t, "USER_INACTIVE"));
        verifyNoInteractions(keycloak);
    }

    @Test
    void resetPassword_forActiveUser_sendsEmail() {
        UUID id = UUID.randomUUID();
        AppUser user = AppUser.create(keycloakId, "a@x.com", "A", "B", null, UserRole.DEALER, orgId);
        when(users.findById(id)).thenReturn(Optional.of(user));

        service.resetPassword(id);

        verify(keycloak).sendUpdatePasswordEmail(keycloakId);
    }

    @Test
    void get_unknownId_isNotFound() {
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).satisfies(t -> assertCode(t, "NOT_FOUND"));
    }
}
