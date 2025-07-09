package be.ahm282.Athar.controller;

import be.ahm282.Athar.domain.TrackingInfo;
import be.ahm282.Athar.dto.TrackingRequest;
import be.ahm282.Athar.service.TrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/track")
public class TrackingController {

    private final TrackingService trackingService;

    @Autowired
    public TrackingController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @GetMapping
    public ResponseEntity<List<TrackingInfo>> getTrackingInfo(@RequestParam String carrier, @RequestParam String trackingNumber, @RequestParam String postcode) {
        TrackingRequest trackingRequest = new TrackingRequest(trackingNumber,  postcode);
        List<TrackingInfo> trackingInfoList = trackingService.track(carrier, trackingRequest);

        return new ResponseEntity<>(trackingInfoList, HttpStatus.OK);
    }

}
