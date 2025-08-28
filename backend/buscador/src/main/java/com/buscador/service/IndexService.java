package com.buscador.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Map;

@Service
public class IndexService {

    private final RestTemplate operadorRest;
    private final RestTemplate elasticRest;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${elasticsearch.url}")
    private String elasticUrl;

    @Value("${elasticsearch.apiKey}")
    private String elasticApiKey;

    @Value("${operador.url}")
    private String operadorUrl;

    private static final String INDEX = "productos";

    public IndexService(
            @Qualifier("operadorRest") RestTemplate operadorRest,
            @Qualifier("elasticRest") RestTemplate elasticRest
    ) {
        this.operadorRest = operadorRest;
        this.elasticRest = elasticRest;
    }

    /**
     * Reindexa todos los productos desde Operador → Elasticsearch (bulk).
     */
    @SuppressWarnings("unchecked")
    public int reindexAll() {
        List<Map<String, Object>> productos;

        try {
            productos = operadorRest.getForObject(operadorUrl, List.class);
        } catch (Exception e) {
            System.err.println("❌ Error obteniendo productos del Operador: " + e.getMessage());
            return 0;
        }

        if (productos == null || productos.isEmpty()) {
            System.out.println("ℹ️ Operador retornó 0 productos.");
            return 0;
        }

        StringBuilder bulkBody = new StringBuilder();
        for (Map<String, Object> p : productos) {
            Object rawId = p.get("id");
            if (rawId == null) continue;
            String id = String.valueOf(rawId);

            // Línea de acción
            bulkBody.append("{\"index\":{\"_index\":\"").append(INDEX).append("\",\"_id\":\"")
                    .append(id).append("\"}}\n");

            try {
                bulkBody.append(mapper.writeValueAsString(p)).append("\n");
            } catch (JsonProcessingException e) {
                System.err.println("⚠️ Error serializando producto id=" + id + ": " + e.getMessage());
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_NDJSON);
        headers.set("Authorization", "ApiKey " + elasticApiKey);

        HttpEntity<String> entity = new HttpEntity<>(bulkBody.toString(), headers);
        String bulkUrl = elasticUrl + "/_bulk";

        try {
            String response = elasticRest.postForObject(bulkUrl, entity, String.class);
            System.out.println("✅ Respuesta bulk: " + response);

            // Validar si hubo errores parciales
            if (response != null && response.contains("\"errors\":true")) {
                System.err.println("⚠️ El bulk reporta errores parciales. Revisa los items.");
            }
        } catch (Exception ex) {
            System.err.println("❌ Error indexando en Elasticsearch: " + ex.getMessage());
            return 0;
        }

        return productos.size();
    }

    /**
     * Búsqueda general multi_match.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> search(String query, int size) {
        String url = elasticUrl + "/" + INDEX + "/_search";

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
            """.formatted(size, query.replace("\"", "\\\""));

        HttpHeaders headers = jsonHeaders();
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        return elasticRest.postForObject(url, entity, Map.class);
    }

    /**
     * Sugerencias (autocomplete) usando search_as_you_type.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> suggest(String query) {
        String url = elasticUrl + "/" + INDEX + "/_search";

        String body = """
            {
              "size": 5,
              "query": {
                "multi_match": {
                  "query": "%s",
                  "type": "bool_prefix",
                  "fields": [
                    "nombre.suggest",
                    "nombre.suggest._2gram",
                    "nombre.suggest._3gram",
                    "nombre"        // fallback para coincidencias directas
                  ]
                }
              },
              "_source": ["id","nombre","imagen"]
            }
            """.formatted(query.replace("\"", "\\\""));

        HttpHeaders headers = jsonHeaders();
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        return elasticRest.postForObject(url, entity, Map.class);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "ApiKey " + elasticApiKey);
        return headers;
    }

    /**
     * Reindexación periódica cada 5 minutos (ajusta si quieres).
     */
    @Scheduled(fixedDelay = 300000)
    public void autoReindex() {
        try {
            int total = reindexAll();
            if (total > 0) {
                System.out.println("✅ Reindexación periódica. Productos indexados: " + total);
            } else {
                System.out.println("⚠️ Reindexación periódica sin productos.");
            }
        } catch (Exception e) {
            System.err.println("❌ Error en reindexación periódica: " + e.getMessage());
        }
    }
}