FROM openjdk:11.0.11-jre

RUN apt-get update \
    && apt-get install -y netcat-openbsd procps uuid-runtime jq \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/app

RUN curl -fsSL https://github.com/shellspec/shellspec/archive/refs/heads/master.zip -o ./shellspec.zip && \
    unzip ./shellspec.zip && \
    ls -l ${PWD}/shellspec-master && \
    ln -s ${PWD}/shellspec-master/shellspec /bin/shellspec && \
    shellspec -v && \
    rm -rf ./shellspec.zip
RUN shellspec --init

COPY ./build/libs/shellspec-remote-all-*.jar ./app.jar
COPY ./bridge.sh .

COPY ./docker/entrypoint.sh .
RUN chmod +x ./entrypoint.sh

ENTRYPOINT ["/opt/app/entrypoint.sh"]