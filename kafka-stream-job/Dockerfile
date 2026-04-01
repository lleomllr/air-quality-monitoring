FROM confluentinc/cp-kafka-connect:7.9.4
USER root
RUN /usr/bin/confluent-hub install --no-prompt confluentinc/kafka-connect-hdfs3:3.0.4
RUN /usr/bin/confluent-hub install --no-prompt kaliy/kafka-connect-rss:0.1.1
RUN /usr/bin/confluent-hub install --no-prompt confluentinc/kafka-connect-jdbc:10.9.2
USER appuser