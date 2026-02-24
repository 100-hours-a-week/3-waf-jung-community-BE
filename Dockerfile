# Stage 1: Build Stage
FROM eclipse-temurin:21-jdk-alpine AS builder

# - Gradle 빌드를 위해 javac 컴파일러 필요
WORKDIR /build

# Gradle Wrapper와 의존성 파일 복사
# - Docker 레이어 캐싱 활용
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Gradle Wrapper 실행 권한 부여
# 의존성 다운로드 (캐시 레이어)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# 소스 코드 복사
# - 소스코드 변경이 가장 빈번함
# - 이 레이어만 재빌드하면 됨
# - 의존성 다운로드 레이어는 캐시 유지
COPY src src

# JAR 파일 빌드
RUN ./gradlew bootJar --no-daemon

# JAR 파일 이름 통일
# - Gradle은 버전 포함: community-0.0.1-SNAPSHOT.jar
# - ENTRYPOINT에서 고정된 이름 사용 가능
RUN mv build/libs/*.jar app.jar


# Stage 2: Runtime Stage
FROM eclipse-temurin:21-jre-alpine

# - 실행만 하므로 javac 컴파일러 불필요
# - 이미지 크기 74MB 절감 (154MB → 80MB)

WORKDIR /app

# 보안을 위한 비root 사용자 생성
RUN addgroup -S spring && \
    adduser -S spring -G spring

# Build Stage에서 빌드된 JAR 파일만 복사
COPY --from=builder --chown=spring:spring /build/app.jar app.jar

# 보안: Non-root 실행
# 이후 모든 명령어는 spring 사용자 권한으로 실행
USER spring

# 컨테이너 실행 시 사용할 포트 (문서화 목적)
# - docker run -p 8080:8080으로 실제 포트 매핑 필요
EXPOSE 8080

# 애플리케이션 실행
# JVM 메모리 설정:
# - UseContainerSupport: 컨테이너 메모리 제한 인식
# - MaxRAMPercentage=75.0: 컨테이너 메모리의 75%를 힙으로 사용
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]
