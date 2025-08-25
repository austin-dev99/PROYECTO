# builder
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app
COPY . .
# evita Permission denied dentro del contenedor
RUN chmod +x mvnw
RUN ./mvnw -q -DskipTests package

# runtime
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

# puerto del servicio (OPERADOR = 8082)
EXPOSE 8082

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
