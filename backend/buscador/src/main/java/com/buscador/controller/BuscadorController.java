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
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/")
public class BuscadorController {

    private final RestTemplate elasticRest;
    private final ObjectMapper mapper;
    private final IndexService indexService;

    @Value("${elasticsearch.url}")
    private String elasticUrl;

    @Value("${elasticsearch.username}")
    private String elasticUser;

    @Value("${elasticsearch.password}")
    private String elasticPass;

    @Autowired
    public BuscadorController(
            @Qualifier("plainRestTemplate") RestTemplate elasticRest,
            ObjectMapper mapper,
            IndexService indexService
    ) {
        this.elasticRest = elasticRest;
        this.mapper = mapper;
        this.indexService = indexService;
    }

    // 🔎 Buscar
    @GetMapping("/search")
    public String search(@RequestParam String q,
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

        return elasticRest.postForObject(esUrl, entity(body), String.class);
    }

    // ✍ Autocompletar
    @GetMapping("/suggest")
    public String suggest(@RequestParam String q) {
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

        return elasticRest.postForObject(esUrl, entity(body), String.class);
    }

    // 📊 Facetas
    @GetMapping("/facets")
    public String facets() {
        String esUrl = elasticUrl + "/productos/_search";
        String body = """
        { "size": 0, "aggs": { "categorias": { "terms": { "field": "categoria.keyword" } } } }
        """;
        return elasticRest.postForObject(esUrl, entity(body), String.class);
    }

    // 📥 Indexación manual (si la quieres llamar con curl)
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
        headers.setBasicAuth(elasticUser, elasticPass); // 👈 Agregamos auth
        return new HttpEntity<>(body, headers);
    }
}
