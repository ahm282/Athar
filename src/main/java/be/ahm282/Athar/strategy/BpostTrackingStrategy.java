package be.ahm282.Athar.strategy;

import be.ahm282.Athar.client.BpostClient;
import be.ahm282.Athar.domain.TrackingEvent;
import be.ahm282.Athar.domain.TrackingInfo;
import be.ahm282.Athar.dto.TrackingRequest;
import be.ahm282.Athar.repository.TrackingInfoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BpostTrackingStrategy implements TrackingStrategy {

    private final BpostClient bpostClient;
    private final TrackingInfoRepository trackingInfoRepository;
    private final ObjectMapper objectMapper;

    public BpostTrackingStrategy(BpostClient bpostClient, TrackingInfoRepository trackingInfoRepository) {
        this.bpostClient = bpostClient;
        this.trackingInfoRepository = trackingInfoRepository;
        this.objectMapper = new ObjectMapper();
    }

    public final boolean supports(String carrier) {
        return "bpost".equalsIgnoreCase(carrier.trim());
    }

    @Override
    public final boolean requiresPostcode() {
        return true;
    }

    @Override
    public final List<TrackingInfo> track(TrackingRequest request) {
        request.validate(this.requiresPostcode());

        String json = this.bpostClient.fetchTrackingData(request.getTrackingNumber(), request.getPostcode());
        List<TrackingInfo> parsedInfos = parseTrackingResponse(json);

        return parsedInfos.stream()
                .map(this::saveOrUpdateTrackingInfo)
                .collect(Collectors.toList());
    }

    private TrackingInfo saveOrUpdateTrackingInfo(TrackingInfo parsedInfo) {
        Optional<TrackingInfo> existingOptional = trackingInfoRepository.findByTrackingNumber(parsedInfo.getTrackingNumber());

        if (existingOptional.isEmpty()) {
            return trackingInfoRepository.save(parsedInfo); // Save new shipment
        }

        TrackingInfo existingInfo = existingOptional.get(); // Shipment exists. retrieve it

        boolean changed = updateExistingTrackingInfo(existingInfo, parsedInfo); // Check for changes

        // Only save if changes occurred; otherwise, return as-is
        return changed ? trackingInfoRepository.save(existingInfo) : existingInfo;
    }

    private boolean updateExistingTrackingInfo(TrackingInfo existingShipment, TrackingInfo parsed) {
        boolean changed = false;

        List<TrackingEvent> newEvents = getNewEvents(parsed.getEvents(), existingShipment.getEvents());

        if (!newEvents.isEmpty()) {
            addNewEventsToExisting(existingShipment, newEvents);
            changed = true;
        }

        if (!parsed.getStatus().equals(existingShipment.getStatus())) {
            existingShipment.setStatus(parsed.getStatus());
            changed = true;
        }

        return changed;
    }

    private void addNewEventsToExisting(TrackingInfo existingShipment, List<TrackingEvent> newEvents) {
        newEvents.forEach(event -> {
            event.setTrackingInfo(existingShipment);
            existingShipment.getEvents().add(event);
        });
    }

    private List<TrackingInfo> parseTrackingResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.get("items");

            if (items == null || !items.isArray()) {
                return new ArrayList<>();
            }

            List<TrackingInfo> results = new ArrayList<>();

            for (JsonNode item : items) {
                TrackingInfo trackingInfo = parseTrackingItem(item);
                results.add(trackingInfo);
            }

            return results;
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse tracking JSON", e);
        }
    }

    private TrackingInfo parseTrackingItem(JsonNode item) {
        TrackingInfo trackingInfo = new TrackingInfo();

        // Basic info
        trackingInfo.setTrackingNumber(item.path("itemCode").asText());
        trackingInfo.setCarrier("Bpost");

        // Parse addresses
        parseReceiverInfo(item.path("receiver"), trackingInfo);
        parseSenderInfo(item.path("sender"), trackingInfo);

        // Parse events and set status
        parseEventsAndStatus(item.path("events"), trackingInfo);

        return trackingInfo;
    }

    private void parseReceiverInfo(JsonNode receiver, TrackingInfo trackingInfo) {
        trackingInfo.setReceiverName(receiver.path("name").asText());
        trackingInfo.setDestinationStreet(receiver.path("street").asText());
        trackingInfo.setDestinationMunicipality(receiver.path("municipality").asText());
        trackingInfo.setDestinationPostcode(receiver.path("postcode").asText());
        trackingInfo.setDestinationCountry(receiver.path("countryCode").asText());
    }

    private void parseSenderInfo(JsonNode sender, TrackingInfo trackingInfo) {
        trackingInfo.setSenderName(sender.path("name").asText());
        trackingInfo.setSenderStreet(sender.path("street").asText());
        trackingInfo.setSenderMunicipality(sender.path("municipality").asText());
        trackingInfo.setSenderPostcode(sender.path("postcode").asText());
        trackingInfo.setSenderCountry(sender.path("countryCode").asText());
    }

    private void parseEventsAndStatus(JsonNode events, TrackingInfo trackingInfo) {
        if (!events.isArray() || events.isEmpty()) {
            return;
        }

        for (JsonNode eventNode : events) {
            TrackingEvent event = parseTrackingEvent(eventNode, trackingInfo);
            trackingInfo.getEvents().add(event);
        }

        // Set status from the first (most recent) event
        String latestStatus = events.path(0)
                .path("key")
                .path("EN")
                .path("description")
                .asText();
        trackingInfo.setStatus(latestStatus);
    }

    private TrackingEvent parseTrackingEvent(JsonNode eventNode, TrackingInfo trackingInfo) {
        TrackingEvent event = new TrackingEvent();

        event.setDate(eventNode.path("date").asText());
        event.setTime(eventNode.path("time").asText());
        event.setLocation(eventNode.path("location").path("locationName").asText());
        event.setDescription(eventNode.path("key").path("EN").path("description").asText());
        event.setIrregularity(eventNode.path("irregularity").asBoolean());
        event.setTrackingInfo(trackingInfo);

        return event;
    }

    private List<TrackingEvent> getNewEvents(List<TrackingEvent> incoming, List<TrackingEvent> existing) {
        Set<String> existingKeys = existing.stream()
                .map(this::createEventKey)
                .collect(Collectors.toSet());

        return incoming.stream()
                .filter(e -> !existingKeys.contains(createEventKey(e)))
                .toList();
    }

    private String createEventKey(TrackingEvent event) {
        return event.getDate() + "|" + event.getTime() + "|" + event.getDescription();
    }

}
