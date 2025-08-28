package com.buscador.controller;

import com.buscador.service.IndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/buscador")
public class BuscadorController {

    private final RestTemplate elasticRest;
    private final ObjectMapper mapper;
    private final IndexService indexService;

    @Value("${elasticsearch.url}")
    private String elasticUrl;

    @Value("${elasticsearch.apiKey}")
    private String elasticApiKey;

    @Autowired
    public BuscadorController(
            @Qualifier("elasticRest") RestTemplate elasticRest,
            ObjectMapper mapper,
            IndexService indexService
    ) {
        this.elasticRest = elasticRest;
        this.mapper = mapper;
        this.indexService = indexService;
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    // ================== /search ahora devuelve también items ==================
    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String q,
                                    @RequestParam(defaultValue = "20") int size) {
        System.out.println(">>> /buscador/search q=" + q + " size=" + size);
        try {
            Map<String,Object> raw = indexService.search(q, size);
            List<Map<String,Object>> items = mapHits(raw);
            long total = extractTotal(raw);
            long took = ((Number) raw.getOrDefault("took", 0)).longValue();

            // Mantengo la respuesta cruda y añado campos simplificados
            Map<String,Object> enriched = new LinkedHashMap<>(raw);
            enriched.put("items", items);
            enriched.put("totalParsed", total);
            enriched.put("tookParsed", took);

            return ResponseEntity.ok(enriched);
        } catch (HttpClientErrorException e) {
            return handleEsClientError(e, "search");
        } catch (RestClientException e) {
            return ResponseEntity.status(502)
                    .body(Map.of("status", "error", "message", "Falló petición a Elasticsearch"));
        }
    }

    // ================== /suggest añade items también ==================
    @GetMapping("/suggest")
    public ResponseEntity<?> suggest(@RequestParam String q) {
        if (q == null || q.trim().length() < 2) {
            return ResponseEntity.ok(Map.of(
                    "hits", Map.of("hits", List.of()),
                    "items", List.of()
            ));
        }
        System.out.println(">>> /buscador/suggest q=" + q);
        try {
            Map<String,Object> raw = indexService.suggest(q);
            List<Map<String,Object>> items = mapHits(raw);
            Map<String,Object> enriched = new LinkedHashMap<>(raw);
            enriched.put("items", items);
            return ResponseEntity.ok(enriched);
        } catch (HttpClientErrorException e) {
            return handleEsClientError(e, "suggest");
        } catch (RestClientException e) {
            return ResponseEntity.status(502)
                    .body(Map.of("status","error","message","Falló petición a Elasticsearch"));
        }
    }

    // ================== Endpoints simples (siguen disponibles) ==================
    @GetMapping("/search-simple")
    public ResponseEntity<?> searchSimple(@RequestParam String q,
                                          @RequestParam(defaultValue = "20") int size) {
        try {
            Map<String,Object> raw = indexService.search(q, size);
            List<Map<String,Object>> items = mapHits(raw);
            long total = extractTotal(raw);
            long took = ((Number) raw.getOrDefault("took", 0)).longValue();
            return ResponseEntity.ok(Map.of(
                    "items", items,
                    "total", total,
                    "took", took
            ));
        } catch (HttpClientErrorException e) {
            return handleEsClientError(e, "search-simple");
        } catch (RestClientException e) {
            return ResponseEntity.status(502)
                    .body(Map.of("status","error","message","Falló petición a ES"));
        }
    }

    @GetMapping("/suggest-simple")
    public ResponseEntity<?> suggestSimple(@RequestParam String q) {
        if (q == null || q.trim().length() < 2) {
            return ResponseEntity.ok(Map.of("items", List.of()));
        }
        try {
            Map<String,Object> raw = indexService.suggest(q);
            List<Map<String,Object>> items = mapHits(raw);
            return ResponseEntity.ok(Map.of("items", items));
        } catch (HttpClientErrorException e) {
            return handleEsClientError(e, "suggest-simple");
        } catch (RestClientException e) {
            return ResponseEntity.status(502)
                    .body(Map.of("status","error","message","Falló petición a ES"));
        }
    }

    @GetMapping("/facets")
    public ResponseEntity<?> facets() {
        String esUrl = elasticUrl + "/productos/_search";
        String body = """
                {
                  "size": 0,
                  "aggs": {
                    "categorias": { "terms": { "field": "categoria.keyword" } },
                    "subcategorias": { "terms": { "field": "subcategoria.keyword" } }
                  }
                }
                """;
        try {
            String response = elasticRest.postForObject(esUrl, entity(body), String.class);
            return ResponseEntity.ok(mapper.readValue(response, Map.class));
        } catch (HttpClientErrorException e) {
            return handleEsClientError(e, "facets");
        } catch (Exception e) {
            return ResponseEntity.status(502)
                    .body(Map.of("status","error","message","Falló petición a ES"));
        }
    }

    @PostMapping("/index-from-operador")
    public ResponseEntity<?> indexFromOperador() {
        int total = indexService.reindexAll();
        if (total > 0) {
            return ResponseEntity.ok(Map.of("status","ok","message","Indexados "+ total +" productos"));
        }
        return ResponseEntity.status(500)
                .body(Map.of("status","error","message","No se indexaron productos"));
    }

    // ================== Helpers ==================
    private List<Map<String,Object>> mapHits(Map<String,Object> raw) {
        if (raw == null) return List.of();
        Object hitsObj = raw.get("hits");
        if (!(hitsObj instanceof Map<?,?> hitsMap)) return List.of();
        Object innerHits = hitsMap.get("hits");
        if (!(innerHits instanceof List<?> list)) return List.of();

        return list.stream().map(h -> {
            if (!(h instanceof Map<?,?> hit)) return Map.<String,Object>of();
            Object sourceObj = hit.get("_source");
            Map<String,Object> src = sourceObj instanceof Map<?,?> m
                    ? new LinkedHashMap<>((Map<String,Object>) m)
                    : new LinkedHashMap<>();
            String id = String.valueOf(src.getOrDefault("id", hit.get("_id")));
            src.put("id", id);
            Object score = hit.get("_score");
            if (score instanceof Number n) src.put("_score", n);
            return src;
        }).collect(Collectors.toList());
    }

    private long extractTotal(Map<String,Object> raw) {
        try {
            Object hitsObj = raw.get("hits");
            if (hitsObj instanceof Map<?,?> hm) {
                Object totalObj = hm.get("total");
                if (totalObj instanceof Map<?,?> tm) {
                    Object v = tm.get("value");
                    if (v instanceof Number n) return n.longValue();
                } else if (totalObj instanceof Number n) {
                    return n.longValue();
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private ResponseEntity<?> handleEsClientError(HttpClientErrorException e, String tag) {
        String body = e.getResponseBodyAsString();
        System.err.println("❌ ES error " + tag + ": " + e.getStatusCode() + " body=" + body);
        return ResponseEntity.status(e.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private HttpEntity<String> entity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "ApiKey " + elasticApiKey);
        return new HttpEntity<>(body, headers);
    }
}