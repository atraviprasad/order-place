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
import com.orderplace.user.api.dto.CreateCustomerRequest;
import com.orderplace.user.api.dto.CustomerResponse;
import com.orderplace.user.model.AppUser;
import com.orderplace.user.model.Customer;
import com.orderplace.user.model.UserRole;
import com.orderplace.user.keycloak.KeycloakAdminClient;
import com.orderplace.user.repository.AppUserRepository;
import com.orderplace.user.repository.CustomerRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock CustomerRepository customers;
    @Mock AppUserRepository users;
    @Mock KeycloakAdminClient keycloak;
    @InjectMocks CustomerService service;

    private final UUID customerId = UUID.randomUUID();
    private final UUID kc1 = UUID.randomUUID();
    private final UUID kc2 = UUID.randomUUID();

    private static Customer customer() {
        return Customer.create("CUS-001", "Demo Customer", "Rahul", "c@x.com", "+91", "Bill", "Deliver");
    }

    private AppUser userOf(UUID keycloakId, String email) {
        return AppUser.create(keycloakId, email, "F", "L", null, UserRole.CUSTOMER, customerId);
    }

    private static CreateCustomerRequest request(String code) {
        return new CreateCustomerRequest(code, " Demo Customer ", "Rahul", "C@X.com", "+91", "Bill", "Deliver");
    }

    @Test
    void create_normalisesCodeAndEmail() {
        when(customers.saveAndFlush(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse response = service.create(request(" cus-001 "));

        assertThat(response.customerCode()).isEqualTo("CUS-001");
        assertThat(response.email()).isEqualTo("c@x.com");
        assertThat(response.name()).isEqualTo("Demo Customer");
        assertThat(response.active()).isTrue();
    }

    @Test
    void create_duplicateCode_isRejected() {
        when(customers.existsByCustomerCodeIgnoreCase("CUS-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("cus-001")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("DUPLICATE_RESOURCE"));
        verify(customers, never()).saveAndFlush(any());
    }

    @Test
    void deactivate_disablesEveryActiveUserInKeycloakThenSavesCustomer() {
        Customer c = customer();
        when(customers.findById(customerId)).thenReturn(Optional.of(c));
        when(users.findByOrgIdAndActiveTrue(customerId)).thenReturn(List.of(userOf(kc1, "a@x.com"), userOf(kc2, "b@x.com")));
        when(customers.saveAndFlush(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deactivate(customerId);

        InOrder order = inOrder(keycloak, customers);
        order.verify(keycloak).setEnabled(kc1, false);
        order.verify(keycloak).setEnabled(kc2, false);
        order.verify(customers).saveAndFlush(c);
        assertThat(c.isActive()).isFalse();
    }

    @Test
    void deactivate_alreadyInactive_doesNothing() {
        Customer c = customer();
        c.deactivate();
        when(customers.findById(customerId)).thenReturn(Optional.of(c));

        service.deactivate(customerId);

        verifyNoInteractions(keycloak);
        verify(customers, never()).saveAndFlush(any());
    }

    @Test
    void activate_reEnablesUsers() {
        Customer c = customer();
        c.deactivate();
        when(customers.findById(customerId)).thenReturn(Optional.of(c));
        when(users.findByOrgIdAndActiveTrue(customerId)).thenReturn(List.of(userOf(kc1, "a@x.com")));
        when(customers.saveAndFlush(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        service.activate(customerId);

        verify(keycloak).setEnabled(kc1, true);
        assertThat(c.isActive()).isTrue();
    }

    @Test
    void activate_keycloakFailsHalfWay_undoesTheUsersAlreadyEnabled() {
        Customer c = customer();
        c.deactivate();
        when(customers.findById(customerId)).thenReturn(Optional.of(c));
        when(users.findByOrgIdAndActiveTrue(customerId)).thenReturn(List.of(userOf(kc1, "a@x.com"), userOf(kc2, "b@x.com")));
        when(customers.saveAndFlush(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", "down"))
                .when(keycloak).setEnabled(kc2, true);

        assertThatThrownBy(() -> service.activate(customerId))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("DEPENDENCY_UNAVAILABLE"));

        verify(keycloak).setEnabled(kc1, true);
        verify(keycloak).setEnabled(kc1, false);   // compensation: user 1 is locked out again
    }

    @Test
    void get_unknownId_isNotFound() {
        when(customers.findById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(customerId))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_FOUND"));
    }
}
