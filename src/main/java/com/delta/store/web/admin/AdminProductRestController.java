package com.delta.store.web.admin;

import com.delta.store.product.model.Product;
import com.delta.store.product.repository.ProductRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductRestController {

    private final ProductRepository productRepository;

    public AdminProductRestController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // DTO "piatto" per tabella admin
    public record AdminProductRowDTO(
            Long id,
            String codpro,
            String name,
            String description,
            BigDecimal price,
            String imageUrl,
            Long categoryId,
            String categoryName,
            Long brandId,
            Long groupId,
            Long subgroupId,
            UUID publicId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    // Risposta server-side per DataTables
    public record DataTablesResponse<T>(
            int draw,
            long recordsTotal,
            long recordsFiltered,
            List<T> data
    ) {}

    // =========================
    // 1) LISTA "semplice" (RAW JSON)
    // =========================
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<AdminProductRowDTO> listProducts() {
        List<Product> products = productRepository.findAllByOrderByIdDesc();
        return products.stream().map(this::toRow).toList();
    }

    // ==========================================
    // 1b) LISTA "PRETTY" (JSON indentato MANUALE)
    // URL: /api/admin/products/pretty
    // ==========================================
    @GetMapping(value = "/pretty", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> listProductsPretty() {
        try {
            List<AdminProductRowDTO> data = listProducts();
            String pretty = toPrettyJson(data);
            return ResponseEntity.ok(pretty);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Errore pretty JSON: " + e.getMessage());
        }
    }

    // ==========================================
    // 1c) VIEWER HTML (browser-friendly)
    // URL: /api/admin/products/view
    // ==========================================
    @GetMapping(value = "/view", produces = MediaType.TEXT_HTML_VALUE)
    public String viewProductsJson() {
        return """
                <!doctype html>
                <html lang="it">
                <head>
                  <meta charset="utf-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>Products JSON Viewer</title>
                  <style>
                    body { font-family: ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, Arial; margin: 16px; }
                    .bar { display:flex; gap:8px; align-items:center; margin-bottom:12px; flex-wrap:wrap; }
                    button { cursor:pointer; padding:8px 10px; border:1px solid #ccc; background:#fff; border-radius:10px; }
                    input { padding:8px 10px; border:1px solid #ccc; border-radius:10px; min-width:260px; }
                    pre { background:#f6f8fa; border:1px solid #e5e7eb; border-radius:12px; padding:12px; overflow:auto; }
                    .muted { color:#6b7280; font-size: 12px; }
                  </style>
                </head>
                <body>
                  <div class="bar">
                    <button id="btnLoad">Carica</button>
                    <button id="btnPretty">Pretty</button>
                    <button id="btnRaw">Raw</button>
                    <input id="filter" placeholder="Filtra (codpro / name / categoryName)..." />
                    <span class="muted" id="status"></span>
                  </div>
                
                  <pre id="out">(clicca “Carica”)</pre>
                
                  <script>
                    const out = document.getElementById('out');
                    const status = document.getElementById('status');
                    const filter = document.getElementById('filter');
                    let lastJson = null;
                    let lastPretty = null;
                
                    function renderText(txt){
                      out.textContent = txt || '(nessun dato)';
                    }
                
                    function applyFilter(){
                      if(!lastJson){ return; }
                      const q = (filter.value || '').trim().toLowerCase();
                      if(!q){ renderText(lastPretty); status.textContent = ''; return; }
                      const filtered = lastJson.filter(p => {
                        const a = (p.codpro || '').toLowerCase();
                        const b = (p.name || '').toLowerCase();
                        const c = (p.categoryName || '').toLowerCase();
                        return a.includes(q) || b.includes(q) || c.includes(q);
                      });
                      renderText(JSON.stringify(filtered, null, 2));
                      status.textContent = `Filtrati: ${filtered.length} / ${lastJson.length}`;
                    }
                
                    document.getElementById('btnLoad').addEventListener('click', async () => {
                      status.textContent = 'Caricamento...';
                      try{
                        // prendiamo JSON standard
                        const res = await fetch('/api/admin/products', { headers: { 'Accept': 'application/json' }});
                        if(!res.ok) throw new Error('HTTP ' + res.status);
                        lastJson = await res.json();
                        lastPretty = JSON.stringify(lastJson, null, 2);
                        renderText(lastPretty);
                        status.textContent = `Totale: ${lastJson.length}`;
                      }catch(e){
                        renderText('Errore: ' + e.message);
                        status.textContent = '';
                      }
                    });
                
                    document.getElementById('btnPretty').addEventListener('click', () => {
                      renderText(lastPretty);
                    });
                
                    document.getElementById('btnRaw').addEventListener('click', () => {
                      if(!lastJson){ renderText('(nessun dato)'); return; }
                      renderText(JSON.stringify(lastJson));
                    });
                
                    filter.addEventListener('input', applyFilter);
                  </script>
                </body>
                </html>
                """;
    }

    // ==================================
    // 2) DETTAGLIO: /{id}
    // ==================================
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdminProductRowDTO> getById(@PathVariable Long id) {
        return productRepository.findById(id)
                .map(p -> ResponseEntity.ok(toRow(p)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ==========================================================
    // 3) DataTables server-side: /datatables
    // ==========================================================
    @GetMapping(value = "/datatables", produces = MediaType.APPLICATION_JSON_VALUE)
    public DataTablesResponse<AdminProductRowDTO> datatables(@RequestParam MultiValueMap<String, String> params) {

        int draw = parseInt(params.getFirst("draw"), 0);
        int start = parseInt(params.getFirst("start"), 0);
        int length = parseInt(params.getFirst("length"), 10);

        int page = (length <= 0) ? 0 : (start / length);

        String searchValue = first(params, "search[value]").orElse("").trim();

        String orderColIndexStr = first(params, "order[0][column]").orElse("0");
        String orderDir = first(params, "order[0][dir]").orElse("desc");

        int orderColIndex = parseInt(orderColIndexStr, 0);

        String columnDataParam = "columns[" + orderColIndex + "][data]";
        String columnName = first(params, columnDataParam).orElse("id");

        Sort sort = buildSafeSort(columnName, orderDir);
        Pageable pageable = PageRequest.of(page, length, sort);

        long recordsTotal = productRepository.count();

        Specification<Product> spec = buildSearchSpec(searchValue);

        Page<Product> pageResult = productRepository.findAll(spec, pageable);
        long recordsFiltered = pageResult.getTotalElements();

        List<AdminProductRowDTO> data = pageResult.getContent().stream()
                .map(this::toRow)
                .collect(Collectors.toList());

        return new DataTablesResponse<>(draw, recordsTotal, recordsFiltered, data);
    }

    // =========================
    // DELETE
    // =========================
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        if (!productRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        productRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // =========================
    // Helpers
    // =========================
    private AdminProductRowDTO toRow(Product p) {
        return new AdminProductRowDTO(
                p.getId(),
                p.getCodpro(),
                p.getName(),
                p.getDescription(),
                p.getPrice(),
                p.getImage(),
                p.getCategory() != null ? p.getCategory().getId() : null,
                p.getCategory() != null ? p.getCategory().getName() : null,
                p.getBrandId(),
                p.getGroupId(),
                p.getSubgroupId(),
                p.getPublicId(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    private Specification<Product> buildSearchSpec(String search) {
        if (search == null || search.isBlank()) {
            return (root, query, cb) -> cb.conjunction();
        }

        final String like = "%" + search.toLowerCase(Locale.ROOT) + "%";

        return (root, query, cb) -> {
            Join<Object, Object> categoryJoin = root.join("category", JoinType.LEFT);

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.like(cb.lower(root.get("codpro")), like));
            predicates.add(cb.like(cb.lower(root.get("name")), like));
            predicates.add(cb.like(cb.lower(categoryJoin.get("name")), like));

            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }

    private Sort buildSafeSort(String columnName, String dir) {
        boolean asc = "asc".equalsIgnoreCase(dir);

        String mappedProperty = switch (columnName) {
            case "id" -> "id";
            case "codpro" -> "codpro";
            case "name" -> "name";
            case "price" -> "price";
            case "brandId" -> "brandId";
            case "description" -> "description";
            case "groupId" -> "groupId";
            case "subgroupId" -> "subgroupId";
            case "publicId" -> "publicId";
            case "createdAt" -> "createdAt";
            case "updatedAt" -> "updatedAt";
            default -> "id";
        };

        Sort sort = Sort.by(mappedProperty);
        return asc ? sort.ascending() : sort.descending();
    }

    private Optional<String> first(MultiValueMap<String, String> params, String key) {
        return Optional.ofNullable(params.getFirst(key));
    }

    private int parseInt(String s, int def) {
        try {
            if (s == null) return def;
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    // ==========================================================
    // PRETTY JSON MANUALE (NO JACKSON)
    // ==========================================================
    private String toPrettyJson(List<AdminProductRowDTO> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            AdminProductRowDTO p = list.get(i);
            sb.append("  {\n");
            sb.append("    \"id\": ").append(n(p.id())).append(",\n");
            sb.append("    \"codpro\": ").append(q(p.codpro())).append(",\n");
            sb.append("    \"name\": ").append(q(p.name())).append(",\n");
            sb.append("    \"description\": ").append(q(p.description())).append(",\n");
            sb.append("    \"price\": ").append(qNum(p.price())).append(",\n");
            sb.append("    \"imageUrl\": ").append(q(p.imageUrl())).append(",\n");
            sb.append("    \"categoryId\": ").append(n(p.categoryId())).append(",\n");
            sb.append("    \"categoryName\": ").append(q(p.categoryName())).append(",\n");
            sb.append("    \"brandId\": ").append(n(p.brandId())).append(",\n");
            sb.append("    \"groupId\": ").append(n(p.groupId())).append(",\n");
            sb.append("    \"subgroupId\": ").append(n(p.subgroupId())).append(",\n");
            sb.append("    \"publicId\": ").append(q(p.publicId() != null ? p.publicId().toString() : null)).append(",\n");
            sb.append("    \"createdAt\": ").append(q(p.createdAt() != null ? p.createdAt().toString() : null)).append(",\n");
            sb.append("    \"updatedAt\": ").append(q(p.updatedAt() != null ? p.updatedAt().toString() : null)).append("\n");
            sb.append("  }");
            if (i < list.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]\n");
        return sb.toString();
    }

    private String q(String s) {
        if (s == null) return "null";
        return "\"" + escapeJson(s) + "\"";
    }

    private String qNum(BigDecimal n) {
        if (n == null) return "null";
        // numero senza virgolette
        return n.toPlainString();
    }

    private String n(Long v) {
        return v == null ? "null" : v.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
