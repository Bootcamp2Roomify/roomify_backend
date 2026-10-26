# Roomify API + CV service in one Hugging Face Docker Space (free CPU Basic: 2 vCPU, 16 GB, no card).
# The CV service listens on 127.0.0.1 only: it has no authentication, so it is never exposed.
# Build context = the folder made by build-space.sh (backend/ and cv/ sources side by side).
FROM maven:3.9.16-eclipse-temurin-21 AS backend
WORKDIR /build
COPY backend/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
RUN mvn -B -q package -DskipTests && cp target/roomify-backend-*.jar /build/app.jar

FROM eclipse-temurin:21.0.12_8-jre AS jre

FROM python:3.14.9-slim
# OpenCV (pulled in by ultralytics) needs these shared libraries at runtime.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=jre /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk PATH=/opt/java/openjdk/bin:$PATH

WORKDIR /app/cv
COPY cv/requirements.txt cv/requirements.lock ./
RUN pip install --no-cache-dir --extra-index-url https://download.pytorch.org/whl/cpu \
        -r requirements.txt -c requirements.lock
COPY cv/app ./app
ENV YOLO_CONFIG_DIR=/tmp/ultralytics
# Bake the weights into the image so the Space starts without a network download.
RUN python -c "from ultralytics import YOLO; YOLO('yolo11n.pt')"

COPY --from=backend /build/app.jar /app/app.jar
COPY start.sh /app/start.sh
# Hugging Face runs the container as uid 1000.
RUN useradd --create-home --uid 1000 roomify && chown -R roomify /app && chmod +x /app/start.sh
USER roomify

# Non-secret settings. Secrets (SPRING_DATASOURCE_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET) are Space secrets.
ENV PORT=7860 STORAGE_TYPE=database AI_DEMO_MODE=true \
    VISION_SERVICE_BASE_URL=http://127.0.0.1:8000 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=25"
EXPOSE 7860
CMD ["/app/start.sh"]
