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

    // Caché en memoria para sugerencias
    private final Map<String, String> suggestCache = new ConcurrentHashMap<>();
    private final long CACHE_EXPIRATION_MS = 30000; // 30s
    private final Map<String, Long> suggestCacheTime = new ConcurrentHashMap<>();

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

    // ✍ Autocompletar con caché
    @GetMapping("/suggest")
    public ResponseEntity<String> suggest(@RequestParam String q) {
        // Limpiar caché caducada
        suggestCacheTime.forEach((key, time) -> {
            if (System.currentTimeMillis() - time > CACHE_EXPIRATION_MS) {
                suggestCache.remove(key);
                suggestCacheTime.remove(key);
            }
        });

        // Verificar caché
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
            long start = System.currentTimeMillis();
            String result = elasticRest.postForObject(esUrl, entity(body), String.class);
            long duration = System.currentTimeMillis() - start;
            System.out.println("🔹 /suggest query took " + duration + " ms");

            // Guardar en caché
            suggestCache.put(q, result);
            suggestCacheTime.put(q, System.currentTimeMillis());

            return ResponseEntity.ok(result);
        } catch (RestClientException e) {
            System.err.println("❌ Error /suggest: " + e.getMessage());
            // Devolver respuesta vacía para no bloquear Railway
            return ResponseEntity.ok("{\"hits\":{\"hits\":[]}}");
        }
    }

    // Utilidad
    private HttpEntity<String> entity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "ApiKey " + elasticApiKey);
        return new HttpEntity<>(body, headers);
    }
}
