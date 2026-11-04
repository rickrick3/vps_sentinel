package com.expertiz.sentinel.core.infrastructure.internal;

import com.expertiz.sentinel.core.infrastructure.AdminStatus;
import com.expertiz.sentinel.core.shared.text.Hostname;
import com.expertiz.sentinel.core.shared.text.IpAddress;
import com.expertiz.sentinel.core.shared.text.NoControlChars;
import com.expertiz.sentinel.core.shared.text.TextRules;
import com.expertiz.sentinel.core.shared.web.ValidationGroups;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Corps de {@code POST} et {@code PUT /api/v1/servers} (spec §9.3, §10.3). Les chaînes sont nettoyées avant la
 * validation (espaces aux extrémités ; vide équivaut à absent). En {@code PUT}, un champ facultatif absent est vidé.
 */
public record ServerRequest(
        @NotBlank
        @Size(max = 100, message = "100 caractères maximum")
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 ._-]*$",
                message = "lettres, chiffres, espaces, « . », « _ » ou « - », en commençant par une lettre ou un chiffre")
        String name,
        @Hostname String hostname,
        @IpAddress String publicIp,
        @IpAddress String privateIp,
        @Size(max = 100, message = "100 caractères maximum") @NoControlChars String provider,
        @Size(max = 100, message = "100 caractères maximum") @NoControlChars String region,
        @Pattern(regexp = OsFamily.PATTERN, message = "famille de système inconnue") String osFamily,
        @Size(max = 32, message = "32 caractères maximum")
        @Pattern(regexp = "^[A-Za-z0-9._ -]+$", message = "lettres, chiffres, espaces, « . », « _ » ou « - »")
        String osVersion,
        @Min(1) @Max(1024) Integer cpuCores,
        @Min(1) @Max(16_777_216) Integer ramMb,
        @Min(1) @Max(1_048_576) Integer diskGb,
        @NotNull(groups = ValidationGroups.Update.class)
        @Pattern(regexp = AdminStatus.PATTERN, message = "statut inconnu")
        String adminStatus,
        @Size(max = 2000, message = "2000 caractères maximum") @NoControlChars(allowNewlines = true) String description,
        List<String> tags,
        @Null(groups = ValidationGroups.Create.class, message = "ne doit pas être fourni à la création")
        @NotNull(groups = ValidationGroups.Update.class)
        @PositiveOrZero
        Long version) {

    public ServerRequest {
        name = TextRules.clean(name);
        hostname = TextRules.clean(hostname);
        publicIp = TextRules.clean(publicIp);
        privateIp = TextRules.clean(privateIp);
        provider = TextRules.clean(provider);
        region = TextRules.clean(region);
        osFamily = TextRules.clean(osFamily);
        osVersion = TextRules.clean(osVersion);
        adminStatus = TextRules.clean(adminStatus);
        description = TextRules.clean(description);
    }
}
