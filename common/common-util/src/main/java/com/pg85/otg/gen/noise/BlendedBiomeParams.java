package com.pg85.otg.gen.noise;

public record BlendedBiomeParams(
        float height,
        float biomeVolatility,
        double volatility1,
        double volatility2,
        double horizontalFracture,
        double verticalFracture,
        double volatilityWeight1,
        double volatilityWeight2,
        double valleyFactor,
        double peakFactor
) {}
