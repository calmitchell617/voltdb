cd \
    && sudo apt-get update \
    && DEBIAN_FRONTEND=noninteractive sudo apt-get install -y ant build-essential ant-optional openjdk-8-jdk-headless python3 python2 cmake valgrind ntp ccache git python-setuptools \
    && export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 \
    && export PATH=$PATH:JAVA_HOME \
    && echo 'export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64' >> ~/.bashrc \
    && echo 'export PATH=$PATH:JAVA_HOME' >> ~/.bashrc \
    && git clone https://github.com/calmitchell617/voltdb.git \
    && cd voltdb \
    && ant clean \
    && ant \
    && export PATH=$PATH:/home/ubuntu/voltdb/bin \
    && echo 'export PATH=$PATH:/home/ubuntu/voltdb/bin' >> ~/.bashrc