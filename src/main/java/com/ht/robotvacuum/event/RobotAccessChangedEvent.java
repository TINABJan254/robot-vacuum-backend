package com.ht.robotvacuum.event;

/**
 * Published when a robot's owner may have changed (pair, unpair, owner update, delete).
 * Live subscriptions for the robot must be re-authorized.
 */
public record RobotAccessChangedEvent(long robotId) {
}
