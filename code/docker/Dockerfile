FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY code/server /src
RUN if [ ! -f src/main/resources/application.yml ]; then cp src/main/resources/application.yml.example src/main/resources/application.yml; fi
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/solitaire-server.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
