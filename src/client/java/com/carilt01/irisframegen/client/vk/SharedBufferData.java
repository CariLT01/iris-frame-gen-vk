package com.carilt01.irisframegen.client.vk;

public class SharedBufferData {

    private ImageView imageView;
    private ImageView localImageView;
    private final long sampler;

    private boolean initialized = false;

    public SharedBufferData(ImageView imageView, ImageView localImageView, long sampler) {
        this.imageView = imageView;
        this.sampler = sampler;
        this.localImageView = localImageView;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean value) {
        this.initialized = value;
    }

    public ImageView imageView() {
        return imageView;
    }

    public long sampler() {
        return sampler;
    }

    public void setImageView(ImageView imageView) {
        this.imageView = imageView;
    }

    public void setLocalImageView(ImageView imageView) {
        this.localImageView = imageView;
    }

    public ImageView getLocalImageView() {
        return localImageView;
    }
}
