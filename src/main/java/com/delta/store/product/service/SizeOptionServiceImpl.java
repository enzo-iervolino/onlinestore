package com.delta.store.product.service ;

import com.delta.store.product.model.SizeOption;
import com.delta.store.product.repository.SizeOptionRepository;
import com.delta.store.product.service.SizeOptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class SizeOptionServiceImpl implements SizeOptionService {

    private final SizeOptionRepository repo;

    // Ordine “alpha” tipico abbigliamento
    private static final Map<String, Integer> ALPHA_ORDER = buildAlphaOrder();

    // 2XL, 3XL, 4XL...
    private static final Pattern NXL_PATTERN = Pattern.compile("^(\\d+)XL$");

    // Bambini: mesi e anni (es. 3M, 12M / 2Y, 6Y)
    private static final Pattern KIDS_MONTHS_PATTERN = Pattern.compile("^(\\d{1,2})M$");
    private static final Pattern KIDS_YEARS_PATTERN  = Pattern.compile("^(\\d{1,2})Y$");

    // Scarpe: prefisso consigliato (SH39, SH42.5, EU40, EU41.5)
    private static final Pattern SHOES_PREFIX_PATTERN = Pattern.compile("^(SH|EU)(\\d{1,2})([\\.,]5)?$");

    // Numeriche pure o mezze taglie (39, 40, 41, 42.5)
    private static final Pattern NUMERIC_OR_HALF_PATTERN = Pattern.compile("^(\\d{1,2})([\\.,]5)?$");

    public SizeOptionServiceImpl(SizeOptionRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SizeOption> findAll() {
        return sortSmart(repo.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SizeOption> search(String q) {
        List<SizeOption> list;
        if (q == null || q.isBlank()) {
            list = repo.findAll();
        } else {
            String term = q.trim();
            list = repo.findByNameItContainingIgnoreCaseOrCodeContainingIgnoreCaseOrderByIdAsc(term, term);
        }
        return sortSmart(list);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SizeOption> findById(Long id) {
        return repo.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SizeOption> findByCodeIgnoreCase(String code) {
        if (code == null) return Optional.empty();
        return repo.findByCodeIgnoreCase(code.trim());
    }

    @Override
    public SizeOption save(SizeOption size) {
        return repo.save(size);
    }

    @Override
    public void deleteById(Long id) {
        repo.deleteById(id);
    }

    // ---------------------------
    // ORDINAMENTO “SMART”
    // ---------------------------

    /**
     * Gruppi in ordine:
     * 0) Adult alpha + TU (XXS..XL..)
     * 1) Kids (mesi/anni)
     * 2) Abbigliamento numeriche (34..60)
     * 3) Scarpe (SH39, EU40, ... oppure heuristica mezze/odd)
     * 4) Altro
     */
    private List<SizeOption> sortSmart(List<SizeOption> list) {
        if (list == null || list.isEmpty()) return Collections.emptyList();

        List<SizeOption> copy = new ArrayList<>(list);
        copy.sort(Comparator
                .comparingInt((SizeOption s) -> key(s).group)
                .thenComparingInt(s -> key(s).order)
                .thenComparing(s -> safeUpper(s.getCode()))
        );
        return copy;
    }

    private SortKey key(SizeOption s) {
        String code = safeUpper(s != null ? s.getCode() : null);

        // 0) TU sempre prima
        if ("TU".equals(code)) {
            return new SortKey(0, 0);
        }

        // 0) alpha standard (XXS..XXXL, XL, XXL...)
        Integer alpha = ALPHA_ORDER.get(code);
        if (alpha != null) {
            return new SortKey(0, alpha);
        }

        // 0) NXL tipo 2XL, 3XL, 4XL ...
        Matcher nxl = NXL_PATTERN.matcher(code);
        if (nxl.matches()) {
            int n = Integer.parseInt(nxl.group(1));
            int base;
            if (n == 2) base = ALPHA_ORDER.getOrDefault("XXL", 70);
            else if (n == 3) base = ALPHA_ORDER.getOrDefault("XXXL", 80);
            else base = ALPHA_ORDER.getOrDefault("XXXL", 80) + (n - 3);
            return new SortKey(0, base);
        }

        // 1) Kids mesi (3M, 6M, 12M...)
        Matcher km = KIDS_MONTHS_PATTERN.matcher(code);
        if (km.matches()) {
            int months = Integer.parseInt(km.group(1));
            return new SortKey(1, months); // 3, 6, 9, 12, 18, 24...
        }

        // 1) Kids anni (2Y, 3Y, 4Y...) - li metto dopo i mesi
        Matcher ky = KIDS_YEARS_PATTERN.matcher(code);
        if (ky.matches()) {
            int years = Integer.parseInt(ky.group(1));
            return new SortKey(1, 1000 + years); // anni dopo i mesi
        }

        // 3) Scarpe con prefisso (consigliato)
        Matcher sp = SHOES_PREFIX_PATTERN.matcher(code);
        if (sp.matches()) {
            double v = parseHalfNumber(sp.group(2), sp.group(3));
            return new SortKey(3, toSortableInt(v));
        }

        // Numeriche (o mezze taglie) senza prefisso: qui decidiamo se "clothing" o "shoes"
        Matcher num = NUMERIC_OR_HALF_PATTERN.matcher(code);
        if (num.matches()) {
            double v = parseHalfNumber(num.group(1), num.group(2));
            int iv = (int) Math.floor(v);

            boolean hasHalf = (num.group(2) != null);
            boolean isOdd = (iv % 2 != 0);

            // Heuristica: mezze taglie o dispari => scarpe (se stai usando codici puri senza prefisso)
            if (hasHalf || isOdd) {
                return new SortKey(3, toSortableInt(v));
            }

            // altrimenti numeriche “abbigliamento”
            return new SortKey(2, iv);
        }

        // 4) fallback
        return new SortKey(4, 9999);
    }

    private static double parseHalfNumber(String intPart, String halfPart) {
        double v = Double.parseDouble(intPart);
        if (halfPart != null) {
            v += 0.5;
        }
        return v;
    }

    // trasforma 42.5 in 4250, 42 in 4200 per ordinare bene come int
    private static int toSortableInt(double v) {
        return (int) Math.round(v * 100);
    }

    private static String safeUpper(String s) {
        return (s == null) ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private static Map<String, Integer> buildAlphaOrder() {
        Map<String, Integer> map = new HashMap<>();
        map.put("XXS", 10);
        map.put("XS",  20);
        map.put("S",   30);
        map.put("M",   40);
        map.put("L",   50);
        map.put("XL",  60);
        map.put("XXL", 70);
        map.put("XXXL",80);
        map.put("4XL", 81);
        map.put("5XL", 82);
        map.put("6XL", 83);
        return map;
    }

    private static class SortKey {
        final int group;
        final int order;

        SortKey(int group, int order) {
            this.group = group;
            this.order = order;
        }
    }
}