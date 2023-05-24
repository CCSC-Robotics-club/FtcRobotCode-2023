package org.firstinspires.ftc.teamcode.Drivers;

import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.teamcode.RobotModules.RobotPositionCalculator;

public class ChassisDriver {
    /* private final double maxPower = 0.6;
    private final double encoderDistanceStartDecelerate = 15000;
    private final double motorPowerPerEncoderValueError = (maxPower / encoderDistanceStartDecelerate); */
    private static final double maxRotatingPowerStationary = 0.5;
    private static final double rotationDifferenceStartDecelerateStationary = Math.toRadians(15);
    private static final double motorPowerPerRotationDifferenceStationary = -(maxRotatingPowerStationary / rotationDifferenceStartDecelerateStationary);
    private static final double velocityDebugTimeRotationStationary = 0.03;

    private static final double maxRotatingPowerInMotion = 0.35;
    private static final double rotationDifferenceStartDecelerateInMotion = Math.toRadians(45);
    private static final double motorPowerPerRotationDifferenceInMotion = -(maxRotatingPowerInMotion / rotationDifferenceStartDecelerateInMotion);
    private static final double velocityDebugTimeRotationInMotion = 0.1;

    private final double integralCoefficientRotationStationary = 0 * motorPowerPerRotationDifferenceStationary;
    private final double rotationalTolerance = Math.toRadians(3.5);
    private final double minRotatingAngularVelocity = Math.toRadians(10); // 5 degrees a second

    private double maxMotioningPower = 0.5;
    private double encoderDifferenceStartDecelerate = 2000;
    private double motorPowerPerEncoderDifference = (maxMotioningPower / encoderDifferenceStartDecelerate);
    private double velocityDebugTimeTranslation = 0.12;
    private double integrationCoefficientTranslation = 0 * motorPowerPerEncoderDifference; // not needed yet, (originally 0.05 * motor_power_per...)
    private double translationalEncoderTolerance = 250;
    /** the minimum encoder speed, in encoder value per second, of the robot. so the robot can judge whether it is stuck */
    private final double minMotioningEncoderSpeed = 100; // todo: measure this value

    private HardwareDriver hardwareDriver;
    private RobotPositionCalculator positionCalculator;

    private double xAxleMotion = 0;
    private double yAxleMotion = 0;
    private double rotationalMotion = 0;

    private double xAxleTranslationTarget = 0;

    private double yAxleTranslationTarget = 0;
    private double[] translationalIntegration = new double[2];
    private double targetedRotation = 0;
    private boolean RASActivation = false;
    private boolean aimProcessInterrupted = false;

    private final int goToRotationMode = 1;
    private final int manualMode = 0;
    private int rotationMode = manualMode;
    private final int gotoPositionMode = 2;
    private int translationalMode = manualMode;

    private double rotationalIntegration;

    private ElapsedTime dt = new ElapsedTime();

    public ChassisDriver(HardwareDriver hardwareDriver, RobotPositionCalculator positionCalculator) {
        this.hardwareDriver = hardwareDriver;
        this.positionCalculator = positionCalculator;
    }

    public void setRobotTranslationalMotion(double xAxleMotion, double yAxleMotion) {
        this.xAxleMotion = xAxleMotion;
        this.yAxleMotion = yAxleMotion;
        translationalMode = manualMode;
        sendCommandsToMotors();
    }

    public void setTargetedTranslation_fixedRotation(double xAxleTranslation, double yAxleTranslation) {
        this.xAxleTranslationTarget = xAxleTranslation;
        this.yAxleTranslationTarget = yAxleTranslation;
        switchToGoToPosition_fixedRotation_mode();
        translationalIntegration[0] = 0;
        translationalIntegration[1] = 0;
    }

    /** in radian */
    public void setTargetedRotation(double targetedRotation) {
        this.targetedRotation = targetedRotation;
        switchToGoToRotationMode();
        rotationalIntegration = 0;
    }

    public void setRotationalMotion(double rotationalMotion) {
        switchToManualRotationMode();
        this.rotationalMotion = rotationalMotion;
        dt.reset();
        sendCommandsToMotors();
    }

