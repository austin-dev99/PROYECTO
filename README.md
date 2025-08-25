# Proyecto de Tienda con Microservicios

Este proyecto implementa una tienda en línea utilizando una arquitectura de microservicios con Spring Boot para el backend y React para el frontend.

## Estructura del Proyecto

- **Frontend**: Aplicación React para la interfaz de usuario
- **Backend**: Microservicios implementados con Spring Boot
  - **API Gateway**: Enruta las peticiones a los diferentes microservicios
  - **Servicio Operador**: Gestiona datos de productos almacenados en MySQL
  - **Servicio Buscador**: Proporciona búsquedas avanzadas usando Elasticsearch
  - **Eureka Server**: Registro y descubrimiento de servicios (opcional)

## Componentes del Sistema

### API Gateway (puerto 8080)

El API Gateway está implementado utilizando Spring Cloud Gateway y actúa como el punto de entrada único para todas las solicitudes. Enruta las peticiones a los microservicios correspondientes basándose en la ruta.

Configuración en `application.yml`:
```yaml
server:
  port: 8080

spring:
  application:
    name: cloud-gateway
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

### Servicio de Búsqueda (puerto 8081)

Este servicio utiliza Elasticsearch para proporcionar funcionalidades de búsqueda avanzada, sugerencias y facetas.

Configuración en `application.yml`:
```yaml
server:
  port: 8081

