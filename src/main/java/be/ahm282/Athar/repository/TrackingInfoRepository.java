package be.ahm282.Athar.repository;

import be.ahm282.Athar.domain.TrackingInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrackingInfoRepository extends JpaRepository<TrackingInfo, UUID> {

    Optional<TrackingInfo> findByTrackingNumber(String trackingNumber);
    List<TrackingInfo> findByStatus(String status);
    List<TrackingInfo> findByCarrier(String carrier);

    List<TrackingInfo> findBySenderName(String sender);
    List<TrackingInfo> findByReceiverName(String receiver);
    List<TrackingInfo> findByDestinationPostcode(String destinationPostcode);
    List<TrackingInfo> findByDestinationMunicipality(String destinationCity);

}