    public void pilotInterruption() {
        RASActivation = false;
        aimProcessInterrupted = true;
    }

    public boolean isAimProcessInterrupted() { return aimProcessInterrupted; }

    public void newAimStarted() {
        RASActivation = true;
        aimProcessInterrupted = false;
    }

    public void aimStopped() { RASActivation = false; }

    public void switchToManualRotationMode() { rotationMode = manualMode;}
    private void switchToGoToRotationMode() { rotationMode = goToRotationMode; }

    public void switchToManualPositionMode() { translationalMode = manualMode; }
    private void switchToGoToPosition_fixedRotation_mode() {
        translationalMode = gotoPositionMode;
        this.targetedRotation = positionCalculator.getRobotRotation();
        switchToGoToRotationMode();
    }

    public boolean isRASActivated() { return RASActivation; }

    public void sendCommandsToMotors() {
        if (rotationMode == goToRotationMode) updateRotationalMotorSpeed(dt.seconds());
        if (translationalMode == gotoPositionMode) updateTranslationalMotionUsingEncoder_fixedRotation(dt.seconds());
        hardwareDriver.leftFront.setPower(yAxleMotion + rotationalMotion + xAxleMotion);
        hardwareDriver.leftRear.setPower(yAxleMotion + rotationalMotion - xAxleMotion);
        hardwareDriver.rightFront.setPower(yAxleMotion - rotationalMotion - xAxleMotion);
        hardwareDriver.rightRear.setPower(yAxleMotion - rotationalMotion + xAxleMotion);
        // TODO make the robot stick to the rotation it was when pilot not sending commands on rotation
    }

    private void updateRotationalMotorSpeed(double dt) {
        double velocityDebugTime, motorPowerPerRotationDifference, maxPower, integralCoefficient;
        if (RASActivation) {
            velocityDebugTime = velocityDebugTimeRotationStationary;
            motorPowerPerRotationDifference = motorPowerPerRotationDifferenceStationary;
            maxPower = maxRotatingPowerStationary;
            integralCoefficient = integralCoefficientRotationStationary;
        } else {
            velocityDebugTime = velocityDebugTimeRotationInMotion;
            motorPowerPerRotationDifference = motorPowerPerRotationDifferenceInMotion;
            maxPower = maxRotatingPowerInMotion;
            integralCoefficient = 0;
        }

        double currentRotation = this.positionCalculator.getRobotRotation();
        /* according to the angular velocity, predict the future rotation of the robot after velocity debug time */
        double futureRotation = currentRotation + velocityDebugTime * this.positionCalculator.getAngularVelocity();

        double rotationalRawError = getActualDifference(currentRotation, targetedRotation);
        double rotationalError = getActualDifference(futureRotation, targetedRotation);

        if (Math.abs(rotationalError) < rotationDifferenceStartDecelerateStationary) rotationalIntegration += rotationalRawError * dt;

        rotationalMotion = rotationalError * motorPowerPerRotationDifference + rotationalIntegration * integralCoefficient;
        rotationalMotion = Math.copySign(Math.min(maxPower, Math.abs(rotationalMotion)), rotationalMotion);

        System.out.println("rotation:" + Math.toDegrees(this.positionCalculator.getRobotRotation()) + ";raw error:" + Math.toDegrees(rotationalRawError) + "; error:" + Math.toDegrees(rotationalError) + "; power" + rotationalMotion);
    }

