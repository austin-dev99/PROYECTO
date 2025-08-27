package com.buscador.controller;
//
//Controlador que expone
//        /buscador/search,
//        /buscador/suggest,
//        /buscador/facets.

import com.buscador.service.IndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;

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

    // 🔎 Buscar
    @GetMapping("/search")
    public ResponseEntity<String> search(@RequestParam String q,
                                         @RequestParam(defaultValue = "20") int size) {
        String esUrl = elasticUrl + "/productos/_search";
        String body = """
        {
          "size": %d,
          "_source": ["id","nombre","descripcion","categoria","subcategoria"],
          "query": {
            "multi_match": {
              "query": "%s",
              "fields": ["nombre^3", "descripcion^2", "categoria", "subcategoria"]
            }
          }
        }
        """.formatted(size, q);

        try {
            long start = System.currentTimeMillis();
            String result = elasticRest.postForObject(esUrl, entity(body), String.class);
            long duration = System.currentTimeMillis() - start;
            System.out.println("🔹 /search query took " + duration + " ms");
            return ResponseEntity.ok(result);
        } catch (RestClientException e) {
            System.err.println("❌ Error /search: " + e.getMessage());
            return ResponseEntity.status(502).body("{\"error\":\"Elasticsearch no responde\"}");
        }
    }

    // ✍ Autocompletar
    @GetMapping("/suggest")
    public ResponseEntity<String> suggest(@RequestParam String q) {
        String esUrl = elasticUrl + "/productos/_search";
        String body = """
        {
          "size": 5,
          "_source": ["nombre"],
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
            long start = System.currentTimeMillis();
            String result = elasticRest.postForObject(esUrl, entity(body), String.class);
            long duration = System.currentTimeMillis() - start;
            System.out.println("🔹 /suggest query took " + duration + " ms");
            return ResponseEntity.ok(result);
        } catch (RestClientException e) {
            System.err.println("❌ Error /suggest: " + e.getMessage());
            return ResponseEntity.status(502).body("{\"error\":\"Elasticsearch no responde\"}");
        }
    }

    // 📊 Facetas
    @GetMapping("/facets")
    public ResponseEntity<String> facets() {
        String esUrl = elasticUrl + "/productos/_search";
        String body = """
        {
          "size": 0,
          "aggs": { "categorias": { "terms": { "field": "categoria.keyword" } } }
        }
        """;

        try {
            long start = System.currentTimeMillis();
            String result = elasticRest.postForObject(esUrl, entity(body), String.class);
            long duration = System.currentTimeMillis() - start;
            System.out.println("🔹 /facets query took " + duration + " ms");
            return ResponseEntity.ok(result);
        } catch (RestClientException e) {
            System.err.println("❌ Error /facets: " + e.getMessage());
            return ResponseEntity.status(502).body("{\"error\":\"Elasticsearch no responde\"}");
        }
    }

    // 📥 Indexación manual
    @PostMapping("/index-from-operador")
    public ResponseEntity<String> indexFromOperador() {
        int total = indexService.reindexAll();
        if (total > 0) return ResponseEntity.ok("✅ Indexados " + total + " productos.");
        return ResponseEntity.status(500).body("❌ No se indexaron productos.");
    }

    // Utilidad
    private HttpEntity<String> entity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "ApiKey " + elasticApiKey);
        return new HttpEntity<>(body, headers);
    }
}
