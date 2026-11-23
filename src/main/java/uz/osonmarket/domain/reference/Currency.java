package uz.osonmarket.domain.reference;

import jakarta.persistence.*;

@Entity
@Table(name = "currencies")
public class Currency {
    @Id
    @Column(length = 8)
    private String code;
    @Column(length = 16)
    private String symbol;
    private Short decimalPlaces;
    private Boolean enabled;

    protected Currency() {}
}
