# Pale Blue Spring Admin

A lightweight Spring Boot admin UI that auto-discovers your JPA entities and provides simple list and detail pages to browse data in your database. It is designed to be dropped into an existing Spring Boot application with minimal setup.

[![Maven Central](https://img.shields.io/maven-central/v/com.paleblueapps/springadmin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/com.paleblueapps/springadmin)

## Key features
- Zero controller code in your app – auto-configuration wires everything
- Auto-discovers JPA entities via the JPA metamodel
- Thymeleaf-based UI for list and detail views
- Configurable base path, title, and pagination
- Optional index-page fragment slots for custom dashboard panels

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
spring.data.admin.ui.index-fragments.before-entities[0]=admin/my-panel :: content
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
        index-fragments:
          before-entities:
            - admin/my-panel :: content
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

### 5) Customizing the Admin UI

#### 5a) Add custom computed fields to your entities
You can add read-only "computed" fields to your entities using the `@AdminComputedField` annotation. These fields will appear in both the list and detail views.

```kotlin
import com.paleblueapps.springadmin.annotation.AdminComputedField

@Entity
class User(
    val firstName: String,
    val lastName: String
) {
    @AdminComputedField("Full Name")
    fun getFullName(): String = "$firstName $lastName"
}
```

The annotation can be applied to functions or property getters. The `name` parameter defines the label shown in the UI.

#### 5b) Add custom content to the admin index page
You can inject Thymeleaf fragments before or after the entity list on the dashboard without overriding `sda/index.html`.

**1. Configure the fragments in your `application.yml`**
```yaml
spring:
  data:
    admin:
      ui:
        index-fragments:
          before-entities:
            - admin/header-note :: content
          after-entities:
            - admin/footer-note :: content
```

**2. Create the fragment template**
Create a file at `src/main/resources/templates/admin/header-note.html`:
```html
<!doctype html>
<html xmlns:th="http://www.thymeleaf.org" lang="en">
  <body>
    <section th:fragment="content" class="my-4 p-4 bg-blue-50 border border-blue-200 rounded">
      <h2 class="text-lg font-bold">Custom dashboard panel</h2>
      <p th:text="${tokenCount}">0</p>
    </section>
  </body>
</html>
```

**3. Provide dynamic data (optional)**
If your fragment needs dynamic data, use a `@ControllerAdvice` targeting `AdminIndexController`.

```kotlin
@ControllerAdvice(assignableTypes = [AdminIndexController::class])
class AdminIndexAdvice {
    @ModelAttribute("tokenCount")
    fun tokenCount(): Long = 42
}
```

### 6) Securing the admin UI
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
The repository includes an `example` module that serves as a demo Spring Boot application.
It showcases how Spring Admin can represent complex database relationships in a clean, modern admin UI.

### Run via Gradle
- Navigate to the root directory of spring-admin.
- Run the following command:

```shell
./gradlew bootRun
```

### Run via IntelliJ IDEA
- Open the project in IntelliJ IDEA
- Run the `ExampleApplication` class from the IDE

### Accessing the Admin UI
- Once the application is running, navigate to `http:localhost:8080/admin` on your web browser.
