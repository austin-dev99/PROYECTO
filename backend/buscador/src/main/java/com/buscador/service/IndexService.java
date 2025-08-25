package com.buscador.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class IndexService {

    private final RestTemplate rest;
    private final ObjectMapper mapper;

    // Variables configurables vía ENV
    private final String operadorBaseUrl;
    private final String elasticHost;
    private final String elasticApiKey;

    public IndexService(RestTemplate rest, ObjectMapper mapper,
                        @Value("${OPERADOR_URL:http://localhost:8082}") String operadorBaseUrl,
                        @Value("${ELASTICSEARCH_HOST:http://localhost:9200}") String elasticHost,
                        @Value("${ELASTIC_API_KEY:}") String elasticApiKey) {
        this.rest = rest;
        this.mapper = mapper;
        this.operadorBaseUrl = operadorBaseUrl;
        this.elasticHost = elasticHost;
        this.elasticApiKey = elasticApiKey;
    }

    /**
     * Indexa todos los productos obtenidos desde Operador usando la Bulk API de Elasticsearch.
     * Devuelve la cantidad de documentos procesados (no necesariamente indexados con éxito).
     */
    public int indexAllFromOperador() {
        try {
            String url = operadorBaseUrl.endsWith("/") ? operadorBaseUrl + "productos" : operadorBaseUrl + "/productos";
            ResponseEntity<String> resp = rest.getForEntity(url, String.class);

            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                System.err.println("Operador no responde correctamente: " + resp.getStatusCode());
                return 0;
            }

            List<Map<String, Object>> productos = mapper.readValue(resp.getBody(), new TypeReference<>() {});
            if (productos.isEmpty()) {
                System.out.println("No hay productos para indexar.");
                return 0;
            }

            // Construir NDJSON para Bulk API
            StringBuilder bulk = new StringBuilder();
            for (Map<String, Object> p : productos) {
                Object idObj = p.getOrDefault("id", p.get("ID"));
                if (idObj != null) {
                    bulk.append("{\"index\":{\"_id\":\"").append(idObj).append("\"}}\n");
                } else {
                    bulk.append("{\"index\":{}}\n");
                }
                bulk.append(mapper.writeValueAsString(p)).append("\n");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/x-ndjson"));
            if (elasticApiKey != null && !elasticApiKey.isBlank()) {
                headers.set("Authorization", "ApiKey " + elasticApiKey);
            }

            HttpEntity<String> entity = new HttpEntity<>(bulk.toString(), headers);
            String bulkUrl = elasticHost.endsWith("/") ? elasticHost + "_bulk" : elasticHost + "/_bulk";

            ResponseEntity<String> esResp = rest.postForEntity(bulkUrl, entity, String.class);

            // opcional: podríamos parsear esResp.getBody() para verificar errors:true
            System.out.println("Bulk response status: " + esResp.getStatusCode());
            return productos.size();

        } catch (Exception ex) {
            ex.printStackTrace();
            return 0;
        }
    }

    /**
     * Reintenta indexar cada 30s (espera 30s antes del primer intento).
     * Puedes ajustar initialDelay y fixedDelay mediante variables en application.yml o env vars.
     */
    @Scheduled(initialDelayString = "${index.initialDelay:30000}", fixedDelayString = "${index.fixedDelay:30000}")
    public void scheduledIndex() {
        int count = indexAllFromOperador();
        System.out.println("Scheduled index: " + count + " documentos procesados.");
    }
}
