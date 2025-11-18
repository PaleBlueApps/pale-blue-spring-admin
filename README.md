# Pale Blue Spring Admin

A lightweight Spring Boot admin UI that auto-discovers your JPA entities and provides simple list and detail pages to browse data in your database. It is designed to be dropped into an existing Spring Boot application with minimal setup.

[![Maven Central](https://img.shields.io/maven-central/v/com.paleblueapps/springadmin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/com.paleblueapps/springadmin)

## Key features
- Zero controller code in your app – auto-configuration wires everything
- Auto-discovers JPA entities via the JPA metamodel
- Thymeleaf-based UI for list and detail views
- Configurable base path, title, and pagination

## Requirements
- Spring Boot 3.3+
- Spring Web
- Spring Data JPA (and a working JPA provider + datasource)

### 1) Add the dependency

Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.paleblueapps:springadmin:VERSION-HERE")
}
```

Maven

```xml
<dependency>
  <groupId>com.paleblueapps</groupId>
  <artifactId>springadmin</artifactId>
  <version>VERSION-HERE</version>
</dependency>
```

### 2) Configure properties (application.yml or application.properties)
The library is enabled by default. You can control the base path, UI title, and pagination.

#### application.properties example
```properties
spring.data.admin.enabled=true
spring.data.admin.base-path=/admin
spring.data.admin.ui.title=My Admin
spring.data.admin.pagination.default-size=25
spring.data.admin.pagination.max-size=200
```

#### application.yml example:
```yaml
spring:
  data:
    admin:
      enabled: true
      base-path: /admin
      ui:
        title: My Admin
      pagination:
        default-size: 25
        max-size: 200
```

### 3) Ensure JPA is set up
- Add your entities annotated with @Entity
- Configure a datasource and JPA provider (e.g., Hibernate)
- The admin UI discovers entities from the JPA metamodel. Entities must have a single @Id field.

### 4) Run and access the UI
- Start your Spring Boot app
- Navigate to http://localhost:8080/admin (or whatever `spring.data.admin.base-path` you configured)

### 5) Securing the admin UI
By default, the endpoints are standard MVC controllers mounted under spring.data.admin.base-path (default /admin). With Spring Security, you can restrict access, e.g.:

Kotlin DSL example (Spring Security 6):
```kotlin
@Bean
fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
    http
        .authorizeHttpRequests { auth ->
            auth
                .requestMatchers("/admin/**").hasRole("ADMIN") // match your configured base path
                .anyRequest().permitAll() // To allow without authentication NOTE: use your preferred authentication mechanism here
        }
    return http.build()
}
```

If you change the base path, update the matcher accordingly.

## Run the example project

Clone the spring admin project to your desired location. The project contains a module named `example` which servers as a demo app.

The example project is a simple Spring Boot app that uses the spring admin plugin. It has a single entity `User`.

Run the command `./gradlew bootRun` from the spring admin's root directory.

You can also run the example project using IntelliJ IDEA's graphical interface. It first requires a manual environment variable setup. Add an environment variable `DB_PATH` with value `example/data/app.db` and save it. Run the `ExampleApplication` from the GUI.

Once the example project runs, go to your web browser and navigate to `http://localhost:8080/admin` to access the admin UI.

> [!NOTE]
> If you encounter an error `Caused by: org.sqlite.SQLiteException: [SQLITE_CONSTRAINT_PRIMARYKEY] A PRIMARY KEY constraint failed (UNIQUE constraint failed: users.id)` when running the app, navigate to `example/data` directory and delete the `app.db` file. Then run the app again.
