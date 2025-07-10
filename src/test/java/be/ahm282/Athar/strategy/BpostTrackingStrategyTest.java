/**
 * Unit tests for {@link BpostTrackingStrategy}, responsible for tracking parcel shipments via Bpost.
 *----------------------
 * The strategy:
 *  - Validates inputs (tracking number, postcode)
 *  - Parses JSON responses from Bpost's API
 *  - Updates or inserts TrackingInfo entities
 *  - Deduplicates events and updates shipment status accordingly
 *----------------------
 * Covered Cases:
 * ----------------------
 * Supports carrier name normalization (case-insensitive, trimmed)
 * Requires postcode and tracking number (throws on missing values)
 * Saves new TrackingInfo for first-time tracking
 * Updates existing TrackingInfo with:
 *    - New events only (avoids duplicates)
 *    - Updated status
 * Handles edge JSON responses:
 *    - Empty item array → returns empty list
 *    - Missing items property → returns empty list
 *    - Malformed JSON → throws RuntimeException with clear message
 * Correctly sets:
 *    - Carrier field to "Bpost"
 *    - Sender and receiver address fields
 *    - TrackingEvent fields including irregularities
 * Processes multi-item JSON responses (returns multiple entities)
 * Ensures event–parent linkage (TrackingEvent → TrackingInfo)
 * Returns saved entity containing DB-generated UUID
 *----------------------
 * Suggested Additions in the future:
 * ---------------------
 * Test resilience when bpostClient returns null or empty string
 * Test DB failure on save (simulate repository exception)
 *----------------------
 * Notes:
 * ----------------------
 * - JSON is stubbed via helper methods (string-based mocks)
 * - Emphasis on business rule correctness over implementation detail
 */

package be.ahm282.Athar.strategy;

import be.ahm282.Athar.client.BpostClient;
import be.ahm282.Athar.domain.TrackingEvent;
import be.ahm282.Athar.domain.TrackingInfo;
import be.ahm282.Athar.dto.TrackingRequest;
import be.ahm282.Athar.repository.TrackingInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
public class BpostTrackingStrategyTest {

    @Mock
    private BpostClient bpostClient;

    @Mock
    private TrackingInfoRepository trackingInfoRepository;

