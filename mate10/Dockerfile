# ============================================================
# 阶段 1：构建
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

COPY pom.xml .
RUN mvn -B dependency:go-offline

# 再复制源码并打包
COPY src ./src
RUN mvn -B clean package -DskipTests

# ============================================================
# 阶段 2：运行
# ============================================================
FROM eclipse-temurin:17-jre

WORKDIR /app

# OCR :安装 Tesseract和中文包
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-chi-sim \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /build/target/*.jar app.jar

# 时区
ENV TZ=Asia/Shanghai

#声明端口
EXPOSE 8080

#启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]

