package com.buscador.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    public IndexService(
            @Qualifier("operadorRest") RestTemplate operadorRest,
            @Qualifier("elasticRest") RestTemplate elasticRest
    ) {
        this.operadorRest = operadorRest;
        this.elasticRest = elasticRest;
    }

    /**
     * 🔄 Reindexa todos los productos desde el Operador → Elasticsearch
     */
    @SuppressWarnings("unchecked")
    public int reindexAll() {
        List<Map<String, Object>> productos;

        try {
            productos = operadorRest.getForObject(operadorUrl, List.class);
        } catch (Exception e) {
            System.err.println("❌ Error al obtener productos del Operador: " + e.getMessage());
            return 0;
        }

        if (productos == null || productos.isEmpty()) {
            System.out.println("ℹ️ Operador no devolvió productos.");
            return 0;
        }

        StringBuilder bulkBody = new StringBuilder();
        for (Map<String, Object> p : productos) {
            Object rawId = p.get("id");
            if (rawId == null) continue;
            String id = String.valueOf(rawId);

            bulkBody.append("{\"index\":{\"_id\":\"").append(id).append("\"}}\n");
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

        try {
            String bulkUrl = elasticUrl + "/productos/_bulk";
            String response = elasticRest.postForObject(bulkUrl, entity, String.class);
            System.out.println("✅ Respuesta Elasticsearch: " + response);
        } catch (Exception ex) {
            System.err.println("❌ Error indexando en Elasticsearch: " + ex.getMessage());
            return 0;
        }

        return productos.size();
    }

    /**
     * 🔎 Búsqueda general (multi_match en nombre, descripción, categoría y subcategoría)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> search(String query) {
        String url = elasticUrl + "/productos/_search";

        String body = """
            {
              "query": {
                "multi_match": {
                  "query": "%s",
                  "fields": ["nombre", "descripcion", "categoria", "subcategoria"]
                }
              }
            }
            """.formatted(query);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "ApiKey " + elasticApiKey);

        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        return elasticRest.postForObject(url, entity, Map.class);
    }

    /**
     * 💡 Sugerencias tipo autocompletado (match_phrase_prefix en nombre)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> suggest(String query) {
        String url = elasticUrl + "/productos/_search";

        String body = """
            {
              "query": {
                "match_phrase_prefix": {
                  "nombre": "%s"
                }
              },
              "_source": ["id", "nombre", "imagen"] 
            }
            """.formatted(query);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "ApiKey " + elasticApiKey);

        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        return elasticRest.postForObject(url, entity, Map.class);
    }

    /**
     * ⏰ Reindexación automática cada 30 segundos
     */
    @Scheduled(fixedDelay = 30000)
    public void autoReindex() {
        try {
            int total = reindexAll();
            if (total > 0) {
                System.out.println("✅ Reindexación periódica completada. Productos indexados: " + total);
            } else {
                System.out.println("⚠️ Reindexación periódica: sin productos disponibles.");
            }
        } catch (Exception e) {
            System.err.println("❌ Error en reindexación periódica: " + e.getMessage());
        }
    }
}
