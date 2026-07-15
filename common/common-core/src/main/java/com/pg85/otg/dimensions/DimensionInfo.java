package com.pg85.otg.dimensions;

import com.pg85.otg.util.DimensionNameUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DimensionInfo {
    private String name;
    private String preset;
    private long seed;
    private long created;

    public static DimensionInfo create(String presetName, long seed) {
        String normalizedName = DimensionNameUtils.normalizeName(presetName);
        return DimensionInfo.builder()
                .name(normalizedName)
                .preset(presetName)
                .seed(seed)
                .created(System.currentTimeMillis())
                .build();
    }
}
