# WebRTC Call Backend

A Spring Boot backend service for the WebRTC call application.

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher

## Getting Started

### Running the Application

1. Navigate to the project directory:
   ```bash
   cd java_backend
   ```

2. Build the project:
   ```bash
   mvn clean install
   ```

3. Run the application:
   ```bash
   mvn spring-boot:run
   ```

   Or run the JAR file:
   ```bash
   java -jar target/webrtc-call-backend-1.0.0.jar
   ```

The application will start on `http://localhost:8080`

### API Endpoints

- **Health Check**: `GET /api/health`
  - Returns the status of the backend service

### Database

The application uses H2 in-memory database for development. You can access the H2 console at:
- URL: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:webrtc_db`
- Username: `sa`
- Password: (leave empty)

### Configuration

- Development profile: `application-dev.properties`
- Production profile: `application-prod.properties`

To run with a specific profile:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## Project Structure

```
java_backend/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/webrtccall/
│   │   │       ├── WebRtcCallApplication.java
│   │   │       ├── config/
│   │   │       └── controller/
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/
└── pom.xml
```

## Technologies Used

- Spring Boot 3.2.0
- Spring Data JPA
- H2 Database (development)
- PostgreSQL (production ready)
- Maven

## Development

### Adding Dependencies

Edit `pom.xml` and add your dependencies, then run:
```bash
mvn clean install
```

### Building for Production

```bash
mvn clean package -Pprod
```

## License

This project is part of the WebRTC Call application.

