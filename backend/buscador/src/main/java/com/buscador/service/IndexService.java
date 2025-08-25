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


    // Valores leídos de variables de entorno (application.yml o Railway ENV)

    @Value("${app.elasticsearch.url}")
    private String elasticUrl;
    @Value("${app.operador.url}")
    private String operadorUrl;
    @Value("${app.elasticsearch.apiKey}")
    private String elasticApiKey;

    public IndexService(
            @Qualifier("eurekaRestTemplate") RestTemplate operadorRest,
            @Qualifier("plainRestTemplate") RestTemplate elasticRest
    ) {
        this.operadorRest = operadorRest;
        this.elasticRest = elasticRest;
    }

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

        HttpEntity<String> entity = new HttpEntity<>(bulkBody.toString(), headers);

        try {
            String response = elasticRest.postForObject(elasticUrl, entity, String.class);
            System.out.println("✅ Respuesta Elasticsearch: " + response);
        } catch (Exception ex) {
            System.err.println("❌ Error indexando en Elasticsearch: " + ex.getMessage());
            return 0;
        }

        return productos.size();
    }

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
