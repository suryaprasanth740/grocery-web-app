package com.suryaprasanth.grocery.model;

/** Why a buyer reported a shop or product. */
public enum ReportReason {
    WRONG_PRICE("Price on the app doesn't match the shop"),
    EXPIRED_OR_BAD_QUALITY("Expired or poor-quality item"),
    MISLEADING_INFO("Wrong photo or description"),
    FAKE_SHOP("Shop doesn't seem real"),
    RUDE_OR_UNSAFE("Rude or unsafe behaviour"),
    OTHER("Something else");

    private final String label;

    ReportReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
