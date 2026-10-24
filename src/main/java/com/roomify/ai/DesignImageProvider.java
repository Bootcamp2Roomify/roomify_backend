package com.roomify.ai;

public interface DesignImageProvider {

    GeneratedDesignImage generate(
            byte[] originalImage,
            String originalFilename,
            String contentType,
            String designInstructions
    );

    String getModelName();
}