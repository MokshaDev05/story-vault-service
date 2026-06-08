package com.moksha.storyvault.model.enums;

public enum KudosStatus {
    /** Default — no detection attempted yet. */
    UNKNOWN,
    /** AO3 confirmed kudos was given ("You have already left kudos here!"). */
    GIVEN,
    /** Kudos section found but given-state not confirmed (not given, or not logged in to AO3). */
    NOT_DETECTED
}