    private void updateTranslationalMotionUsingEncoder_fixedRotation(double dt) {
        double[] currentPosition = positionCalculator.getRobotPosition();
        double currentRotation = positionCalculator.getRobotRotation();

        /* according to the robot's translational motion, predict it's future position */
        double[] positionPrediction = new double[2];
        positionPrediction[0] = positionCalculator.getVelocity()[0] * velocityDebugTimeTranslation + currentPosition[0];
        positionPrediction[1] = positionCalculator.getVelocity()[1] * velocityDebugTimeTranslation + currentPosition[1];

        double[] positionRawErrorToGround = new double[]{xAxleTranslationTarget - currentPosition[0], yAxleTranslationTarget - currentPosition[1]};
        double[] positionErrorToGround = new double[] {xAxleTranslationTarget - positionPrediction[0], yAxleTranslationTarget- positionPrediction[1]};

        double[] positionRawError, positionError; positionRawError = new double[2]; positionError = new double[2];
        positionRawError[0] = positionRawErrorToGround[0] * Math.cos(currentRotation) + positionRawErrorToGround[1] * Math.sin(currentRotation);
        positionRawError[1] = positionRawErrorToGround[0] * -Math.sin(currentRotation) + positionRawErrorToGround[1] * Math.cos(currentRotation);
        positionError[0] = positionErrorToGround[0] * Math.cos(currentRotation) + positionErrorToGround[1] * Math.sin(currentRotation);
        positionError[1] = positionErrorToGround[0] * -Math.sin(currentRotation) + positionErrorToGround[1] * Math.cos(currentRotation);

        // do the integration when the robot is almost there
        if (Math.abs(positionError[0]) + Math.abs(positionError[1]) < encoderDifferenceStartDecelerate / 4) {
            translationalIntegration[0] += dt * positionRawError[0];
            translationalIntegration[1] += dt * positionRawError[1];
        }

        xAxleMotion = positionError[0] * motorPowerPerEncoderDifference + translationalIntegration[0] * integrationCoefficientTranslation;
        yAxleMotion = positionError[1] * motorPowerPerEncoderDifference + translationalIntegration[1] * integrationCoefficientTranslation;

        xAxleMotion = Math.copySign(
                Math.min(Math.abs(xAxleMotion), maxMotioningPower),
                xAxleMotion
        );
        yAxleMotion = Math.copySign(
                Math.min(Math.abs(yAxleMotion), maxMotioningPower),
                yAxleMotion
        );

        updateRotationalMotorSpeed(dt);

        System.out.println("motor power:" + xAxleMotion + "," + yAxleMotion +"; error:" + positionError[0] + "," + positionError[1] + ";error to ground:" + positionErrorToGround[0] + "," + positionErrorToGround[1]);
    }

    @Deprecated
    private void updateTranslationalMotionUsingEncoder(double dt) {
        double[] currentPosition = positionCalculator.getRobotPosition();
        double currentRotation = positionCalculator.getRobotRotation();

        /* according to the robot's translational motion, predict it's future position */
        double[] positionPrediction = new double[2];
        positionPrediction[0] = positionCalculator.getVelocity()[0] * velocityDebugTimeTranslation + currentPosition[0];
        positionPrediction[1] = positionCalculator.getVelocity()[1] * velocityDebugTimeTranslation + currentPosition[1];

        double[] positionRawError = new double[]{xAxleTranslationTarget - currentPosition[0], yAxleTranslationTarget - currentPosition[1]};
        double[] positionError = new double[] {xAxleTranslationTarget - positionPrediction[0], yAxleTranslationTarget- positionPrediction[1]};

        // do the integration when the robot is almost there
        if (Math.abs(positionError[0]) + Math.abs(positionError[1]) < encoderDifferenceStartDecelerate) {
            translationalIntegration[0] += dt * positionRawError[0];
            translationalIntegration[1] += dt * positionRawError[1];
        }

        double xAxleMotionToGround;
        double yAxleMotionToGround;
        xAxleMotionToGround = positionError[0] * motorPowerPerEncoderDifference + translationalIntegration[0] * integrationCoefficientTranslation;
        yAxleMotionToGround = positionError[1] * motorPowerPerEncoderDifference + translationalIntegration[1] * integrationCoefficientTranslation;

        xAxleMotionToGround = Math.copySign(
                Math.min(Math.abs(xAxleMotionToGround), maxMotioningPower),
                xAxleMotionToGround
        );
        yAxleMotionToGround = Math.copySign(
                Math.min(Math.abs(yAxleMotionToGround), maxMotioningPower),
                yAxleMotionToGround
        );

        xAxleMotion = (xAxleMotionToGround * Math.cos(currentRotation)) + (yAxleMotionToGround * Math.sin(currentRotation));
        yAxleMotion = (xAxleMotionToGround * Math.sin(currentRotation)) + (yAxleMotionToGround * Math.cos(currentRotation));

        // System.out.println("raw error:" + positionRawError[1] + "; error:" + positionError[1] + "; motion(to ground)" + yAxleMotionToGround + "; motion:" + yAxleMotion);
    }

