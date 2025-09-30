# Monitoring Setup - Prometheus & Grafana

This guide explains how to use Prometheus and Grafana monitoring for your Spring Boot AREA_Back application.

## Configuration

### 1. Spring Boot Metrics
The application automatically exposes metrics via the `/actuator/prometheus` endpoint thanks to:
- `spring-boot-starter-actuator`
- `micrometer-registry-prometheus`

### 2. Monitoring Services

#### Prometheus
- **URL**: http://localhost:9090
- **Configuration**: `monitoring/prometheus.yml`
- Collects metrics from your application every 5 seconds

#### Grafana
- **URL**: http://localhost:3000
- **Login**: admin / admin123
- Pre-configured dashboard for Spring Boot

## Startup

```bash
# Start all services
docker-compose up -d

# Start your Spring Boot application
./gradlew bootRun
```

## Available Endpoints

- **Application**: http://localhost:8080
- **Prometheus Metrics**: http://localhost:8080/actuator/prometheus
- **Health Check**: http://localhost:8080/actuator/health
- **Prometheus UI**: http://localhost:9090
- **Grafana**: http://localhost:3000

## Available Metrics

### JVM Metrics
- `jvm_memory_used_bytes` - Memory usage
- `jvm_gc_pause_seconds` - Garbage Collector
- `jvm_threads_live_threads` - Active threads

### HTTP Metrics
- `http_server_requests_seconds` - HTTP response time
- `http_server_requests_seconds_count` - Number of requests
- `http_server_requests_seconds_sum` - Total request time

### Application Metrics
- `process_cpu_usage` - CPU usage
- `system_cpu_usage` - System CPU
- `process_uptime_seconds` - Uptime

## Grafana Dashboard

The dashboard includes:
1. **CPU Usage** - Processor usage
2. **JVM Memory Usage** - Memory usage by zone
3. **HTTP Request Rate** - Requests per second rate
4. **HTTP Response Time** - Response time (percentiles)

## Customization

### Adding custom metrics

```java
@Component
public class CustomMetrics {
    private final Counter customCounter;
    private final Timer customTimer;
    
    public CustomMetrics(MeterRegistry meterRegistry) {
        this.customCounter = Counter.builder("custom_operations_total")
            .description("Total custom operations")
            .register(meterRegistry);
            
        this.customTimer = Timer.builder("custom_operation_duration")
            .description("Custom operation duration")
            .register(meterRegistry);
    }
    
    public void recordOperation() {
        customCounter.increment();
        Timer.Sample sample = Timer.start();
        // ... your logic
        sample.stop(customTimer);
    }
}
```

### Creating alerts

Add alert rules in `monitoring/prometheus.yml`:

```yaml
rule_files:
  - "alerts.yml"
```

## Troubleshooting

### Prometheus cannot scrape metrics
- Check that your Spring Boot application is started
- Check the endpoint: http://localhost:8080/actuator/prometheus
- Check the Docker network configuration

### Grafana cannot connect to Prometheus
- Check that Prometheus is accessible via: http://prometheus:9090
- Check the logs: `docker-compose logs grafana`

### Missing metrics
- Restart the application after modifying the configuration
- Check application logs for Actuator errors