    private BpostTrackingStrategy strategy;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        strategy = new BpostTrackingStrategy(bpostClient, trackingInfoRepository);
    }

    @Test
    void shouldSupportBpostCarrier() {
        assertTrue(strategy.supports("bpost"));
        assertTrue(strategy.supports("Bpost"));
        assertTrue(strategy.supports("BPOST"));
        assertTrue(strategy.supports("  bpost  "));
        assertFalse(strategy.supports("DHL"));
        assertFalse(strategy.supports("PostNL"));
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

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> strategy.track(request));

        assertThrows(IllegalArgumentException.class, () -> strategy.track(request));
        assertEquals("Postcode is required!", exception.getMessage());
    }

    @Test
    void shouldThrowIfTrackingNumberMissing() {
        TrackingRequest request = new TrackingRequest();
        request.setTrackingNumber(null);
        request.setPostcode("2300");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> strategy.track(request));

        assertThrows(IllegalArgumentException.class, () -> strategy.track(request));
        assertEquals("Tracking number is required!", exception.getMessage());
    }

    @Test
    void track_newShipment_shouldSaveDirectly() {
        // Arrange
        String mockJson = createMockJsonWithSingleItem();
        TrackingRequest request = new TrackingRequest("00164300796602406833", "2340");

        when(bpostClient.fetchTrackingData(anyString(), anyString())).thenReturn(mockJson);
        when(trackingInfoRepository.findByTrackingNumber("00164300796602406833")).thenReturn(Optional.empty());
        when(trackingInfoRepository.save(any(TrackingInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<TrackingInfo> result = strategy.track(request);

        // Assert
        assertEquals(1, result.size());
        verify(trackingInfoRepository).save(any(TrackingInfo.class));
        verify(trackingInfoRepository).findByTrackingNumber("00164300796602406833");
    }

    @Test
    void track_existingShipment_shouldUpdateWithNewEvents() {
        // Arrange
        String mockJson = createMockJsonWithMultipleEvents();
        TrackingRequest request = new TrackingRequest("00164300796602406833", "2340");

        TrackingInfo existingInfo = createExistingTrackingInfo();
        when(bpostClient.fetchTrackingData(anyString(), anyString())).thenReturn(mockJson);
        when(trackingInfoRepository.findByTrackingNumber("00164300796602406833")).thenReturn(Optional.of(existingInfo));
        when(trackingInfoRepository.save(any(TrackingInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<TrackingInfo> result = strategy.track(request);

        // Assert
        assertEquals(1, result.size());
        verify(trackingInfoRepository).save(existingInfo);
        assertTrue(existingInfo.getEvents().size() > 1); // Should have added new events
    }

    @Test
    void track_existingShipment_shouldUpdateStatus() {
        // Arrange
        String mockJson = createMockJsonWithUpdatedStatus();
        TrackingRequest request = new TrackingRequest("00164300796602406833", "2340");

        TrackingInfo existingInfo = createExistingTrackingInfo();
        existingInfo.setStatus("Old Status");

        when(bpostClient.fetchTrackingData(anyString(), anyString())).thenReturn(mockJson);
        when(trackingInfoRepository.findByTrackingNumber("00164300796602406833")).thenReturn(Optional.of(existingInfo));
        when(trackingInfoRepository.save(any(TrackingInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<TrackingInfo> result = strategy.track(request);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Delivered", existingInfo.getStatus());
        verify(trackingInfoRepository).save(existingInfo);
    }

    @Test
    void track_existingShipment_shouldNotDuplicateEvents() {
        // Arrange
        String mockJson = createMockJsonWithSingleItem();
        TrackingRequest request = new TrackingRequest("00164300796602406833", "2340");

        TrackingInfo existingInfo = createExistingTrackingInfoWithSameEvent();

        when(bpostClient.fetchTrackingData(anyString(), anyString())).thenReturn(mockJson);
        when(trackingInfoRepository.findByTrackingNumber("00164300796602406833")).thenReturn(Optional.of(existingInfo));
        when(trackingInfoRepository.save(any(TrackingInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<TrackingInfo> result = strategy.track(request);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1, existingInfo.getEvents().size()); // Should not duplicate
        verify(trackingInfoRepository).save(existingInfo);
    }

    @Test
    void track_shouldReturnSavedEntitiesFromDatabase() {
        // Arrange
        String mockJson = createMockJsonWithSingleItem();
        TrackingRequest request = new TrackingRequest("00164300796602406833", "2340");

        TrackingInfo savedInfo = new TrackingInfo();
        UUID savedId = UUID.randomUUID();
        savedInfo.setId(savedId); // Simulate database ID
        savedInfo.setTrackingNumber("00164300796602406833");

        when(bpostClient.fetchTrackingData(anyString(), anyString())).thenReturn(mockJson);
        when(trackingInfoRepository.findByTrackingNumber("00164300796602406833")).thenReturn(Optional.empty());
        when(trackingInfoRepository.save(any(TrackingInfo.class))).thenReturn(savedInfo);

        // Act
        List<TrackingInfo> result = strategy.track(request);

        // Assert
        assertEquals(1, result.size());
        assertEquals(savedId, result.get(0).getId()); // Should return entity with DB ID
    }

    @Test
    void parseTrackingResponse_emptyItemsArray_shouldReturnEmptyList() {
        // Arrange
        String emptyJson = """
            {
                "items": []
            }
            """;

        when(bpostClient.fetchTrackingData(anyString(), anyString())).thenReturn(emptyJson);
        when(trackingInfoRepository.findByTrackingNumber(anyString())).thenReturn(Optional.empty());

        TrackingRequest request = new TrackingRequest("12345", "1000");

        // Act
        List<TrackingInfo> result = strategy.track(request);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void parseTrackingResponse_missingItemsProperty_shouldReturnEmptyList() {
        // Arrange
        String malformedJson = """
            {
                "data": "no items property"
            }
            """;

        when(bpostClient.fetchTrackingData(anyString(), anyString())).thenReturn(malformedJson);
        when(trackingInfoRepository.findByTrackingNumber(anyString())).thenReturn(Optional.empty());

        TrackingRequest request = new TrackingRequest("12345", "1000");

        // Act
        List<TrackingInfo> result = strategy.track(request);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void parseTrackingResponse_invalidJson_shouldThrowRuntimeException() {
        // Arrange
        String invalidJson = "{ invalid json }";

        when(bpostClient.fetchTrackingData(anyString(), anyString())).thenReturn(invalidJson);

        TrackingRequest request = new TrackingRequest("12345", "1000");

        // Act
        RuntimeException exception = assertThrows(RuntimeException.class, () -> strategy.track(request));

        // Assert
        assertEquals("Failed to parse tracking JSON", exception.getMessage());
    }

    @Test
    void parseTrackingItem_shouldSetCarrierToBpost() {
        // Arrange
        String mockJson = createMockJsonWithSingleItem();
        TrackingRequest request = new TrackingRequest("00164300796602406833", "2340");

        when(bpostClient.fetchTrackingData(anyString(), anyString())).thenReturn(mockJson);
        when(trackingInfoRepository.findByTrackingNumber(anyString())).thenReturn(Optional.empty());
        when(trackingInfoRepository.save(any(TrackingInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<TrackingInfo> result = strategy.track(request);

        // Assert
        assertEquals("Bpost", result.get(0).getCarrier());
    }

    @Test
    void parseTrackingItem_shouldParseAllReceiverInfo() {
        // Arrange
        String mockJson = createMockJsonWithSingleItem();
        TrackingRequest request = new TrackingRequest("00164300796602406833", "2340");

        when(bpostClient.fetchTrackingData(anyString(), anyString())).thenReturn(mockJson);
        when(trackingInfoRepository.findByTrackingNumber(anyString())).thenReturn(Optional.empty());
        when(trackingInfoRepository.save(any(TrackingInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<TrackingInfo> result = strategy.track(request);

        // Assert
        TrackingInfo info = result.get(0);
        assertEquals("AHMED MAHGOUB", info.getReceiverName());
        assertEquals("LINDENLAAN 15", info.getDestinationStreet());
        assertEquals("BEERSE", info.getDestinationMunicipality());
        assertEquals("2340", info.getDestinationPostcode());
        assertEquals("BE", info.getDestinationCountry());
    }

    @Test
    void parseTrackingItem_shouldParseAllSenderInfo() {
        // Arrange
        String mockJson = createMockJsonWithSingleItem();
        TrackingRequest request = new TrackingRequest("00164300796602406833", "2340");

        when(bpostClient.fetchTrackingData(anyString(), anyString())).thenReturn(mockJson);
        when(trackingInfoRepository.findByTrackingNumber(anyString())).thenReturn(Optional.empty());
        when(trackingInfoRepository.save(any(TrackingInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<TrackingInfo> result = strategy.track(request);

        // Assert
        TrackingInfo info = result.get(0);
        assertEquals("DHL CONNECT", info.getSenderName());
        assertEquals("BEDRIJVENZONE MACHELEN CARGO 829C", info.getSenderStreet());
        assertEquals("Office Exchange Brussels Airport Remailing", info.getSenderMunicipality());
        assertEquals("1934", info.getSenderPostcode());
        assertEquals("BE", info.getSenderCountry());
    }

    @Test
    void parseEventsAndStatus_shouldParseAllEventFields() {
        // Arrange
        String mockJson = createMockJsonWithDetailedEvent();
        TrackingRequest request = new TrackingRequest("00164300796602406833", "2340");

        when(bpostClient.fetchTrackingData(anyString(), anyString())).thenReturn(mockJson);
        when(trackingInfoRepository.findByTrackingNumber(anyString())).thenReturn(Optional.empty());
        when(trackingInfoRepository.save(any(TrackingInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<TrackingInfo> result = strategy.track(request);

        // Assert
        TrackingInfo info = result.get(0);
        assertEquals(1, info.getEvents().size());

        TrackingEvent event = info.getEvents().get(0);
        assertEquals("2023-12-01", event.getDate());
        assertEquals("14:30:00", event.getTime());
        assertEquals("Brussels", event.getLocation());
        assertEquals("Package delivered", event.getDescription());
        assertTrue(event.isIrregularity());
    }

    @Test
    void parseEventsAndStatus_noEvents_shouldNotSetStatus() {
        // Arrange
        String mockJson = createMockJsonWithNoEvents();
        TrackingRequest request = new TrackingRequest("00164300796602406833", "2340");

        when(bpostClient.fetchTrackingData(anyString(), anyString())).thenReturn(mockJson);
        when(trackingInfoRepository.findByTrackingNumber(anyString())).thenReturn(Optional.empty());
        when(trackingInfoRepository.save(any(TrackingInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<TrackingInfo> result = strategy.track(request);

        // Assert
        TrackingInfo info = result.get(0);
        assertTrue(info.getEvents().isEmpty());
        assertNull(info.getStatus());
    }

    @Test
    void updateExistingTrackingInfo_shouldSetTrackingInfoOnNewEvents() {
        // Arrange
        String mockJson = createMockJsonWithMultipleEvents();
        TrackingRequest request = new TrackingRequest("00164300796602406833", "2340");

        TrackingInfo existingInfo = createExistingTrackingInfo();

        when(bpostClient.fetchTrackingData(anyString(), anyString())).thenReturn(mockJson);
        when(trackingInfoRepository.findByTrackingNumber("00164300796602406833")).thenReturn(Optional.of(existingInfo));
        when(trackingInfoRepository.save(any(TrackingInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        strategy.track(request);

        // Assert
        existingInfo.getEvents().forEach(event -> assertEquals(existingInfo, event.getTrackingInfo()));
    }

    @Test
    void track_multipleItems_shouldProcessAll() {
        // Arrange
        String mockJson = createMockJsonWithMultipleItems();
        TrackingRequest request = new TrackingRequest("00164300796602406833", "2340");

        when(bpostClient.fetchTrackingData(anyString(), anyString())).thenReturn(mockJson);
        when(trackingInfoRepository.findByTrackingNumber(anyString())).thenReturn(Optional.empty());
        when(trackingInfoRepository.save(any(TrackingInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<TrackingInfo> result = strategy.track(request);

        // Assert
        assertEquals(2, result.size());
        verify(trackingInfoRepository, times(2)).save(any(TrackingInfo.class));
    }

    //=================================
    // HELPER METHODS
    //=================================
    private TrackingInfo createExistingTrackingInfo() {
        TrackingInfo info = new TrackingInfo();
        info.setTrackingNumber("00164300796602406833");
        info.setCarrier("Bpost");
        info.setStatus("In transit");
        info.setEvents(new ArrayList<>());

        TrackingEvent existingEvent = new TrackingEvent();
        existingEvent.setDate("2023-11-30");
        existingEvent.setTime("09:00:00");
        existingEvent.setDescription("Package received");
        existingEvent.setTrackingInfo(info);

        info.getEvents().add(existingEvent);
        return info;
    }

    private TrackingInfo createExistingTrackingInfoWithSameEvent() {
        TrackingInfo info = new TrackingInfo();
        info.setTrackingNumber("00164300796602406833");
        info.setCarrier("Bpost");
        info.setStatus("In transit");
        info.setEvents(new ArrayList<>());

        TrackingEvent existingEvent = new TrackingEvent();
        existingEvent.setDate("2023-12-01");
        existingEvent.setTime("10:00:00");
        existingEvent.setDescription("Confirmation of preparation of the shipment received");
        existingEvent.setTrackingInfo(info);

        info.getEvents().add(existingEvent);
        return info;
    }

    //=================================
    // Mock JSON Responses
    //=================================
    private String createMockJsonWithSingleItem() {
        return """
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
                                "date": "2023-12-01",
                                "time": "10:00:00",
                                "location": {
                                    "locationName": "Brussels"
                                },
                                "key": {
                                    "EN": {
                                        "description": "Confirmation of preparation of the shipment received"
                                    }
                                },
                                "irregularity": false
                            }
                        ]
                    }
                ]
            }
            """;
    }

    private String createMockJsonWithMultipleEvents() {
        return """
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
                                "date": "2023-12-02",
                                "time": "15:00:00",
                                "location": {
                                    "locationName": "Brussels"
                                },
                                "key": {
                                    "EN": {
                                        "description": "Package delivered"
                                    }
                                },
                                "irregularity": false
                            },
                            {
                                "date": "2023-12-01",
                                "time": "10:00:00",
                                "location": {
                                    "locationName": "Brussels"
                                },
                                "key": {
                                    "EN": {
                                        "description": "Confirmation of preparation of the shipment received"
                                    }
                                },
                                "irregularity": false
                            }
                        ]
                    }
                ]
            }
            """;
    }

    private String createMockJsonWithUpdatedStatus() {
        return """
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
                                "date": "2023-12-01",
                                "time": "10:00:00",
                                "location": {
                                    "locationName": "Brussels"
                                },
                                "key": {
                                    "EN": {
                                        "description": "Delivered"
                                    }
                                },
                                "irregularity": false
                            }
                        ]
                    }
                ]
            }
            """;
    }

    private String createMockJsonWithDetailedEvent() {
        return """
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
                                "date": "2023-12-01",
                                "time": "14:30:00",
                                "location": {
                                    "locationName": "Brussels"
                                },
                                "key": {
                                    "EN": {
                                        "description": "Package delivered"
                                    }
                                },
                                "irregularity": true
                            }
                        ]
                    }
                ]
            }
            """;
    }

    private String createMockJsonWithNoEvents() {
        return """
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
                        "events": []
                    }
                ]
            }
            """;
    }

    private String createMockJsonWithMultipleItems() {
        return """
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
                                "date": "2023-12-01",
                                "time": "10:00:00",
                                "location": {
                                    "locationName": "Brussels"
                                },
                                "key": {
                                    "EN": {
                                        "description": "Shipped"
                                    }
                                },
                                "irregularity": false
                            }
                        ]
                    },
                    {
                        "itemCode": "00164300796602406834",
                        "receiver": {
                            "name": "JOHN DOE",
                            "street": "MAIN STREET 1",
                            "municipality": "ANTWERP",
                            "postcode": "2000",
                            "countryCode": "BE"
                        },
                        "sender": {
                            "name": "SENDER B",
                            "street": "SENDER STREET 1",
                            "municipality": "BRUSSELS",
                            "postcode": "1000",
                            "countryCode": "BE"
                        },
                        "events": [
                            {
                                "date": "2023-12-01",
                                "time": "11:00:00",
                                "location": {
                                    "locationName": "Antwerp"
                                },
                                "key": {
                                    "EN": {
                                        "description": "In transit"
                                    }
                                },
                                "irregularity": false
                            }
                        ]
                    }
                ]
            }
            """;
    }

}
