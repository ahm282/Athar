package be.ahm282.Athar.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrackingRequest {

    private String trackingNumber;
    private String postcode;

    public void validate(boolean requiresPostcode) throws IllegalArgumentException {
        validateNotEmpty(trackingNumber, "Tracking number");

        if (requiresPostcode) {
            validateNotEmpty(postcode, "Postcode");
        }
    }

    private void validateNotEmpty(String value, String fieldName) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required!");
        }
    }

}
