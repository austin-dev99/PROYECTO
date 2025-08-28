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

import java.util.Map;

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

    // Health simple
    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    // Buscar productos
    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int size
    ) {
        System.out.println(">>> /search q=" + q + " size=" + size);
        try {
            Map<String, Object> resp = indexService.search(q, size);
            return ResponseEntity.ok(resp);
        } catch (HttpClientErrorException e) {
            return handleEsClientError(e, "search");
        } catch (RestClientException e) {
            return ResponseEntity.status(502)
                    .body(Map.of("status", "error", "message", "Falló petición a Elasticsearch"));
        }
    }

    // Autocomplete
    @GetMapping("/suggest")
    public ResponseEntity<?> suggest(@RequestParam String q) {
        if (q == null || q.trim().length() < 2) {
            return ResponseEntity.ok(Map.of("hits", Map.of("hits", java.util.List.of())));
        }
        System.out.println(">>> /suggest q=" + q);
        try {
            Map<String, Object> resp = indexService.suggest(q);
            return ResponseEntity.ok(resp);
        } catch (HttpClientErrorException e) {
            return handleEsClientError(e, "suggest");
        } catch (RestClientException e) {
            return ResponseEntity.status(502)
                    .body(Map.of("status", "error", "message", "Falló petición a Elasticsearch"));
        }
    }

    // Facetas
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
                    .body(Map.of("status", "error", "message", "Falló petición a ES"));
        }
    }

    // Reindex manual
    @PostMapping("/index-from-operador")
    public ResponseEntity<?> indexFromOperador() {
        int total = indexService.reindexAll();
        if (total > 0) {
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Indexados " + total + " productos"));
        }
        return ResponseEntity.status(500)
                .body(Map.of("status", "error", "message", "No se indexaron productos"));
    }

    private ResponseEntity<?> handleEsClientError(HttpClientErrorException e, String tag) {
        String body = e.getResponseBodyAsString();
        System.err.println("❌ ES error " + tag + ": " + e.getStatusCode() + " body=" + body);
        return ResponseEntity.status(e.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    // Headers util
    private HttpEntity<String> entity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "ApiKey " + elasticApiKey);
        return new HttpEntity<>(body, headers);
    }
}