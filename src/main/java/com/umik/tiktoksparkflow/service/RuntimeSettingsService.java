package com.umik.tiktoksparkflow.service;

import com.umik.tiktoksparkflow.dto.RuntimeSettingsDTO;
import com.umik.tiktoksparkflow.vo.RuntimeSettingsVO;

public interface RuntimeSettingsService {
    RuntimeSettingsVO get();

    RuntimeSettingsVO update(RuntimeSettingsDTO settings);
}
