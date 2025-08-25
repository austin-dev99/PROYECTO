# ===== builder =====
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app
COPY . .
# 💡 evita el "Permission denied"
RUN chmod +x mvnw
RUN ./mvnw -q -DskipTests package

# ===== runner =====
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
# cambia el puerto según el servicio
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]
