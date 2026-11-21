package dev.motionengine.core.locomotion;

/** States of the locomotion graph. Add new ones here and wire transitions in the controller. */
public enum LocomotionState {
    /** Vanilla animation owns the body (swimming, elytra, riding, sleeping, ladders...). */
    VANILLA,
    IDLE,
    START,
    MOVE,
    STOP,
    PIVOT,
    AIRBORNE,
    LAND
}
