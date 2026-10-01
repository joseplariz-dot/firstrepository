FROM eclipse-temurin:17-jdk

WORKDIR /app

# Copy all project files into /app
COPY . .

# Download the PostgreSQL JDBC driver directly using curl
RUN curl -o postgresql-42.7.2.jar https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.2/postgresql-42.7.2.jar

# Compile Main.java
RUN javac -cp postgresql-42.7.2.jar Main.java

# Run Java application
CMD ["sh", "-c", "java -cp .:postgresql-42.7.2.jar Main"]
