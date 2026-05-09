package org.ulpgc.dacd.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class FlightOffer {
    private String matchExternalId;
    private String originAirport;
    private String destinationAirport;
    private LocalDate departureDate;
    private LocalDate returnDate;
    private String airline;
    private BigDecimal priceOriginal;
    private String currency;
    private boolean residentDiscountApplicable;
    private BigDecimal estimatedResidentPrice;
    private String source;
    private LocalDateTime capturedAt;

    public FlightOffer() {
    }

    public FlightOffer(String matchExternalId, String originAirport, String destinationAirport,
                       LocalDate departureDate, LocalDate returnDate, String airline,
                       BigDecimal priceOriginal, String currency, boolean residentDiscountApplicable,
                       BigDecimal estimatedResidentPrice, String source, LocalDateTime capturedAt) {
        this.matchExternalId = matchExternalId;
        this.originAirport = originAirport;
        this.destinationAirport = destinationAirport;
        this.departureDate = departureDate;
        this.returnDate = returnDate;
        this.airline = airline;
        this.priceOriginal = priceOriginal;
        this.currency = currency;
        this.residentDiscountApplicable = residentDiscountApplicable;
        this.estimatedResidentPrice = estimatedResidentPrice;
        this.source = source;
        this.capturedAt = capturedAt;
    }

    public String getMatchExternalId() {
        return matchExternalId;
    }

    public String getOriginAirport() {
        return originAirport;
    }

    public String getDestinationAirport() {
        return destinationAirport;
    }

    public LocalDate getDepartureDate() {
        return departureDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public String getAirline() {
        return airline;
    }

    public BigDecimal getPriceOriginal() {
        return priceOriginal;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isResidentDiscountApplicable() {
        return residentDiscountApplicable;
    }

    public BigDecimal getEstimatedResidentPrice() {
        return estimatedResidentPrice;
    }

    public String getSource() {
        return source;
    }

    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }

    @Override
    public String toString() {
        return "FlightOffer{" +
                "matchExternalId='" + matchExternalId + '\'' +
                ", originAirport='" + originAirport + '\'' +
                ", destinationAirport='" + destinationAirport + '\'' +
                ", departureDate=" + departureDate +
                ", returnDate=" + returnDate +
                ", airline='" + airline + '\'' +
                ", priceOriginal=" + priceOriginal +
                ", currency='" + currency + '\'' +
                ", residentDiscountApplicable=" + residentDiscountApplicable +
                ", estimatedResidentPrice=" + estimatedResidentPrice +
                ", source='" + source + '\'' +
                ", capturedAt=" + capturedAt +
                '}';
    }
}
