package uz.osonmarket.domain.reference;

import jakarta.persistence.*;

@Entity
@Table(name = "languages")
public class Language {
    @Id
    @Column(name = "code", length = 8)
    private String code;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "is_default")
    private Boolean isDefault;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }
}
