package com.riskplatform.portfolio;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "position")
@Getter
@Setter
@NoArgsConstructor
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(name = "instrument_id", nullable = false, length = 50)
    private String instrumentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type", nullable = false, length = 20)
    private InstrumentType instrumentType;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "valuation_date", nullable = false)
    private LocalDate valuationDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Value in the currency the price is quoted in (see priceCurrency()). */
    public BigDecimal marketValue() {
        return quantity.multiply(price);
    }

    /**
     * Currency the price is quoted in. An FX pair "XXXYYY" is quoted as
     * YYY per 1 XXX, so USDINR at 73.38 is priced in INR whatever the
     * stored currency column says.
     */
    public String priceCurrency() {
        if (instrumentType == InstrumentType.FX && instrumentId != null
                && instrumentId.matches("[A-Z]{6}")) {
            return instrumentId.substring(3);
        }
        return currency;
    }

    public enum InstrumentType {
        EQUITY, ETF, BOND, FX
    }
}