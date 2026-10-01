FROM eclipse-temurin:17-jdk

WORKDIR /app

# Copy all files into the container working directory (/app)
COPY . .

# Install wget and download the PostgreSQL JDBC driver
RUN apt-get update && apt-get install -y wget && \
    wget https://jdbc.postgresql.org/development/1200/postgresql-42.7.2.jar

# Compile Main.java using the JDBC driver
RUN javac -cp postgresql-42.7.2.jar Main.java

# Run the compiled application
CMD ["sh", "-c", "java -cp .:postgresql-42.7.2.jar Main"]


