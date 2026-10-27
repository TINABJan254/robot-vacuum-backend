package com.ht.robotvacuum.exception;

public class RobotPairingException extends RuntimeException {

    public RobotPairingException() {
        super("Robot ID or pairing code is incorrect");
    }
}
