package com.buscador.controller;

import com.buscador.service.IndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/buscador")
public class BuscadorController {

    private final RestTemplate rest;
    private final ObjectMapper mapper;
    private final IndexService indexService;
    private final String elasticHost;
    private final String elasticApiKey;

    public BuscadorController(RestTemplate rest, ObjectMapper mapper, IndexService indexService,
                              @Value("${ELASTICSEARCH_HOST:http://localhost:9200}") String elasticHost,
                              @Value("${ELASTIC_API_KEY:}") String elasticApiKey) {
        this.rest = rest;
        this.mapper = mapper;
        this.indexService = indexService;
        this.elasticHost = elasticHost;
        this.elasticApiKey = elasticApiKey;
    }

    /** Endpoint para indexar manualmente: POST /buscador/index-from-operador */
    @PostMapping("/index-from-operador")
    public ResponseEntity<String> indexFromOperador() {
        int n = indexService.indexAllFromOperador();
        return ResponseEntity.ok("{\"indexed\":" + n + "}");
    }

    /** Buscar full-text: GET /buscador/search?q=texto&size=20 */
    @GetMapping("/search")
    public ResponseEntity<String> search(@RequestParam String q, @RequestParam(defaultValue = "20") int size) {
        String url = (elasticHost.endsWith("/") ? elasticHost + "productos/_search" : elasticHost + "/productos/_search");
        String body = """
                {
                  "size": %d,
                  "query": {
                    "multi_match": {
                      "query": "%s",
                      "fields": ["nombre^3","descripcion^2","categoria","subcategoria"]
                    }
                  }
                }
                """.formatted(size, q);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (elasticApiKey != null && !elasticApiKey.isBlank()) headers.set("Authorization", "ApiKey " + elasticApiKey);

        HttpEntity<String> req = new HttpEntity<>(body, headers);
        ResponseEntity<String> res = rest.postForEntity(url, req, String.class);
        return ResponseEntity.status(res.getStatusCode()).body(res.getBody());
    }

    /** Suggest (autocomplete): GET /buscador/suggest?q=pre */
    @GetMapping("/suggest")
    public ResponseEntity<String> suggest(@RequestParam String q) {
        String url = (elasticHost.endsWith("/") ? elasticHost + "productos/_search" : elasticHost + "/productos/_search");
        String body = """
                {
                  "size": 5,
                  "query": {
                    "multi_match": {
                      "query": "%s",
                      "type": "bool_prefix",
                      "fields": ["nombre.suggest","nombre.suggest._2gram","nombre.suggest._3gram"]
                    }
                  }
                }
                """.formatted(q);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (elasticApiKey != null && !elasticApiKey.isBlank()) headers.set("Authorization", "ApiKey " + elasticApiKey);

        HttpEntity<String> req = new HttpEntity<>(body, headers);
        ResponseEntity<String> res = rest.postForEntity(url, req, String.class);
        return ResponseEntity.status(res.getStatusCode()).body(res.getBody());
    }

    /** Facets: GET /buscador/facets */
    @GetMapping("/facets")
    public ResponseEntity<String> facets() {
        String url = (elasticHost.endsWith("/") ? elasticHost + "productos/_search" : elasticHost + "/productos/_search");
        String body = "{ \"size\": 0, \"aggs\": { \"categorias\": { \"terms\": { \"field\": \"categoria.keyword\" } } } }";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (elasticApiKey != null && !elasticApiKey.isBlank()) headers.set("Authorization", "ApiKey " + elasticApiKey);

        HttpEntity<String> req = new HttpEntity<>(body, headers);
        ResponseEntity<String> res = rest.postForEntity(url, req, String.class);
        return ResponseEntity.status(res.getStatusCode()).body(res.getBody());
    }
}
