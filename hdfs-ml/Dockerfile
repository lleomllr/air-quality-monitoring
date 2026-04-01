FROM bde2020/hadoop-base:2.0.0-hadoop3.2.1-java8

ENV HDFS_CONF_dfs_namenode_name_dir=file:///hadoop/dfs/name
RUN mkdir -p /hadoop/dfs/name
VOLUME /hadoop/dfs/name

COPY init-hdfs.sh /init-hdfs.sh
RUN chmod a+x /init-hdfs.sh

EXPOSE 9870

CMD ["/init-hdfs.sh"]