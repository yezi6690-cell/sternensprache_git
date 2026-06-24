package com.mindisle.app.live2d

data class Live2DModel(
    val id: String,
    val name: String,
    val assetModelPath: String,
    val previewImagePath: String,
    val description: String,
    val runtimeVersion: String = "cubism4"
)
