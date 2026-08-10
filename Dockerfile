# 仅用于下载、解压 Playwright Linux 驱动
FROM maven:3.9-eclipse-temurin-17 AS playwright-driver
ARG PLAYWRIGHT_VERSION=1.56.0
RUN mvn -q dependency:get \
    -Dartifact=com.microsoft.playwright:driver-bundle:${PLAYWRIGHT_VERSION}:jar \
    && mkdir -p /opt/playwright-driver \
    && cd /opt/playwright-driver \
    && jar xf /root/.m2/repository/com/microsoft/playwright/driver-bundle/${PLAYWRIGHT_VERSION}/driver-bundle-${PLAYWRIGHT_VERSION}.jar driver/linux

# 必须与 pom.xml 中 playwright 1.56.0 保持一致
FROM mcr.microsoft.com/playwright/java:v1.56.0-noble

USER root

# 只复制 Linux x64 的 node + package，不带全平台 driver-bundle
COPY --from=playwright-driver /opt/playwright-driver/driver/linux/ /opt/playwright-driver/
RUN chmod +x /opt/playwright-driver/node

# 项目以有头浏览器运行，容器中使用 Xvfb 提供虚拟显示器, 安装Xvfb和x11vnc
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    xvfb \
    x11vnc \
    novnc \
    websockify \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /TiktokSparkFlow

#把之前构建的 jar 包复制过来
COPY ./target/*.jar ./app.jar

# 容器时区与项目时间格式保持一致
ENV TZ=Asia/Shanghai
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Shanghai -Dplaywright.cli.dir=/opt/playwright-driver"
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

# Profile、好友缓存、任务、发送历史、运行配置和日志都应挂载到宿主机
RUN mkdir -p ./data ./logs

# 使用自定义启动脚本，ENTRYPOINT无法将"-screen 0 1600x1000x24"作为单独的一个参数传递给 xvfb-run
COPY start.sh ./start.sh

RUN chmod +x ./start.sh

EXPOSE 8080 6080

ENTRYPOINT ["sh","./start.sh"]

# ENTRYPOINT exec xvfb-run -a -s "-screen 0 1600x1000x24" java -jar /TiktokSparkFlow/app.jar
