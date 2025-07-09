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

    public BpostTrackingStrategy(BpostClient bpostClient, TrackingInfoRepository trackingInfoRepository) {
        this.bpostClient = bpostClient;
        this.trackingInfoRepository = trackingInfoRepository;
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

        for (TrackingInfo parsed : parsedInfos) {
            Optional<TrackingInfo> existingOptional = trackingInfoRepository.findByTrackingNumber(parsed.getTrackingNumber());

            if (existingOptional.isEmpty()) {
                // New shipment - Save everything from response
                trackingInfoRepository.save(parsed);
            } else {
                TrackingInfo existingShipment = existingOptional.get();

                // Find new events only to write to DB
                List<TrackingEvent> existingEvents = existingShipment.getEvents();
                List<TrackingEvent> newEvents = getNewEvents(parsed.getEvents(), existingEvents);

                if (!newEvents.isEmpty()) {
                    newEvents.forEach(event -> {
                        event.setTrackingInfo(existingShipment);
                        existingShipment.getEvents().add(event);
                    });

                    trackingInfoRepository.save(existingShipment);
                }

                existingShipment.setStatus(parsed.getStatus());
                trackingInfoRepository.save(existingShipment);
            }
        }

        return parsedInfos;
    }

    private List<TrackingInfo> parseTrackingResponse(String json) {
        ObjectMapper mapper = new ObjectMapper();
        List<TrackingInfo> results = new ArrayList<>();

        try {
            JsonNode root = mapper.readTree(json);
            JsonNode items = root.get("items");

            if (items != null && items.isArray()) {
                for (JsonNode item : items) {
                    TrackingInfo trackingInfo = new TrackingInfo();

                    trackingInfo.setTrackingNumber(item.path("itemCode").asText());
                    trackingInfo.setCarrier("Bpost");

                    // Receiver / Destination
                    JsonNode receiver = item.path("receiver");
                    trackingInfo.setReceiverName(receiver.path("name").asText());
                    trackingInfo.setDestinationStreet(receiver.path("street").asText());
                    trackingInfo.setDestinationMunicipality(receiver.path("municipality").asText());
                    trackingInfo.setDestinationPostcode(receiver.path("postcode").asText());
                    trackingInfo.setDestinationCountry(receiver.path("countryCode").asText());


                    // Sender / Origin
                    JsonNode sender = item.path("sender");
                    trackingInfo.setSenderName(sender.path("name").asText());
                    trackingInfo.setSenderStreet(sender.path("street").asText());
                    trackingInfo.setSenderMunicipality(sender.path("municipality").asText());
                    trackingInfo.setSenderPostcode(sender.path("postcode").asText());
                    trackingInfo.setSenderCountry(sender.path("countryCode").asText());

                    // Latest Event
                    JsonNode events = item.path("events");
                    if (events.isArray() && !events.isEmpty()) {
                        for (JsonNode eventNode : events) {
                            TrackingEvent event = new TrackingEvent();

                            event.setDate(eventNode.path("date").asText());
                            event.setTime(eventNode.path("time").asText());
                            event.setLocation(eventNode.path("location").path("locationName").asText());
                            event.setDescription(eventNode.path("key").path("EN").path("description").asText());
                            event.setIrregularity(eventNode.path("irregularity").asBoolean());

                            event.setTrackingInfo(trackingInfo);

                            trackingInfo.getEvents().add(event);
                        }

                        trackingInfo.setStatus(events.path(0).path("key").path("EN").path("description").asText());
                    }

                    results.add(trackingInfo);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse tracking JSON", e);
        }

        return results;
    }

    private List<TrackingEvent> getNewEvents(List<TrackingEvent> incoming, List<TrackingEvent> existing) {
        Set<String> existingKeys = existing.stream()
                .map(e -> e.getDate() + "|" + e.getTime() + "|" + e.getDescription())
                .collect(Collectors.toSet());

        return incoming.stream()
                .filter(e -> !existingKeys.contains(e.getDate() + "|" + e.getTime() + "|" + e.getDescription()))
                .toList();
    }

}