    /**
     * go to a targeted sector and stop, for auto stage
     * @param x: the x-axle target
     * @param y: the y-axle target
     * @return whether the process succeeded or did it got stuck
     * */
    public boolean goToPosition(double x, double y) {
        setTargetedTranslation_fixedRotation(x, y);
        double xError, yError;
        ElapsedTime dt = new ElapsedTime();
        /* the time that the robot has been stuck */
        ElapsedTime stuckTime = new ElapsedTime();
        do {
            positionCalculator.forceUpdateEncoderValue();
            positionCalculator.periodic();

            xError = x - positionCalculator.getRobotPosition()[0];
            yError = y - positionCalculator.getRobotPosition()[1];
            sendCommandsToMotors();
            dt.reset();

            /* to judge if the robot is stuck */
            if (
                    positionCalculator.getRawVelocity()[1] * positionCalculator.getRawVelocity()[1]
                            + positionCalculator.getRawVelocity()[0] * positionCalculator.getRawVelocity()[0]
                            > minMotioningEncoderSpeed * minMotioningEncoderSpeed) {
                stuckTime.reset();
            } else if (stuckTime.seconds() > 0.5) {
                return false;
            }
        } while(xError * xError + yError * yError > translationalEncoderTolerance * translationalEncoderTolerance);
        switchToManualPositionMode();
        setRobotTranslationalMotion(0, 0);
        return true;
    }

    /**
     * go to a rotation and stop, for auto stage
     * @param radian: the targeted facing, in radian
     * @return whether the process succeeded
     * */
    public boolean goToRotation(double radian) {
        setTargetedRotation(radian);
        double rotationError;
        ElapsedTime dt = new ElapsedTime();
        ElapsedTime stuckTime = new ElapsedTime();
        do {
            positionCalculator.forceUpdateEncoderValue();
            positionCalculator.periodic();

            rotationError = getActualDifference(positionCalculator.getRobotRotation(), radian);
            sendCommandsToMotors();
            dt.reset();

            if (Math.abs(positionCalculator.getAngularVelocity()) > minRotatingAngularVelocity) stuckTime.reset();
            else if (stuckTime.seconds() > 0.5) return false;
        } while (Math.abs(rotationError) > rotationalTolerance);
        switchToManualRotationMode();
        setRotationalMotion(0);
        return true;
    }

    /**
     * go to a rotation and stop, for auto stage
     * @param degrees: the targeted facing, in degrees
     * @return whether the process succeeded
     * */
    public boolean goToRotation(int degrees) {
        return goToRotation(Math.toRadians(degrees));
    }

    public void setAutoMode(boolean autoMode) {
        if (autoMode) {
            encoderDifferenceStartDecelerate = 2400;
            velocityDebugTimeTranslation = 0.2;
        } else {
            encoderDifferenceStartDecelerate = 2000;
            velocityDebugTimeTranslation = 0.12;
        }
        motorPowerPerEncoderDifference = (maxMotioningPower / encoderDifferenceStartDecelerate);
        integrationCoefficientTranslation = 0.00 * motorPowerPerEncoderDifference;
    }

    public static double getActualDifference(double currentRotation, double targetedRotation) {
        while (targetedRotation > Math.PI*2) targetedRotation -= Math.PI*2;
        while (targetedRotation < 0) targetedRotation += Math.PI*2;

        double rawDifference = targetedRotation - currentRotation;
        double absoluteDifference = Math.min(
                Math.abs(rawDifference),
                2*Math.PI - Math.abs(rawDifference));

        if ((rawDifference > Math.PI) || (-Math.PI < rawDifference && rawDifference < 0))  {
            absoluteDifference *= -1;
        }
        return absoluteDifference;
    }

    public static double midPoint(double rotation1, double rotation2) {
        rotation1 += getActualDifference(rotation1, rotation2) / 2;
        return rotation1 % (Math.PI*2);
    }
}
