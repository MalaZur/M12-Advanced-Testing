
package lv.bootcamp.shelter.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestClientPartnerRegistryClientTest {

    private MockWebServer mockWebServer;
    private RestClientPartnerRegistryClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        client = new RestClientPartnerRegistryClient(
                RestClient.builder(),
                mockWebServer.url("/").toString()
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void lookup_shouldReturnResultWhenPartnerRespondsSuccessfully()
            throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"registered": true,
                            "partnerShelterName": "Happy Shelter"}
                        """));

        PartnerRegistryResult result = client.lookup("CHIP-001");

        assertThat(result.registered()).isTrue();
        assertThat(result.partnerShelterName())
                .isEqualTo("Happy Shelter");

        RecordedRequest request =
                mockWebServer.takeRequest(5, TimeUnit.SECONDS);

        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath())
                .isEqualTo("/registry/CHIP-001");
    }

    @Test
    void lookup_shouldThrowWhenPartnerServerFails()
            throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(503).setBody("Service Unavailable"));

        assertThatThrownBy(() -> client.lookup("CHIP-002"))
                .isInstanceOf(HttpServerErrorException.class)
                .hasMessageContaining("503");

        RecordedRequest request = mockWebServer.takeRequest(5, TimeUnit.SECONDS);

        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).isEqualTo("/registry/CHIP-002");
    }
}
