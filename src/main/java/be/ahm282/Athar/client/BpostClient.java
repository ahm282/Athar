package be.ahm282.Athar.client;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class BpostClient {

    private final WebClient webClient;

    public BpostClient(WebClient.Builder builder) {
        this.webClient = builder
                .baseUrl("https://track.bpost.cloud/track/items")
                .build();
    }

    public String fetchTrackingData(String trackingNumber, String postcode) {
        return webClient
                .get()
                .uri(uriBuilder ->  uriBuilder.queryParam("itemIdentifier", trackingNumber)
                                .queryParam("postalCode", postcode)
                                .build()

                )
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

}