spring:
  application:
    name: buscador
    elasticsearch:
      uris: ${ELASTICSEARCH_URI:http://elasticsearch:9200}

elasticsearch:
  host: ${ELASTICSEARCH_URI:http://elasticsearch:9200}
```

Principales endpoints:
- `/search`: Búsqueda de productos con palabras clave
- `/suggest`: Autocompletado para el buscador
- `/facets`: Obtener categorías para filtros
- `/index-from-operador`: Indexar productos desde el servicio operador a Elasticsearch

### Servicio Operador (puerto 8082)

Gestiona los datos principales de los productos almacenados en MySQL.

Configuración en `application.yml`:
```yaml
server:
  port: 8082
spring:
  application:
    name: operador
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:mysql://localhost:3306/tienda}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
```

Principales endpoints:
- `/productos`: Obtener todos los productos
- `/productos?categoria={categoria}`: Filtrar productos por categoría
- `/productos/{id}`: Obtener un producto específico por ID

## Integración con Elasticsearch y Kibana

### Elasticsearch

Elasticsearch es un motor de búsqueda y análisis distribuido, basado en Apache Lucene. En este proyecto, se utiliza para:

1. **Indexación de productos**: Los productos almacenados en MySQL se indexan en Elasticsearch para facilitar búsquedas rápidas y avanzadas.
2. **Búsqueda por texto**: Permite buscar productos por términos parciales o completos.
3. **Facetas y filtros**: Proporciona agregaciones para filtrar por categorías.
4. **Autocompletado**: Sugiere términos mientras el usuario escribe en el buscador.

La configuración de Elasticsearch se hace mediante el archivo `mapping.json` que define la estructura de los índices.

### Kibana

Kibana es una plataforma de visualización y gestión para Elasticsearch. En este proyecto:

1. **Monitoreo**: Supervisa el rendimiento y estado de Elasticsearch.
2. **Gestión de índices**: Permite crear, modificar y eliminar índices.
3. **Desarrollo de consultas**: Facilita probar y depurar consultas a Elasticsearch.
4. **Visualizaciones**: Crea gráficos y paneles para analizar datos.

### Migración de datos JSON a Elasticsearch

La migración desde archivos JSON locales a Elasticsearch se realiza mediante:

1. El servicio buscador proporciona un endpoint `/index-from-operador` que:
   - Se conecta al servicio operador para obtener los productos
   - Transforma los datos al formato de Elasticsearch
   - Indexa los productos en Elasticsearch

Los datos en la carpeta `frontend/APP/src/data` ya no se utilizarán directamente una vez que se completa la migración a Elasticsearch, ya que todos los productos estarán disponibles a través de los servicios.

## Frontend y API

### Integración con Axios

El frontend se comunica con los microservicios a través del API Gateway utilizando Axios.

Archivo `api.js`:
```javascript
import axios from "axios";

const API = axios.create({
  baseURL: "http://localhost:8080/", // Gateway
});

// Operador (MySQL)
export const getProductos = (categoria) => {
  if (categoria && categoria.trim() !== "") {
    return API.get(`/operador/productos?categoria=${encodeURIComponent(categoria)}`);
  }
  return API.get("/operador/productos");
};

export const getProductoById = (id) =>
  API.get(`/operador/productos/${id}`);

// Buscador (Elasticsearch)
export const buscarProductos = (termino, size = 20) =>
  API.get(`/buscador/search`, { params: { q: termino, size } });

export const suggestProductos = (prefix) =>
  API.get(`/buscador/suggest`, { params: { q: prefix } });

export const facetsCategorias = () =>
  API.get(`/buscador/facets`);
```

### Uso en los componentes React

Para utilizar las funciones de API en los componentes React:

1. Importar las funciones necesarias:
   ```javascript
   import { buscarProductos, getProductos } from '../api';
   ```

2. Utilizar en componentes funcionales con hooks:
   ```javascript
   useEffect(() => {
     const fetchProductos = async () => {
       try {
         const response = await getProductos(categoria);
         setProductos(response.data);
       } catch (error) {
         console.error("Error al cargar productos:", error);
       }
     };
     
     fetchProductos();
   }, [categoria]);
   ```

3. Para búsquedas:
   ```javascript
   const handleSearch = async (searchTerm) => {
     try {
       const response = await buscarProductos(searchTerm);
       setResultados(response.data);
     } catch (error) {
       console.error("Error en búsqueda:", error);
     }
   };
   ```

## Despliegue Local

### Puertos para pruebas locales

- **API Gateway**: http://localhost:8080
- **Servicio Buscador**: http://localhost:8081
- **Servicio Operador**: http://localhost:8082
- **Frontend**: http://localhost:3000
- **Elasticsearch**: http://localhost:9200
- **Kibana**: http://localhost:5601

### Despliegue con Docker

El proyecto está configurado para desplegarse con Docker Compose:

```bash
# Iniciar todos los servicios de backend
docker-compose -f docker/docker-compose-backend.yml up -d

# Iniciar el frontend
cd frontend/APP
npm start
```

## Solución de Problemas Comunes

### Error 404 en Búsquedas

Si encuentras errores 404 al hacer búsquedas:

1. Verifica que el servicio buscador esté ejecutándose correctamente
2. Asegúrate de que la ruta en el API Gateway esté configurada correctamente
3. Comprueba que Elasticsearch esté funcionando: `curl http://localhost:9200`

### Error de Inyección de Dependencias

El error:
```
Parameter 0 of constructor in com.buscador.service.IndexService required a single bean, but 2 were found:
        - lbRestTemplate: defined by method 'lbRestTemplate' in com.buscador.BuscadorApplication
        - externalRestTemplate: defined by method 'externalRestTemplate' in com.buscador.BuscadorApplication
```

Solución:
1. Usa la anotación `@Qualifier` para especificar qué bean debe inyectarse:
   ```java
   @Autowired
   public IndexService(@Qualifier("lbRestTemplate") RestTemplate restTemplate) {
       this.restTemplate = restTemplate;
   }
   ```
   
2. O marca uno de los beans como primario:
   ```java
   @Bean
   @Primary
   public RestTemplate lbRestTemplate() {
       return new RestTemplate();
   }
   ```

3. Asegúrate de compilar con la opción `-parameters` para retener los nombres de parámetros:
   ```xml
   <plugin>
       <groupId>org.apache.maven.plugins</groupId>
       <artifactId>maven-compiler-plugin</artifactId>
       <configuration>
           <parameters>true</parameters>
       </configuration>
   </plugin>
   ```

### Error de conexión a Elasticsearch

Si recibes el error "No instances available for localhost", verifica:

1. Que Elasticsearch esté en ejecución
2. Que la configuración de URL sea correcta en `application.yml`
3. Si usas Docker, asegúrate de que la red permita la comunicación entre contenedores

### Problemas al iniciar la aplicación

Si tienes errores al iniciar la aplicación con mensajes sobre configuraciones obsoletas:

1. Actualiza las propiedades de configuración como se indica en los mensajes de advertencia
2. Para Spring Cloud Gateway, usa las nuevas propiedades (ejemplo: cambiar `spring.cloud.gateway.discovery.locator.enabled` a `spring.cloud.gateway.server.webflux.discovery.locator.enabled`)
