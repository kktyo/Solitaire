FROM ghcr.io/cirruslabs/flutter:stable AS web
WORKDIR /front
COPY code/front /front
RUN git config --global --add safe.directory /front \
    && flutter config --no-analytics \
    && flutter pub get \
    && flutter create . --project-name solitaire --org com.solitaire --platforms=web \
    && flutter build web --release --dart-define=API_BASE_URL=

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY code/server /src
COPY --from=web /front/build/web /src/src/main/resources/static
RUN if [ ! -f src/main/resources/application.yml ]; then cp src/main/resources/application.yml.example src/main/resources/application.yml; fi
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/solitaire-server.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
