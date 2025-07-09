package be.ahm282.Athar.repository;

import be.ahm282.Athar.domain.TrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TrackingEventRepository extends JpaRepository<TrackingEvent, Long> {

    List<TrackingEvent> findByTrackingInfoId(UUID trackingInfoId);

}