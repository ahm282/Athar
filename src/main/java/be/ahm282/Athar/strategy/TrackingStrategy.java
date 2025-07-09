package be.ahm282.Athar.strategy;

import be.ahm282.Athar.domain.TrackingInfo;
import be.ahm282.Athar.dto.TrackingRequest;

import java.util.List;

public interface TrackingStrategy {

    boolean supports(String carrier);

    default boolean requiresPostcode() {
        return false;
    }

    List<TrackingInfo> track(TrackingRequest request);

}
