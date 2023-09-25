FROM amazoncorretto:20-alpine

RUN apk add --no-cache bash curl jq netcat-openbsd procps uuidgen

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