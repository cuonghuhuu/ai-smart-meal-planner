package com.smartmealplanner;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class SchemaResourceTest {
    @ParameterizedTest
    @CsvSource({"schema,V001__initial_schema.sql", "seed,R001__reference_data.sql"})
    void packagesAuthoritativeSqlByteForByte(String directory, String file) throws Exception {
        try (var resource = getClass().getResourceAsStream("/db/migration/" + file)) {
            assertThat(resource).isNotNull();
            assertThat(resource.readAllBytes()).isEqualTo(
                    Files.readAllBytes(Path.of("..", "database", directory, file)));
        }
    }
}
