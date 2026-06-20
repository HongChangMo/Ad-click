# 멀티모듈 프로젝트 세팅 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Gradle 멀티모듈 프로젝트 뼈대를 구성하고 `./gradlew build` 가 통과하는 상태를 만든다.

**Architecture:** `apps/ad-api` (실행 진입점), `apps/ad-management` (광고 관리 도메인), `apps/ad-click` (클릭 집계 도메인) 3개 모듈. `ad-click → ad-management` 단방향 의존. 각 도메인 모듈은 interfaces / application / domain / infrastructure 4계층 패키지 구조를 가진다.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Gradle 8.8 (Groovy DSL), MySQL, Spring Data Redis (Valkey 연동)

---

## Task 1: Git 초기화 및 .gitignore 설정

**Files:**
- Create: `.gitignore`

**Step 1: Git 초기화**

```bash
cd /Users/zzangmo/project/AdClick
git init
```
Expected: `Initialized empty Git repository`

**Step 2: .gitignore 생성**

```
.gradle/
build/
.idea/
*.iml
out/
.DS_Store
**/application-local.yml
```

**Step 3: 커밋**

```bash
git add .gitignore
git commit -m "chore: init repository"
```

---

## Task 2: Gradle Wrapper 및 루트 빌드 파일 설정

**Files:**
- Create: `settings.gradle`
- Create: `build.gradle`
- Create: `gradle/wrapper/gradle-wrapper.properties` (wrapper 생성 후 자동 생성)

**Step 1: Gradle Wrapper 생성**

```bash
gradle wrapper --gradle-version 8.8
```
Expected: `gradle/`, `gradlew`, `gradlew.bat` 생성됨

**Step 2: settings.gradle 작성**

```groovy
rootProject.name = 'adclick'

include 'apps:ad-api'
include 'apps:ad-management'
include 'apps:ad-click'
```

**Step 3: 루트 build.gradle 작성**

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5' apply false
    id 'io.spring.dependency-management' version '1.1.6' apply false
}

subprojects {
    apply plugin: 'java'
    apply plugin: 'io.spring.dependency-management'

    group = 'com.adclick'
    version = '0.0.1-SNAPSHOT'

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    repositories {
        mavenCentral()
    }

    dependencyManagement {
        imports {
            mavenBom "org.springframework.boot:spring-boot-dependencies:3.3.5"
        }
    }

    dependencies {
        testImplementation 'org.springframework.boot:spring-boot-starter-test'
    }

    test {
        useJUnitPlatform()
    }
}
```

**Step 4: 빌드 확인 (모듈 없어도 루트는 통과해야 함)**

```bash
./gradlew build
```
Expected: `BUILD SUCCESSFUL` (모듈 디렉토리 없어서 경고 나올 수 있음)

**Step 5: 커밋**

```bash
git add settings.gradle build.gradle gradlew gradlew.bat gradle/
git commit -m "chore: configure gradle multi-module root"
```

---

## Task 3: ad-management 모듈 설정

**Files:**
- Create: `apps/ad-management/build.gradle`
- Create: `apps/ad-management/src/main/java/com/adclick/management/` (패키지 구조)
- Create: `apps/ad-management/src/main/resources/` (빈 디렉토리)
- Create: `apps/ad-management/src/test/java/com/adclick/management/`

**Step 1: 디렉토리 생성**

```bash
mkdir -p apps/ad-management/src/main/java/com/adclick/management/{interfaces,application,domain,infrastructure}
mkdir -p apps/ad-management/src/main/resources
mkdir -p apps/ad-management/src/test/java/com/adclick/management
```

**Step 2: apps/ad-management/build.gradle 작성**

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    runtimeOnly 'com.mysql:mysql-connector-j'
}
```

**Step 3: 모듈 빌드 확인**

```bash
./gradlew :apps:ad-management:build
```
Expected: `BUILD SUCCESSFUL`

**Step 4: 커밋**

```bash
git add apps/ad-management/
git commit -m "chore: add ad-management module"
```

---

## Task 4: ad-click 모듈 설정

**Files:**
- Create: `apps/ad-click/build.gradle`
- Create: `apps/ad-click/src/main/java/com/adclick/click/` (패키지 구조)
- Create: `apps/ad-click/src/main/resources/`
- Create: `apps/ad-click/src/test/java/com/adclick/click/`

**Step 1: 디렉토리 생성**

```bash
mkdir -p apps/ad-click/src/main/java/com/adclick/click/{interfaces,application,domain,infrastructure}
mkdir -p apps/ad-click/src/main/resources
mkdir -p apps/ad-click/src/test/java/com/adclick/click
```

**Step 2: apps/ad-click/build.gradle 작성**

```groovy
dependencies {
    implementation project(':apps:ad-management')
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
}
```

**Step 3: 의존 방향 검증 — ad-management 가 ad-click 을 모른다**

`apps/ad-management/build.gradle` 에 `ad-click` 참조가 없는지 눈으로 확인합니다.

