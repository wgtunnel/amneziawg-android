FROM golang:1.24.4 as awg
COPY . /awg
WORKDIR /awg
RUN go mod download && \
    go mod verify && \
    go build -ldflags '-linkmode external -extldflags "-fno-PIC -static"' -v -o /usr/bin

FROM alpine:3.19
ARG AWGTOOLS_RELEASE="1.0.20241018"

RUN apk add linux-headers build-base
COPY awg-tools /awg-tools
RUN pwd && ls -la / && ls -la /awg-tools
WORKDIR /awg-tools/src
# RUN ls -la && pwd && ls awg-tools
RUN make
RUN mkdir -p build && \
    cp wg ./build/awg && \
    cp wg-quick/linux.bash ./build/awg-quick

RUN cp build/awg /usr/bin/awg
RUN cp build/awg-quick /usr/bin/awg-quick

RUN apk --no-cache add iproute2 iptables bash && \
    cd /usr/bin/ && \
    # wget https://github.com/amnezia-vpn/amneziawg-tools/releases/download/v${AWGTOOLS_RELEASE}/alpine-3.19-amneziawg-tools.zip && \
    # unzip -j alpine-3.19-amneziawg-tools.zip && \
    chmod +x /usr/bin/awg /usr/bin/awg-quick && \
    ln -s /usr/bin/awg /usr/bin/wg && \
    ln -s /usr/bin/awg-quick /usr/bin/wg-quick
COPY --from=awg /usr/bin/amneziawg-go /usr/bin/amneziawg-go
