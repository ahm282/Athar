package be.ahm282.Athar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class TrackingInfo {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(updatable = false, nullable = false)
    private UUID id;
    private String trackingNumber;
    private String status;
    private String carrier;

    private String receiverName;
    private String destinationStreet;
    private String destinationMunicipality;
    private String destinationPostcode;
    private String destinationCountry;

    private String senderName;
    private String senderStreet;
    private String senderMunicipality;
    private String senderPostcode;
    private String senderCountry;

    private LocalDateTime createdDate;
    private LocalDateTime lastModifiedDate;

    @OneToMany(mappedBy = "trackingInfo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TrackingEvent> events = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
        lastModifiedDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastModifiedDate = LocalDateTime.now();
    }

}
