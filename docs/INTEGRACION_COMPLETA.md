# Guía de Integración de Microservicios

## Índice
1. [Arquitectura General](#arquitectura-general)
2. [Componentes del Sistema](#componentes-del-sistema)
3. [Flujo de Comunicación](#flujo-de-comunicación)
4. [Inyección de Dependencias](#inyección-de-dependencias)
5. [Pruebas de Integración](#pruebas-de-integración)
6. [Configuración de Puertos](#configuración-de-puertos)
7. [Despliegue con Docker](#despliegue-con-docker)
8. [Solución de Problemas Comunes](#solución-de-problemas-comunes)

## Arquitectura General

El proyecto está estructurado como una aplicación de microservicios con los siguientes componentes principales:

```
                   ┌─────────────┐
                   │   Cliente   │
                   │  (Browser)  │
                   └──────┬──────┘
                          │
                          ▼
┌────────────────────────────────────────┐
│           API Gateway (8080)           │
└─┬─────────────────────┬────────────────┘
  │                     │
  ▼                     ▼
┌────────────┐  ┌───────────────┐
│  Operador  │  │    Buscador   │◄────┐
│   (8082)   │  │    (8081)     │     │
└─────┬──────┘  └───────┬───────┘     │
      │                 │             │
      ▼                 ▼             │
┌──────────┐    ┌──────────────┐    ┌─┴───────┐
│  MySQL   │    │ Elasticsearch │    │ Kibana  │
│  (3306)  │    │    (9200)     │    │ (5601)  │
└──────────┘    └──────────────┘    └─────────┘
```

## Componentes del Sistema

### 1. API Gateway (Puerto 8080)

**Tecnología**: Spring Cloud Gateway

**Función**: Actúa como punto de entrada único para todas las solicitudes del cliente, enrutándolas a los microservicios correspondientes.

**Configuración clave**:
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: operador
          uri: ${OPERADOR_URL:http://localhost:8082}
          predicates:
            - Path=/operador/**
          filters:
            - StripPrefix=1

        - id: buscador
          uri: ${BUSCADOR_URL:http://localhost:8081}
          predicates:
            - Path=/buscador/**
          filters:
            - StripPrefix=1
```

**Explicación del código**:
- `uri`: Define la URL base del microservicio destino
- `predicates`: Condiciones que deben cumplirse para que la solicitud sea enrutada
- `filters`: Modificaciones aplicadas a la solicitud antes de enviarla al microservicio
- `StripPrefix=1`: Elimina el primer segmento de la URL (por ejemplo, `/operador/productos` se convierte en `/productos` antes de enviarse al servicio operador)

**Nota importante**: La propiedad `spring.cloud.gateway.discovery.locator.enabled` se ha actualizado a `spring.cloud.gateway.server.webflux.discovery.locator.enabled` en versiones recientes de Spring Cloud Gateway.

### 2. Servicio Operador (Puerto 8082)

**Tecnología**: Spring Boot, Spring Data JPA

**Función**: Gestiona los datos de productos almacenados en MySQL.

**Configuración clave**:
```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:mysql://localhost:3306/tienda}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
```

**Endpoints principales**:
- `GET /productos`: Obtiene todos los productos
- `GET /productos?categoria={categoria}`: Filtra productos por categoría
- `GET /productos/{id}`: Obtiene un producto específico por ID

**Estructura interna típica**:
- `Controller`: Maneja las solicitudes HTTP
- `Service`: Contiene la lógica de negocio
- `Repository`: Interfaz para acceso a datos
- `Entity`: Mapeo de tablas de base de datos a objetos Java

### 3. Servicio Buscador (Puerto 8081)

**Tecnología**: Spring Boot, Spring Data Elasticsearch

**Función**: Proporciona funcionalidades de búsqueda avanzada utilizando Elasticsearch.

**Configuración clave**:
```yaml
spring:
  elasticsearch:
    uris: ${ELASTICSEARCH_URI:http://elasticsearch:9200}
```

**Endpoints principales**:
- `GET /search?q={texto}`: Busca productos con el texto proporcionado
- `GET /suggest?q={prefijo}`: Proporciona sugerencias para autocompletado
- `GET /facets`: Obtiene categorías para filtrado
- `POST /index-from-operador`: Indexa productos desde el servicio operador

**Estructura interna típica**:
- `Controller`: Maneja las solicitudes HTTP de búsqueda
- `Service`: Contiene lógica de búsqueda y indexación
- `Repository`: Interfaz para Elasticsearch
- `Document`: Mapeo de documentos Elasticsearch

### 4. Elasticsearch (Puerto 9200)

**Función**: Motor de búsqueda y análisis distribuido.

**Configuración clave**: El archivo `mapping.json` define la estructura de los índices:
```json
{
  "mappings": {
    "properties": {
      "id": { "type": "keyword" },
      "nombre": { 
        "type": "text",
        "analyzer": "spanish",
        "fields": {
          "keyword": { "type": "keyword" },
          "completion": { "type": "completion" }
        }
      },
      "descripcion": { "type": "text", "analyzer": "spanish" },
      "precio": { "type": "float" },
      "categoria": { "type": "keyword" }
    }
  }
}
```

**Explicación del mapping**:
- `text`: Campo de texto completo, analizado para búsquedas
- `keyword`: Campo exacto, sin analizar (para filtros)
- `completion`: Tipo especial para sugerencias rápidas
- `analyzer`: Define cómo se procesa el texto (spanish tiene reglas para eliminar tildes, etc.)

### 5. Kibana (Puerto 5601)

**Función**: Interfaz de gestión y visualización para Elasticsearch.

## Flujo de Comunicación

### Frontend a Backend

El frontend se comunica con los microservicios a través del API Gateway utilizando Axios:

```javascript
// Configuración de Axios
const API = axios.create({
  baseURL: "http://localhost:8080/", // Gateway
});

// Ejemplo de comunicación con el servicio operador
export const getProductos = (categoria) => {
  if (categoria && categoria.trim() !== "") {
    return API.get(`/operador/productos?categoria=${encodeURIComponent(categoria)}`);
  }
  return API.get("/operador/productos");
};

// Ejemplo de comunicación con el servicio buscador
export const buscarProductos = (termino, size = 20) =>
  API.get(`/buscador/search`, { params: { q: termino, size } });
```

**Proceso de comunicación**:
1. El cliente hace una solicitud a través de Axios al API Gateway
2. El Gateway determina a qué microservicio enviar la solicitud según la ruta
3. El microservicio procesa la solicitud y devuelve una respuesta
4. La respuesta vuelve al cliente a través del Gateway

### Comunicación entre Microservicios

El servicio buscador se comunica con el servicio operador para indexar productos:

```java
// Ejemplo simplificado de código en IndexService.java
@Service
public class IndexService {
    private final RestTemplate restTemplate;
    
    @Autowired
    public IndexService(@Qualifier("lbRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    public void indexFromOperador() {
        // Obtener productos del servicio operador
        ResponseEntity<List<Producto>> response = 
            restTemplate.exchange(
                "http://localhost:8082/productos", 
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Producto>>() {}
            );
        
        List<Producto> productos = response.getBody();
        
        // Indexar productos en Elasticsearch
        // ...
    }
}
```

## Inyección de Dependencias

### Problema Común

El error que mencionaste:
```
Parameter 0 of constructor in com.buscador.service.IndexService required a single bean, but 2 were found:
        - lbRestTemplate: defined by method 'lbRestTemplate' in com.buscador.BuscadorApplication
        - externalRestTemplate: defined by method 'externalRestTemplate' in com.buscador.BuscadorApplication
```

**Explicación**: Este error ocurre porque hay dos beans de tipo `RestTemplate` definidos en la aplicación, y Spring no sabe cuál inyectar en el constructor de `IndexService`.

### Soluciones

#### 1. Usar @Qualifier

```java
@Service
public class IndexService {
    private final RestTemplate restTemplate;
    
    @Autowired
    public IndexService(@Qualifier("lbRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    // ...
}
```

**Explicación**: `@Qualifier` especifica exactamente qué bean debe ser inyectado.

#### 2. Usar @Primary

```java
@Bean
@Primary
public RestTemplate lbRestTemplate() {
    return new RestTemplate();
}

@Bean
public RestTemplate externalRestTemplate() {
    return new RestTemplate();
}
```

**Explicación**: `@Primary` indica a Spring que este bean debe ser el preferido cuando hay múltiples candidatos.

#### 3. Habilitar Retención de Nombres de Parámetros

En `pom.xml`:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <parameters>true</parameters>
    </configuration>
</plugin>
```

**Explicación**: Esta configuración mantiene los nombres de los parámetros en el bytecode, permitiendo a Spring usar los nombres para resolver ambigüedades.

**¿Por qué es importante?**: La inyección de dependencias adecuada asegura que cada componente reciba los recursos correctos que necesita para funcionar.

## Pruebas de Integración

### Pruebas del API Gateway

```bash
# Verificar que el Gateway enruta correctamente al servicio operador
curl http://localhost:8080/operador/productos

# Verificar que el Gateway enruta correctamente al servicio buscador
curl http://localhost:8080/buscador/search?q=creatina
```

### Pruebas del Servicio Operador

```bash
# Obtener todos los productos
curl http://localhost:8082/productos

# Obtener productos por categoría
curl http://localhost:8082/productos?categoria=suplementos

# Obtener un producto específico
curl http://localhost:8082/productos/1
```

### Pruebas del Servicio Buscador

```bash
# Realizar una búsqueda
curl http://localhost:8081/search?q=creatina

# Obtener sugerencias
curl http://localhost:8081/suggest?q=crea

# Indexar productos desde el servicio operador
curl -X POST http://localhost:8081/index-from-operador
```

### Pruebas de Elasticsearch

```bash
# Verificar que Elasticsearch está funcionando
curl http://localhost:9200

# Verificar el índice de productos
curl http://localhost:9200/productos
```

## Configuración de Puertos

| Servicio        | Puerto | Descripción                           |
|-----------------|--------|---------------------------------------|
| API Gateway     | 8080   | Punto de entrada para todas las APIs  |
| Servicio Buscador| 8081   | Servicio de búsqueda con Elasticsearch|
| Servicio Operador| 8082   | Servicio de gestión de productos     |
| Frontend        | 3000   | Aplicación React                      |
| MySQL           | 3306   | Base de datos relacional              |
| Elasticsearch   | 9200   | Motor de búsqueda                     |
| Kibana          | 5601   | Interfaz para Elasticsearch           |

**Configuración de puerto en Spring Boot**:
```yaml
server:
  port: 8081  # Definición en application.yml
```

**Configuración de puerto en React**:
```json
{
  "scripts": {
    "start": "react-scripts start",  // Usa puerto 3000 por defecto
    "start:custom": "PORT=4000 react-scripts start"  // Ejemplo para cambiar puerto
  }
}
```

## Despliegue con Docker

### Estructura del Docker Compose

El archivo `docker/docker-compose-backend.yml` define cómo se despliegan los servicios:

```yaml
version: '3'
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: password
      MYSQL_DATABASE: tienda
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql

  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.9.0
    environment:
      - discovery.type=single-node
      - ES_JAVA_OPTS=-Xms512m -Xmx512m
    ports:
      - "9200:9200"
    volumes:
      - es-data:/usr/share/elasticsearch/data

  kibana:
    image: docker.elastic.co/kibana/kibana:8.9.0
    environment:
      ELASTICSEARCH_HOSTS: http://elasticsearch:9200
    ports:
      - "5601:5601"
    depends_on:
      - elasticsearch

  operador-service:
    build: ../backend/operador/operador-service
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/tienda
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: password
    ports:
      - "8082:8082"
    depends_on:
      - mysql

  buscador:
    build: ../backend/buscador
    environment:
      ELASTICSEARCH_URI: http://elasticsearch:9200
    ports:
      - "8081:8081"
    depends_on:
      - elasticsearch

  cloud-gateway:
    build: ../backend/gateway/cloud-gateway
    environment:
      OPERADOR_URL: http://operador-service:8082
      BUSCADOR_URL: http://buscador:8081
    ports:
      - "8080:8080"
    depends_on:
      - operador-service
      - buscador

volumes:
  mysql-data:
  es-data:
```

### Proceso de Despliegue

```bash
# Navegar al directorio docker
cd docker

# Iniciar todos los servicios de backend
docker-compose -f docker-compose-backend.yml up -d

# Ver logs de un servicio específico
docker-compose -f docker-compose-backend.yml logs -f buscador

# Detener todos los servicios
docker-compose -f docker-compose-backend.yml down
```

### Verificación del Despliegue

```bash
# Verificar que todos los contenedores están en ejecución
docker ps

# Verificar logs de un contenedor específico
docker logs buscador
```

## Solución de Problemas Comunes

### 1. Error 404 en Búsquedas

**Problema**: Las peticiones al servicio de búsqueda devuelven 404.

**Posibles causas**:
- El servicio buscador no está en ejecución
- La ruta en el API Gateway no está configurada correctamente
- Elasticsearch no está funcionando

**Soluciones**:
1. Verificar que el servicio buscador esté ejecutándose:
   ```bash
   curl http://localhost:8081/actuator/health
   ```

2. Verificar la configuración del Gateway:
   ```yaml
   routes:
     - id: buscador
       uri: ${BUSCADOR_URL:http://localhost:8081}
       predicates:
         - Path=/buscador/**
       filters:
         - StripPrefix=1
   ```

3. Comprobar Elasticsearch:
   ```bash
   curl http://localhost:9200
   ```

### 2. Error 500 en Búsquedas

**Problema**: Las peticiones al servicio de búsqueda devuelven 500.

**Posibles causas**:
- Elasticsearch no está disponible
- El índice no existe o está mal configurado
- Error en la consulta

**Soluciones**:
1. Verificar conexión a Elasticsearch desde el servicio buscador:
   ```bash
   curl http://localhost:8081/actuator/env | grep elasticsearch
   ```

2. Crear o recrear el índice:
   ```bash
   curl -X POST http://localhost:8081/index-from-operador
   ```

### 3. Error de Inyección de Dependencias

**Problema**: Error al iniciar el servicio buscador debido a múltiples beans de tipo RestTemplate.

**Solución**: Implementar una de las soluciones mencionadas en la sección de Inyección de Dependencias.

### 4. Problemas de CORS

**Problema**: El frontend no puede comunicarse con el backend debido a errores de CORS.

**Solución**: Configurar CORS en el API Gateway:

```java
@Configuration
public class CorsConfiguration {
    @Bean
    public CorsWebFilter corsWebFilter() {
        org.springframework.web.cors.CorsConfiguration corsConfig = new org.springframework.web.cors.CorsConfiguration();
        corsConfig.setAllowedOrigins(Arrays.asList("*"));  // O especifica tus dominios
        corsConfig.setMaxAge(8000L);
        corsConfig.addAllowedMethod("*");
        corsConfig.addAllowedHeader("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}
```

### 5. Problemas con configuraciones obsoletas

**Problema**: Advertencias sobre propiedades de configuración obsoletas.

**Solución**: Actualizar las propiedades según las advertencias:

Cambiar:
```yaml
spring:
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
```

A:
```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          discovery:
            locator:
              enabled: true
```