**Step 4: 모듈 빌드 확인**

```bash
./gradlew :apps:ad-click:build
```
Expected: `BUILD SUCCESSFUL`

**Step 5: 커밋**

```bash
git add apps/ad-click/
git commit -m "chore: add ad-click module"
```

---

## Task 5: ad-api 모듈 설정 및 Spring Boot Application 클래스 생성

**Files:**
- Create: `apps/ad-api/build.gradle`
- Create: `apps/ad-api/src/main/java/com/adclick/AdClickApplication.java`
- Create: `apps/ad-api/src/main/resources/application.yml`
- Create: `apps/ad-api/src/test/java/com/adclick/AdClickApplicationTest.java`

**Step 1: 디렉토리 생성**

```bash
mkdir -p apps/ad-api/src/main/java/com/adclick/config
mkdir -p apps/ad-api/src/main/resources
mkdir -p apps/ad-api/src/test/java/com/adclick
```

**Step 2: apps/ad-api/build.gradle 작성**

```groovy
apply plugin: 'org.springframework.boot'

dependencies {
    implementation project(':apps:ad-management')
    implementation project(':apps:ad-click')
    implementation 'org.springframework.boot:spring-boot-starter'
    runtimeOnly 'com.mysql:mysql-connector-j'
}
```

**Step 3: AdClickApplication.java 작성**

`apps/ad-api/src/main/java/com/adclick/AdClickApplication.java`

```java
package com.adclick;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AdClickApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdClickApplication.class, args);
    }
}
```

> `@SpringBootApplication` 이 `com.adclick` 패키지 기준으로 하위 모든 패키지를 컴포넌트 스캔합니다.
> `com.adclick.management`, `com.adclick.click` 모두 스캔 대상에 포함됩니다.

**Step 4: application.yml 작성**

`apps/ad-api/src/main/resources/application.yml`

```yaml
spring:
  application:
    name: ad-click-api

  datasource:
    url: jdbc:mysql://localhost:3306/adclick?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8
    username: adclick
    password: adclick
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
        format_sql: true

  data:
    redis:
      host: localhost
      port: 6379

server:
  port: 8080
```

**Step 5: 컨텍스트 로드 테스트 작성 (DB/Valkey 없이 통과하도록 슬라이스 테스트)**

`apps/ad-api/src/test/java/com/adclick/AdClickApplicationTest.java`

```java
package com.adclick;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=" +
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
})
class AdClickApplicationTest {

    @Test
    void contextLoads() {
    }
}
```

**Step 6: 테스트 실행 (RED 확인 — 아직 통과 못할 수도 있음)**

```bash
./gradlew :apps:ad-api:test
```

Expected: `BUILD SUCCESSFUL` with `contextLoads PASSED`

**Step 7: 전체 빌드 확인**

```bash
./gradlew build
```
Expected: `BUILD SUCCESSFUL`

**Step 8: 커밋**

```bash
git add apps/ad-api/
git commit -m "chore: add ad-api module with Spring Boot application"
```

---

## Task 6: 모듈 간 의존성 검증 테스트

**Goal:** `ad-management` 가 `ad-click` 을 참조하면 컴파일 오류가 나는지 확인합니다.

**Step 1: 검증용 임시 코드 작성**

`apps/ad-management/src/test/java/com/adclick/management/DependencyDirectionTest.java`

```java
package com.adclick.management;

import org.junit.jupiter.api.Test;

class DependencyDirectionTest {

    @Test
    void adManagement_should_not_depend_on_adClick() {
        // ad-management 클래스패스에서 ad-click 패키지 클래스가 로드되면 안 됨
        try {
            Class.forName("com.adclick.click.application.ClickFacade");
            throw new AssertionError("ad-management must not depend on ad-click");
        } catch (ClassNotFoundException e) {
            // 정상: ad-click 클래스가 클래스패스에 없어야 함
        }
    }
}
```

**Step 2: 테스트 실행**

```bash
./gradlew :apps:ad-management:test
```
Expected: `DependencyDirectionTest > adManagement_should_not_depend_on_adClick PASSED`

**Step 3: 커밋**

```bash
git add apps/ad-management/src/test/
git commit -m "test: verify ad-management does not depend on ad-click"
```

---

## 완료 기준

- [ ] `./gradlew build` 가 오류 없이 통과한다
- [ ] `./gradlew :apps:ad-api:test` 에서 `contextLoads` 가 통과한다
- [ ] `./gradlew :apps:ad-management:test` 에서 의존 방향 검증 테스트가 통과한다
- [ ] `apps/ad-management`, `apps/ad-click` 각 모듈에 4계층 패키지 구조가 존재한다
- [ ] `feature_list.json` 의 모든 feature 가 여전히 `not_started` 상태다 (세팅은 feature가 아님)
- [ ] `harness/claude-progress.md` 에 Session Record 가 추가됐다
