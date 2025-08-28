package com.buscador.controller;

import com.buscador.service.IndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/")
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

    // 🔎 Buscar
    @GetMapping("/search")
    public ResponseEntity<String> search(@RequestParam String q,
                                         @RequestParam(defaultValue = "20") int size) {
        String esUrl = elasticUrl + "/productos/_search";
        String body = """
                {
                  "size": %d,
                  "query": {
                    "multi_match": {
                      "query": "%s",
                      "fields": ["nombre^3", "descripcion^2", "categoria", "subcategoria"]
                    }
                  }
                }
                """.formatted(size, q);

        try {
            System.out.println("🔹 Search query: " + q);
            System.out.println("🔹 Elasticsearch POST: " + body);
            String response = elasticRest.postForObject(esUrl, entity(body), String.class);
            return ResponseEntity.ok(response);
        } catch (HttpClientErrorException e) {
            System.err.println("❌ Elasticsearch returned error: " + e.getStatusCode());
            return ResponseEntity.status(e.getStatusCode()).body("{\"status\":\"error\",\"message\":\"Elasticsearch error\"}");
        } catch (RestClientException e) {
            System.err.println("❌ Elasticsearch request failed: " + e.getMessage());
            return ResponseEntity.status(502).body("{\"status\":\"error\",\"message\":\"Application failed to respond\"}");
        }
    }

    // ✍ Autocompletar
    @GetMapping("/suggest")
    public ResponseEntity<String> suggest(@RequestParam String q) {
        String esUrl = elasticUrl + "/productos/_search";
        String body = """
                {
                  "size": 5,
                  "query": {
                    "multi_match": {
                      "query": "%s",
                      "type": "bool_prefix",
                      "fields": ["nombre.suggest", "nombre.suggest._2gram", "nombre.suggest._3gram"]
                    }
                  }
                }
                """.formatted(q);

        try {
            System.out.println("🔹 Suggest query: " + q);
            System.out.println("🔹 Elasticsearch POST: " + body);
            String response = elasticRest.postForObject(esUrl, entity(body), String.class);
            return ResponseEntity.ok(response);
        } catch (HttpClientErrorException e) {
            System.err.println("❌ Elasticsearch returned error: " + e.getStatusCode());
            return ResponseEntity.status(e.getStatusCode()).body("{\"status\":\"error\",\"message\":\"Elasticsearch error\"}");
        } catch (RestClientException e) {
            System.err.println("❌ Elasticsearch request failed: " + e.getMessage());
            return ResponseEntity.status(502).body("{\"status\":\"error\",\"message\":\"Application failed to respond\"}");
        }
    }

    // 📊 Facetas
    @GetMapping("/facets")
    public ResponseEntity<String> facets() {
        String esUrl = elasticUrl + "/productos/_search";
        String body = """
                { "size": 0, "aggs": { "categorias": { "terms": { "field": "categoria.keyword" } } } }
                """;
        try {
            String response = elasticRest.postForObject(esUrl, entity(body), String.class);
            return ResponseEntity.ok(response);
        } catch (RestClientException e) {
            System.err.println("❌ Elasticsearch facets request failed: " + e.getMessage());
            return ResponseEntity.status(502).body("{\"status\":\"error\",\"message\":\"Application failed to respond\"}");
        }
    }

    // 📥 Indexación manual
    @PostMapping("/index-from-operador")
    public ResponseEntity<String> indexFromOperador() {
        int total = indexService.reindexAll();
        if (total > 0) return ResponseEntity.ok("{\"status\":\"ok\",\"message\":\"Indexados " + total + " productos.\"}");
        return ResponseEntity.status(500).body("{\"status\":\"error\",\"message\":\"No se indexaron productos.\"}");
    }

    // Utilidad para headers
    private HttpEntity<String> entity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "ApiKey " + elasticApiKey);
        return new HttpEntity<>(body, headers);
    }
}
