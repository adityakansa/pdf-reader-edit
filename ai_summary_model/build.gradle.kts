// FR-068: the summary model (Minueza-2-96M-Instruct-Variant-04, Q2_K GGUF, Apache-2.0) delivered with the
// install. The .gguf is git-ignored; fetch it with scripts/fetch-summary-model.sh before building a bundle.
plugins {
    alias(libs.plugins.android.asset.pack)
}

assetPack {
    packName.set("ai_summary_model")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
