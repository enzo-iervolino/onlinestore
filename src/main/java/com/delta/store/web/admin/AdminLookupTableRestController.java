package com.delta.store.web.admin;

import com.delta.store.product.model.ImageLookupEntity;
import com.delta.store.product.repository.ProductBrandRepository;
import com.delta.store.product.repository.ProductFamilyRepository;
import com.delta.store.product.repository.ProductGroupRepository;
import com.delta.store.product.repository.ProductSubfamilyRepository;
import com.delta.store.product.repository.ProductSubgroupRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminLookupTableRestController {

    private final ProductGroupRepository groupRepository;
    private final ProductSubgroupRepository subgroupRepository;
    private final ProductBrandRepository brandRepository;
    private final ProductFamilyRepository familyRepository;
    private final ProductSubfamilyRepository subfamilyRepository;

    public AdminLookupTableRestController(ProductGroupRepository groupRepository,
                                          ProductSubgroupRepository subgroupRepository,
                                          ProductBrandRepository brandRepository,
                                          ProductFamilyRepository familyRepository,
                                          ProductSubfamilyRepository subfamilyRepository) {
        this.groupRepository = groupRepository;
        this.subgroupRepository = subgroupRepository;
        this.brandRepository = brandRepository;
        this.familyRepository = familyRepository;
        this.subfamilyRepository = subfamilyRepository;
    }

    public record LookupDTO(Long id, String code, String descri, String imageUrl) {
    }

    @GetMapping("/groups")
    public List<LookupDTO> groups() {
        return toDto(groupRepository.findAllByOrderByCodeAsc());
    }

    @GetMapping("/subgroups")
    public List<LookupDTO> subgroups() {
        return toDto(subgroupRepository.findAllByOrderByCodeAsc());
    }

    @GetMapping("/brands")
    public List<LookupDTO> brands() {
        return toDto(brandRepository.findAllByOrderByCodeAsc());
    }

    @GetMapping("/families")
    public List<LookupDTO> families() {
        return toDto(familyRepository.findAllByOrderByCodeAsc());
    }

    @GetMapping("/subfamilies")
    public List<LookupDTO> subfamilies() {
        return toDto(subfamilyRepository.findAllByOrderByCodeAsc());
    }

    private List<LookupDTO> toDto(List<? extends ImageLookupEntity> items) {
        return items.stream()
                .map(item -> new LookupDTO(item.getId(), item.getCode(), item.getDescri(), item.getImageUrl()))
                .toList();
    }
}
