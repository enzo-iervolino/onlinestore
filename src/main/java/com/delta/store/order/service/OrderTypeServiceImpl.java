package com.delta.store.order.service;

import com.delta.store.order.model.OrderType;
import com.delta.store.order.repository.OrderTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class OrderTypeServiceImpl implements OrderTypeService {

    private final OrderTypeRepository repo;

    public OrderTypeServiceImpl(OrderTypeRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderType> findAll() {
        return repo.findAllByOrderByDescriAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderType> search(String q) {
        if (q == null || q.isBlank()) {
            return findAll();
        }
        String term = q.trim();
        return repo.findByDescriContainingIgnoreCaseOrCodeContainingIgnoreCaseOrderByDescriAsc(term, term);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderType> findById(Long id) {
        return repo.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderType> findByCodeIgnoreCase(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return repo.findByCodeIgnoreCase(code.trim());
    }

    @Override
    public OrderType save(OrderType orderType) {
        return repo.save(orderType);
    }

    @Override
    public void deleteById(Long id) {
        repo.deleteById(id);
    }
}