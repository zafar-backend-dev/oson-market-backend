package uz.osonmarket.release;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "android_release_settings")
public class AndroidReleaseSettings {
    @Id
    private Integer id;
    private int versionCode;
    private String versionName;

    protected AndroidReleaseSettings() {}

    AndroidReleaseSettings(int versionCode, String versionName) {
        this.id = 1;
        this.versionCode = versionCode;
        this.versionName = versionName;
    }

    int versionCode() { return versionCode; }
    String versionName() { return versionName; }
}
