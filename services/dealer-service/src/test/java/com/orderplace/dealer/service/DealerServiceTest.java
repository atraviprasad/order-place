package com.orderplace.dealer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderplace.common.web.ApiException;
import com.orderplace.dealer.api.dto.CreateDealerRequest;
import com.orderplace.dealer.api.dto.DealerResponse;
import com.orderplace.dealer.api.dto.UpdateDealerRequest;
import com.orderplace.dealer.model.Dealer;
import com.orderplace.dealer.repository.DealerRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DealerServiceTest {

    @Mock DealerRepository repository;
    @InjectMocks DealerService service;

    private static CreateDealerRequest createRequest(String code) {
        return new CreateDealerRequest(code, " Metro Interiors ", "Anita", "Sales@Metro.com", "+91-90000", "Mumbai", " ");
    }

    private static Dealer existingDealer(long version) {
        Dealer d = Dealer.create("DLR-010", "Metro Interiors", "Anita", "sales@metro.com", "+91-90000", "Mumbai", null);
        ReflectionTestUtils.setField(d, "version", version);
        return d;
    }

    @Test
    void create_normalisesValuesAndSaves() {
        when(repository.existsByDealerCodeIgnoreCase("DLR-010")).thenReturn(false);
        when(repository.existsByNameIgnoreCaseAndEmailIgnoreCase("Metro Interiors", "sales@metro.com")).thenReturn(false);
        when(repository.saveAndFlush(any(Dealer.class))).thenAnswer(inv -> inv.getArgument(0));

        DealerResponse response = service.create(createRequest(" dlr-010 "));

        assertThat(response.dealerCode()).isEqualTo("DLR-010");
        assertThat(response.name()).isEqualTo("Metro Interiors");
        assertThat(response.email()).isEqualTo("sales@metro.com");
        assertThat(response.taxRegistrationNo()).isNull();
        assertThat(response.active()).isTrue();
    }

    @Test
    void create_duplicateCode_isRejected() {
        when(repository.existsByDealerCodeIgnoreCase("DLR-010")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest("dlr-010")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("DUPLICATE_RESOURCE"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void create_duplicateNameAndEmail_isRejected() {
        when(repository.existsByDealerCodeIgnoreCase("DLR-010")).thenReturn(false);
        when(repository.existsByNameIgnoreCaseAndEmailIgnoreCase("Metro Interiors", "sales@metro.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest("DLR-010")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("DUPLICATE_RESOURCE"));
    }

    @Test
    void update_withStaleVersion_isRejected() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(existingDealer(3L)));
        UpdateDealerRequest request = new UpdateDealerRequest("New Name", "Anita", "a@b.com", "123", "Addr", null, 2L);

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("VERSION_CONFLICT"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void update_withoutVersion_updatesDetailsButNotCode() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(existingDealer(1L)));
        when(repository.existsByNameIgnoreCaseAndEmailIgnoreCaseAndIdNot("New Name", "a@b.com", id)).thenReturn(false);
        when(repository.saveAndFlush(any(Dealer.class))).thenAnswer(inv -> inv.getArgument(0));
        UpdateDealerRequest request = new UpdateDealerRequest("New Name", "Anita", "A@B.com", "123", "Addr", "GST1", null);

        DealerResponse response = service.update(id, request);

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.email()).isEqualTo("a@b.com");
        assertThat(response.dealerCode()).isEqualTo("DLR-010");
    }

    @Test
    void deactivateAndActivate_toggleFlag() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(existingDealer(1L)));
        when(repository.saveAndFlush(any(Dealer.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.deactivate(id).active()).isFalse();
        assertThat(service.activate(id).active()).isTrue();
    }

    @Test
    void get_unknownId_isNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_FOUND"));
    }
}
