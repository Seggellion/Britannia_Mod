package com.seggellion.britannia_mod.service.spawn.wirecontract;

import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnOperationRequest;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingRecord;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnPendingRequestAdapter;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnRequestSerializer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Milestone 5 wire-contract closeout, Part A: proves that the checked-in request fixtures
 * under {@code src/test/resources/wire_contract/requests/} are byte-for-byte what the real
 * {@link ServiceNpcSpawnRequestSerializer} produces for each scenario in
 * {@link ServiceNpcSpawnWireContractScenarios}, via the same
 * {@link ServiceNpcSpawnPendingRequestAdapter} conversion the delivery processor uses in
 * production. This is not a hand-written fixture standing in for the serializer: every run
 * re-derives the bytes from the pending record and diffs them against the committed file.
 */
class ServiceNpcSpawnWireContractRequestFixtureTest {
    private static final String RESOURCE_ROOT = "wire_contract/requests/";

    @TestFactory
    Stream<DynamicTest> requestFixturesMatchTheRealSerializer() {
        Map<String, ServiceNpcSpawnPendingRecord> scenarios = ServiceNpcSpawnWireContractScenarios.all();
        return scenarios.entrySet().stream().map(entry -> DynamicTest.dynamicTest(entry.getKey(), () -> {
            ServiceNpcSpawnOperationRequest request = ServiceNpcSpawnPendingRequestAdapter.adapt(entry.getValue());
            byte[] actual = ServiceNpcSpawnRequestSerializer.serialize(request);
            byte[] expected = readFixture(entry.getKey());
            assertArrayEquals(expected, actual, () -> "request fixture mismatch for scenario '" + entry.getKey()
                + "'\nexpected: " + new String(expected, StandardCharsets.UTF_8)
                + "\nactual:   " + new String(actual, StandardCharsets.UTF_8));
        }));
    }

    private static byte[] readFixture(String scenarioName) {
        String path = RESOURCE_ROOT + scenarioName + ".json";
        try (InputStream in = ServiceNpcSpawnWireContractRequestFixtureTest.class
                .getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                fail("missing checked-in request fixture: " + path);
            }
            return in.readAllBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
