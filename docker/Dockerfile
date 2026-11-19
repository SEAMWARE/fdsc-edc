# -buster is required to have apt available
FROM eclipse-temurin:24_36-jre-alpine

# Optional JVM arguments, such as memory settings
ARG JVM_ARGS=""
ARG JAR

RUN apk --no-cache add curl

WORKDIR /app

COPY ${JAR} edc.jar


# Use "exec" for graceful termination (SIGINT) to reach JVM.
# ARG can not be used in ENTRYPOINT so storing value in an ENV variable
ENV ENV_JVM_ARGS=$JVM_ARGS

# Java4Shell (CVE-2026-114938): the AVX-512 VBMI Base64 intrinsic in OpenJDK
# >= 17.0.6 miscalculates the output buffer size for input with unexpected
# padding and writes past the buffer. ContractOfferIdParser base64-decodes
# caller-supplied contract offer ids, so the vulnerable decoder is reachable
# from the API. The base image above is OpenJDK 24+36; branch 24 is end of
# life and will never receive the fix, so this flag is the mitigation until
# the runtime moves to a patched LTS (OpenJDK >= 25.0.5.1).
ENV JAVA4SHELL_MITIGATION="-XX:-UseBASE64Intrinsics"

# use the "exec" syntax so that SIGINT reaches the JVM -> graceful termination
CMD ["sh", "-c", "exec java $JAVA4SHELL_MITIGATION -Djava.security.egd=file:/dev/urandom -jar edc.jar --log-level=DEBUG"]
