package be.ahm282.Athar.strategy;

import be.ahm282.Athar.client.BpostClient;
import be.ahm282.Athar.domain.TrackingInfo;
import be.ahm282.Athar.dto.TrackingRequest;
import be.ahm282.Athar.repository.TrackingInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BpostTrackingStrategyTest {

    private BpostClient bpostClient;
    private BpostTrackingStrategy strategy;

    @BeforeEach
    void setup() {
        bpostClient = mock(BpostClient.class);
        TrackingInfoRepository trackingInfoRepository = mock(TrackingInfoRepository.class);
        strategy = new BpostTrackingStrategy(bpostClient, trackingInfoRepository);
    }

    @Test
    void shouldSupportBpostCarrier() {
        assertTrue(strategy.supports("bpost"));
        assertTrue(strategy.supports("Bpost"));
        assertTrue(strategy.supports("BPOST"));
        assertFalse(strategy.supports("DHL"));
    }

    @Test
    void shouldRequirePostCode() {
        assertTrue(strategy.requiresPostcode());
    }

    @Test
    void shouldThrowIfPostcodeMissing() {
        TrackingRequest request = new TrackingRequest();
        request.setPostcode(null);
        request.setTrackingNumber("3305518165683602");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            strategy.track(request);
        });

        assertThrows(IllegalArgumentException.class, () -> strategy.track(request));
        assertEquals("Postcode is required!", exception.getMessage());
    }

    @Test
    void shouldThrowIfTrackingNumberMissing() {
        TrackingRequest request = new TrackingRequest();
        request.setTrackingNumber(null);
        request.setPostcode("2300");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            strategy.track(request);
        });

        assertThrows(IllegalArgumentException.class, () -> strategy.track(request));
        assertEquals("Tracking number is required!", exception.getMessage());
    }

    @Test
    void testTrack_parsesResponseCorrectly() {
        // Arrange
        String mockJson = """
        {
            "items": [
                {
                    "itemCode": "00164300796602406833",
                    "receiver": {
                        "name": "AHMED MAHGOUB",
                        "street": "LINDENLAAN 15",
                        "municipality": "BEERSE",
                        "postcode": "2340",
                        "countryCode": "BE"
                    },
                    "sender": {
                        "name": "DHL CONNECT",
                        "street": "BEDRIJVENZONE MACHELEN CARGO 829C",
                        "municipality": "Office Exchange Brussels Airport Remailing",
                        "postcode": "1934",
                        "countryCode": "BE"
                    },
                    "events": [
                        {
                            "key": {
                                "EN": {
                                    "description": "Confirmation of preparation of the shipment received"
                                }
                            }
                        }
                    ]
                }
            ]
        }
        """;

        TrackingRequest request = new TrackingRequest("00164300796602406833", "2340");

        when(bpostClient.fetchTrackingData(anyString(), anyString())).thenReturn(mockJson);

        // Act
        List<TrackingInfo> result = strategy.track(request);

        // Assert
        assertEquals(1, result.size());
        TrackingInfo info = result.get(0);

        assertEquals("00164300796602406833", info.getTrackingNumber());
        assertEquals("AHMED MAHGOUB", info.getReceiverName());
        assertEquals("LINDENLAAN 15", info.getDestinationStreet());
        assertEquals("BEERSE", info.getDestinationMunicipality());
        assertEquals("2340", info.getDestinationPostcode());
        assertEquals("BE", info.getDestinationCountry());

        assertEquals("DHL CONNECT", info.getSenderName());
        assertEquals("BEDRIJVENZONE MACHELEN CARGO 829C", info.getSenderStreet());
        assertEquals("Office Exchange Brussels Airport Remailing", info.getSenderMunicipality());
        assertEquals("1934", info.getSenderPostcode());
        assertEquals("BE", info.getSenderCountry());

        assertEquals("Confirmation of preparation of the shipment received", info.getStatus());

        verify(bpostClient).fetchTrackingData("00164300796602406833", "2340");
    }

    @Test
    void givenMissingPostcode_whenTrack_thenThrowsException() {
        TrackingRequest request = new TrackingRequest("00164300796602406833", null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            strategy.track(request);
        });

        assertEquals("Postcode is required!", ex.getMessage());
    }

}
