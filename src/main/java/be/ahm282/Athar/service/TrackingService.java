package be.ahm282.Athar.service;

import be.ahm282.Athar.domain.TrackingInfo;
import be.ahm282.Athar.dto.TrackingRequest;
import be.ahm282.Athar.strategy.TrackingStrategy;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TrackingService {

    private final List<TrackingStrategy> strategies;

    public TrackingService(List<TrackingStrategy> strategies) {
        this.strategies = strategies;
        System.out.println(strategies.toString());
    }

    public List<TrackingInfo> track(String carrier, TrackingRequest request) {
        return strategies.stream()
                .filter(s -> s.supports(carrier))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported carrier: " + carrier))
                .track(request);
    }

}
