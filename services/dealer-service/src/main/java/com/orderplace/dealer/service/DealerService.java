package com.orderplace.dealer.service;

import com.orderplace.common.web.ApiException;
import com.orderplace.common.web.PagedResponse;
import com.orderplace.dealer.api.dto.CreateDealerRequest;
import com.orderplace.dealer.api.dto.DealerResponse;
import com.orderplace.dealer.api.dto.DealerSummary;
import com.orderplace.dealer.api.dto.UpdateDealerRequest;
import com.orderplace.dealer.model.Dealer;
import com.orderplace.dealer.mapper.DealerMapper;
import com.orderplace.dealer.repository.DealerRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DealerService {

    private static final String DUPLICATE = "DUPLICATE_RESOURCE";

    private final DealerRepository repository;

    public DealerService(DealerRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public DealerResponse create(CreateDealerRequest r) {
        String code = r.dealerCode().strip().toUpperCase(Locale.ROOT);
        String name = r.name().strip();
        String email = r.email().strip().toLowerCase(Locale.ROOT);

        if (repository.existsByDealerCodeIgnoreCase(code)) {
            throw ApiException.conflict(DUPLICATE, "Dealer code already exists: " + code);
        }
        if (repository.existsByNameIgnoreCaseAndEmailIgnoreCase(name, email)) {
            throw ApiException.conflict(DUPLICATE, "A dealer with this name and email already exists");
        }
        Dealer dealer = Dealer.create(code, name, r.contactPerson().strip(), email, r.phone().strip(),
                r.address().strip(), blankToNull(r.taxRegistrationNo()));
        return DealerMapper.toResponse(saveAndFlush(dealer));
    }

    @Transactional
    public DealerResponse update(UUID id, UpdateDealerRequest r) {
        Dealer dealer = find(id);
        if (r.version() != null && !r.version().equals(dealer.getVersion())) {
            throw ApiException.conflict("VERSION_CONFLICT", "This dealer was changed by someone else. Reload and try again.");
        }
        String name = r.name().strip();
        String email = r.email().strip().toLowerCase(Locale.ROOT);
        if (repository.existsByNameIgnoreCaseAndEmailIgnoreCaseAndIdNot(name, email, id)) {
            throw ApiException.conflict(DUPLICATE, "A dealer with this name and email already exists");
        }
        dealer.updateDetails(name, r.contactPerson().strip(), email, r.phone().strip(), r.address().strip(),
                blankToNull(r.taxRegistrationNo()));
        return DealerMapper.toResponse(saveAndFlush(dealer));
    }

    @Transactional
    public DealerResponse deactivate(UUID id) {
        Dealer dealer = find(id);
        dealer.deactivate();
        return DealerMapper.toResponse(saveAndFlush(dealer));
    }

    @Transactional
    public DealerResponse activate(UUID id) {
        Dealer dealer = find(id);
        dealer.activate();
        return DealerMapper.toResponse(saveAndFlush(dealer));
    }

    public DealerResponse get(UUID id) {
        return DealerMapper.toResponse(find(id));
    }

    public PagedResponse<DealerResponse> list(String q, Boolean active, Pageable pageable) {
        String pattern = "%" + (q == null ? "" : q.strip().toLowerCase(Locale.ROOT)) + "%";
        Page<Dealer> page = active == null
                ? repository.search(pattern, pageable)
                : repository.searchByActive(pattern, active, pageable);
        return new PagedResponse<>(page.getContent().stream().map(DealerMapper::toResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** Customer dropdown: only active dealers, sorted by name (FR-ADM-14). */
    public List<DealerSummary> listActive() {
        return repository.findByActiveTrueOrderByNameAsc().stream().map(DealerMapper::toSummary).toList();
    }

    private Dealer find(UUID id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Dealer not found"));
    }

    private Dealer saveAndFlush(Dealer dealer) {
        try {
            return repository.saveAndFlush(dealer);
        } catch (DataIntegrityViolationException e) {
            // two requests created the same dealer at the same moment: the database constraint wins
            throw ApiException.conflict(DUPLICATE, "A dealer with this code or name and email already exists");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
