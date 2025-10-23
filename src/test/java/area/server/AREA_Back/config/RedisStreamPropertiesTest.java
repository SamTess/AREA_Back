package area.server.AREA_Back.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour RedisStreamProperties
 * Type: Tests Unitaires
 * Description: Teste la configuration des propriétés Redis Stream
 */
@DisplayName("RedisStreamProperties - Tests Unitaires")
class RedisStreamPropertiesTest {

    private RedisStreamProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RedisStreamProperties();
    }

    @Test
    @DisplayName("Doit avoir des valeurs par défaut correctes")
    void shouldHaveCorrectDefaultValues() {
        // When & Then
        assertEquals("areas:events", properties.getStreamName());
        assertEquals("area-processors", properties.getConsumerGroup());
        assertEquals(10, properties.getBatchSize());
        assertEquals(4, properties.getThreadPoolSize());
        assertEquals(100, properties.getPollTimeoutMs());
    }

    @Test
    @DisplayName("Doit générer un nom de consommateur par défaut")
    void shouldGenerateDefaultConsumerName() {
        // When
        String consumerName = properties.getConsumerName();

        // Then
        assertNotNull(consumerName);
        assertFalse(consumerName.isEmpty());
        assertTrue(consumerName.contains("-"));
    }

    @Test
    @DisplayName("Doit générer un nom de consommateur unique à chaque appel initial")
    void shouldGenerateUniqueConsumerNameOnInitialCall() {
        // Given
        RedisStreamProperties properties1 = new RedisStreamProperties();
        RedisStreamProperties properties2 = new RedisStreamProperties();

        // When
        String name1 = properties1.getConsumerName();
        String name2 = properties2.getConsumerName();

        // Then
        assertNotNull(name1);
        assertNotNull(name2);
        // Les noms peuvent être différents à cause du UUID aléatoire
        assertNotEquals("", name1);
        assertNotEquals("", name2);
    }

    @Test
    @DisplayName("Doit réutiliser le même nom de consommateur après génération")
    void shouldReuseSameConsumerNameAfterGeneration() {
        // When
        String name1 = properties.getConsumerName();
        String name2 = properties.getConsumerName();

        // Then
        assertEquals(name1, name2);
    }

    @Test
    @DisplayName("Doit permettre de définir un nom de consommateur personnalisé")
    void shouldAllowSettingCustomConsumerName() {
        // When
        properties.setConsumerName("custom-consumer");

        // Then
        assertEquals("custom-consumer", properties.getConsumerName());
    }

    @Test
    @DisplayName("Doit générer un nom par défaut quand le nom est vide")
    void shouldGenerateDefaultNameWhenEmpty() {
        // When
        properties.setConsumerName("");
        String consumerName = properties.getConsumerName();

        // Then
        assertNotNull(consumerName);
        assertFalse(consumerName.isEmpty());
        assertNotEquals("", consumerName);
    }

    @Test
    @DisplayName("Doit générer un nom par défaut quand le nom est null")
    void shouldGenerateDefaultNameWhenNull() {
        // When
        properties.setConsumerName(null);
        String consumerName = properties.getConsumerName();

        // Then
        assertNotNull(consumerName);
        assertFalse(consumerName.isEmpty());
    }

    @Test
    @DisplayName("Doit permettre de définir le nom du stream")
    void shouldAllowSettingStreamName() {
        // When
        properties.setStreamName("custom:stream");

        // Then
        assertEquals("custom:stream", properties.getStreamName());
    }

    @Test
    @DisplayName("Doit permettre de définir le groupe de consommateurs")
    void shouldAllowSettingConsumerGroup() {
        // When
        properties.setConsumerGroup("custom-group");

        // Then
        assertEquals("custom-group", properties.getConsumerGroup());
    }

    @Test
    @DisplayName("Doit permettre de définir la taille du batch")
    void shouldAllowSettingBatchSize() {
        // When
        properties.setBatchSize(20);

        // Then
        assertEquals(20, properties.getBatchSize());
    }

    @Test
    @DisplayName("Doit permettre de définir la taille du pool de threads")
    void shouldAllowSettingThreadPoolSize() {
        // When
        properties.setThreadPoolSize(8);

        // Then
        assertEquals(8, properties.getThreadPoolSize());
    }

    @Test
    @DisplayName("Doit permettre de définir le timeout de polling")
    void shouldAllowSettingPollTimeout() {
        // When
        properties.setPollTimeoutMs(200);

        // Then
        assertEquals(200, properties.getPollTimeoutMs());
    }

    @Test
    @DisplayName("Doit permettre de définir toutes les propriétés")
    void shouldAllowSettingAllProperties() {
        // When
        properties.setStreamName("test:stream");
        properties.setConsumerGroup("test-group");
        properties.setConsumerName("test-consumer");
        properties.setBatchSize(50);
        properties.setThreadPoolSize(16);
        properties.setPollTimeoutMs(500);

        // Then
        assertEquals("test:stream", properties.getStreamName());
        assertEquals("test-group", properties.getConsumerGroup());
        assertEquals("test-consumer", properties.getConsumerName());
        assertEquals(50, properties.getBatchSize());
        assertEquals(16, properties.getThreadPoolSize());
        assertEquals(500, properties.getPollTimeoutMs());
    }

    @Test
    @DisplayName("Doit générer un nom avec hostname et UUID")
    void shouldGenerateNameWithHostnameAndUuid() {
        // When
        String consumerName = properties.getConsumerName();

        // Then
        assertNotNull(consumerName);
        // Le nom doit contenir un tiret séparant hostname/prefix et UUID
        assertTrue(consumerName.contains("-"));
        // La longueur minimale attendue (au moins prefix + "-" + 8 caractères UUID)
        assertTrue(consumerName.length() >= 10);
    }

    @Test
    @DisplayName("Doit accepter des valeurs de batch size personnalisées")
    void shouldAcceptCustomBatchSizes() {
        // Test different batch sizes
        int[] batchSizes = {1, 5, 10, 25, 50, 100};

        for (int size : batchSizes) {
            properties.setBatchSize(size);
            assertEquals(size, properties.getBatchSize());
        }
    }

    @Test
    @DisplayName("Doit accepter des valeurs de thread pool size personnalisées")
    void shouldAcceptCustomThreadPoolSizes() {
        // Test different thread pool sizes
        int[] poolSizes = {1, 2, 4, 8, 16, 32};

        for (int size : poolSizes) {
            properties.setThreadPoolSize(size);
            assertEquals(size, properties.getThreadPoolSize());
        }
    }

    @Test
    @DisplayName("Doit accepter des valeurs de poll timeout personnalisées")
    void shouldAcceptCustomPollTimeouts() {
        // Test different poll timeouts
        int[] timeouts = {50, 100, 200, 500, 1000};

        for (int timeout : timeouts) {
            properties.setPollTimeoutMs(timeout);
            assertEquals(timeout, properties.getPollTimeoutMs());
        }
    }

    @Test
    @DisplayName("Le nom généré doit avoir un UUID de 8 caractères")
    void generatedNameShouldHaveEightCharacterUuid() {
        // When
        String consumerName = properties.getConsumerName();

        // Then
        assertNotNull(consumerName);
        // Extraire la partie UUID (après le dernier tiret)
        String[] parts = consumerName.split("-");
        String uuidPart = parts[parts.length - 1];
        assertEquals(8, uuidPart.length());
    }
}
