FROM eclipse-temurin:17-jre
MAINTAINER youlai <youlaitech@163.com>

RUN /bin/cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo 'Asia/Shanghai' >/etc/timezone

VOLUME /tmp

ADD target/flowable-boot.jar app.jar

ENTRYPOINT ["java", "-Xmx256m", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app.jar"]

EXPOSE 8810
