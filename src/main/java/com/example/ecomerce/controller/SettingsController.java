package com.example.ecomerce.controller;

import com.example.ecomerce.models.StoreSetting;
import com.example.ecomerce.repository.StoreSettingRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/settings")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class SettingsController {

    private static final long SETTINGS_ID = 1L;
    private final StoreSettingRepository settingRepository;

    public SettingsController(StoreSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @GetMapping
    public SettingsResponse getSettings() {
        StoreSetting setting = settingRepository.findById(SETTINGS_ID).orElseGet(this::defaultSetting);
        return toResponse(setting);
    }

    @PutMapping
    public ResponseEntity<?> updateSettings(@RequestBody SettingsRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Payload là bắt buộc"));
        }

        StoreSetting setting = settingRepository.findById(SETTINGS_ID).orElseGet(this::defaultSetting);
        setting.setStoreName(normalize(request.storeName()));
        setting.setPhone(normalize(request.phone()));
        setting.setAddress(normalize(request.address()));
        setting.setMouserApiKey(normalize(request.mouserApiKey()));
        StoreSetting saved = settingRepository.save(setting);
        return ResponseEntity.ok(toResponse(saved));
    }

    private StoreSetting defaultSetting() {
        StoreSetting setting = new StoreSetting();
        setting.setId(SETTINGS_ID);
        setting.setStoreName("");
        setting.setPhone("");
        setting.setAddress("");
        setting.setMouserApiKey("");
        return setting;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }

    private SettingsResponse toResponse(StoreSetting setting) {
        return new SettingsResponse(
                setting.getId(),
                setting.getStoreName(),
                setting.getPhone(),
                setting.getAddress(),
                setting.getMouserApiKey()
        );
    }

    public record SettingsRequest(
            String storeName,
            String phone,
            String address,
            String mouserApiKey
    ) {
    }

    public record SettingsResponse(
            Long id,
            String storeName,
            String phone,
            String address,
            String mouserApiKey
    ) {
    }
}

