package com.buscador.controller;

import com.buscador.service.IndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    // Caché en memoria
    private final Map<String, String> searchCache = new ConcurrentHashMap<>();
    private final Map<String, String> suggestCache = new ConcurrentHashMap<>();
    private final Map<String, String> facetsCache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTime = new ConcurrentHashMap<>();
    private final long CACHE_EXPIRATION_MS = 30000; // 30s

    @Autowired
    public BuscadorController(
            @Qualifier("elasticRest") RestTemplate elasticRest,
            ObjectMapper mapper,
            IndexService indexService
    ) {
        // Configurar timeout en RestTemplate
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(2000); // 2s conexión
        factory.setReadTimeout(5000);    // 5s lectura
        this.elasticRest = new RestTemplate(factory);

        this.mapper = mapper;
        this.indexService = indexService;
    }

    // 🔎 Buscar con caché
    @GetMapping("/search")
    public ResponseEntity<String> search(@RequestParam String q,
                                         @RequestParam(defaultValue = "20") int size) {
        String key = q + "_" + size;
        cleanCache();

        if (searchCache.containsKey(key)) {
            return ResponseEntity.ok(searchCache.get(key));
        }

        String esUrl = elasticUrl + "/productos/_search";
        String body = """
        {
          "size": %d,
          "_source": ["nombre","descripcion","categoria","subcategoria"],
          "query": {
            "multi_match": {
              "query": "%s",
              "fields": ["nombre^3", "descripcion^2", "categoria", "subcategoria"]
            }
          }
        }
        """.formatted(size, q);

        try {
            String result = elasticRest.postForObject(esUrl, entity(body), String.class);
            searchCache.put(key, result);
            cacheTime.put(key, System.currentTimeMillis());
            return ResponseEntity.ok(result);
        } catch (RestClientException e) {
            System.err.println("❌ Error /search: " + e.getMessage());
            return ResponseEntity.ok("{\"hits\":{\"hits\":[]}}");
        }
    }

    // ✍ Autocompletar con caché
    @GetMapping("/suggest")
    public ResponseEntity<String> suggest(@RequestParam String q) {
        cleanCache();

        if (suggestCache.containsKey(q)) {
            return ResponseEntity.ok(suggestCache.get(q));
        }

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
            String result = elasticRest.postForObject(esUrl, entity(body), String.class);
            suggestCache.put(q, result);
            cacheTime.put(q, System.currentTimeMillis());
            return ResponseEntity.ok(result);
        } catch (RestClientException e) {
            System.err.println("❌ Error /suggest: " + e.getMessage());
            return ResponseEntity.ok("{\"hits\":{\"hits\":[]}}");
        }
    }

    // 📊 Facetas con caché
    @GetMapping("/facets")
    public ResponseEntity<String> facets() {
        String key = "facets";
        cleanCache();

        if (facetsCache.containsKey(key)) {
            return ResponseEntity.ok(facetsCache.get(key));
        }

        String esUrl = elasticUrl + "/productos/_search";
        String body = """
        { 
          "size": 0,
          "aggs": { "categorias": { "terms": { "field": "categoria.keyword" } } }
        }
        """;

        try {
            String result = elasticRest.postForObject(esUrl, entity(body), String.class);
            facetsCache.put(key, result);
            cacheTime.put(key, System.currentTimeMillis());
            return ResponseEntity.ok(result);
        } catch (RestClientException e) {
            System.err.println("❌ Error /facets: " + e.getMessage());
            return ResponseEntity.ok("{\"aggregations\":{\"categorias\":{\"buckets\":[]}}}");
        }
    }

    // 📥 Indexación manual
    @PostMapping("/index-from-operador")
    public ResponseEntity<String> indexFromOperador() {
        int total = indexService.reindexAll();
        if (total > 0) return ResponseEntity.ok("✅ Indexados " + total + " productos.");
        return ResponseEntity.status(500).body("❌ No se indexaron productos.");
    }

    // ✨ Utilidad: HttpEntity con headers
    private HttpEntity<String> entity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "ApiKey " + elasticApiKey);
        return new HttpEntity<>(body, headers);
    }

    // ✨ Limpiar caché caducada
    private void cleanCache() {
        long now = System.currentTimeMillis();
        cacheTime.forEach((key, time) -> {
            if (now - time > CACHE_EXPIRATION_MS) {
                searchCache.remove(key);
                suggestCache.remove(key);
                facetsCache.remove(key);
                cacheTime.remove(key);
            }
        });
    }
}
