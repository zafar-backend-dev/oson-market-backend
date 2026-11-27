package uz.osonmarket.release;

import jakarta.persistence.Column;
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
    @Column(length = 2048)
    private String downloadUrl;

    protected AndroidReleaseSettings() {}

    AndroidReleaseSettings(int versionCode, String versionName, String downloadUrl) {
        this.id = 1;
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.downloadUrl = downloadUrl;
    }

    int versionCode() { return versionCode; }
    String versionName() { return versionName; }
    String downloadUrl() { return downloadUrl; }
}
