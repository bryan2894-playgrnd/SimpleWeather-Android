package com.thewizrd.shared_resources.weatherdata.tomorrow;

import com.google.gson.annotations.SerializedName;

public class ResponseItem {

    @SerializedName("instruction")
    private String instruction;

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public String getInstruction() {
        return instruction;
    }
}