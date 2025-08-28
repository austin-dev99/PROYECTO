package com.buscador.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Component
public class IndexInitializer {

    private final RestTemplate elasticRest;

    @Value("${elasticsearch.url}")
    private String elasticUrl;

    @Value("${elasticsearch.apiKey}")
    private String apiKey;

    private static final String INDEX_NAME = "productos";

    public IndexInitializer(RestTemplate elasticRest) {
        this.elasticRest = elasticRest;
    }

    @PostConstruct
    public void ensureIndexAndWarm() {
        ensureIndex();
    }

    private void ensureIndex() {
        String indexUrl = elasticUrl + "/" + INDEX_NAME;
        try {
            ResponseEntity<Void> head = elasticRest.exchange(indexUrl, HttpMethod.HEAD, authEntity(), Void.class);
            if (head.getStatusCode().is2xxSuccessful()) {
                System.out.println("✅ Índice '" + INDEX_NAME + "' ya existe.");
                return;
            }
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() != 404) {
                System.err.println("⚠️ HEAD índice devolvió: " + e.getStatusCode());
            } else {
                System.out.println("ℹ️ Índice no existe, se creará.");
            }
        } catch (Exception ex) {
            System.err.println("⚠️ Error comprobando índice: " + ex.getMessage());
        }

        // Mapping con campo search_as_you_type para sugerencias
        String mapping = """
        {
          "settings": {
            "analysis": {
              "analyzer": {
                "folding_analyzer": {
                  "tokenizer": "standard",
                  "filter": ["lowercase","asciifolding"]
                }
              }
            }
          },
          "mappings": {
            "properties": {
              "id":        { "type": "keyword" },
              "nombre": {
                "type": "text",
                "analyzer": "folding_analyzer",
                "fields": {
                  "keyword": { "type": "keyword", "ignore_above": 256 },
                  "suggest": { "type": "search_as_you_type" }
                }
              },
              "descripcion": {
                "type": "text",
                "analyzer": "folding_analyzer",
                "fields": {
                  "keyword": { "type": "keyword", "ignore_above": 256 }
                }
              },
              "categoria": {
                "type": "text",
                "fields": { "keyword": { "type": "keyword", "ignore_above": 256 } }
              },
              "subcategoria": {
                "type": "text",
                "fields": { "keyword": { "type": "keyword", "ignore_above": 256 } }
              },
              "imagen": { "type": "keyword", "ignore_above": 512 },
              "precio": { "type": "float" },
              "updated_at": { "type": "date", "format": "strict_date_optional_time||epoch_millis" }
            }
          }
        }
        """;

        HttpHeaders h = authHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<String> createResp = elasticRest.exchange(
                    indexUrl,
                    HttpMethod.PUT,
                    new HttpEntity<>(mapping, h),
                    String.class
            );
            System.out.println("✅ Índice creado: " + createResp.getStatusCode());
        } catch (HttpStatusCodeException e) {
            System.err.println("❌ Error creando índice: " + e.getStatusCode() + " body=" + e.getResponseBodyAsString());
        } catch (Exception ex) {
            System.err.println("❌ Error creando índice: " + ex.getMessage());
        }
    }

    private HttpEntity<Void> authEntity() {
        return new HttpEntity<>(authHeaders());
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "ApiKey " + apiKey);
        return headers;
    }
}