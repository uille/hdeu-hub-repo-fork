#
# Copyright (C) 2024 European Union
#  __
# Licensed under the EUPL, Version 1.2 or – as soon they will be approved by the
# European Commission – subsequent versions of the EUPL (the “Licence”);
# You may not use this work except in compliance with the Licence.
# You may obtain a copy of the Licence at:
# *
# https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
# *
# Unless required by applicable law or agreed to in writing, software distributed under
# the Licence is distributed on an “AS IS” basis, WITHOUT WARRANTIES OR CONDITIONS
# OF ANY KIND, either express or implied. See the Licence for the specific language
# governing permissions and limitations under the Licence.
#

FROM maven:3.9.9-eclipse-temurin-17 AS build
COPY ./ /usr/src/app
WORKDIR /usr/src/app
RUN mvn clean package -Dmaven.test.skip=true

FROM eclipse-temurin:17-jdk

ENV VERTICLE_FILE hdeu-hub-repo.jar
# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles
# Set the location of the logback file
ENV LOG_HOME $VERTICLE_HOME/logs
ENV LOG_FILENAME incognito.log
# Set the log level
ENV LOG_LEVEL_FILE INFO
ENV XDG_CONFIG_HOME $VERTICLE_HOME

EXPOSE 8080
EXPOSE 8085
EXPOSE 5000

RUN mkdir -p $VERTICLE_HOME/logs && groupadd vertx && useradd -g vertx vertx && chown -R vertx $VERTICLE_HOME && chmod -R g+w $VERTICLE_HOME

# Create log folder
#RUN mkdir $LOG_HOME && chown -R vertx $LOG_HOME && chmod -R g+w $LOG_HOME

# Copy your fat jar to the container
COPY --from=build /usr/src/app/target/$VERTICLE_FILE $VERTICLE_HOME/
#COPY misc/ $VERTICLE_HOME/misc/

USER vertx

# Launch the verticle
WORKDIR $VERTICLE_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["exec java $JAVA_OPTS -jar $VERTICLE_FILE"]
